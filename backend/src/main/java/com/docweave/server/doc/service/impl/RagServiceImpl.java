package com.docweave.server.doc.service.impl;

import com.docweave.server.common.exception.ErrorCode;
import com.docweave.server.doc.dto.ChatMessageDto;
import com.docweave.server.doc.dto.ChatRoomDto;
import com.docweave.server.doc.dto.request.ChatRequestDto;
import com.docweave.server.doc.dto.response.ChatResponseDto;
import com.docweave.server.doc.entity.ChatDocument;
import com.docweave.server.doc.entity.ChatMessage;
import com.docweave.server.doc.entity.ChatRoom;
import com.docweave.server.doc.entity.DocContent;
import com.docweave.server.doc.exception.AiProcessingException;
import com.docweave.server.doc.exception.ChatRoomFindingException;
import com.docweave.server.doc.exception.FileHandlingException;
import com.docweave.server.doc.exception.GuardrailException;
import com.docweave.server.doc.repository.ChatDocumentRepository;
import com.docweave.server.doc.repository.ChatMessageRepository;
import com.docweave.server.doc.repository.ChatRoomRepository;
import com.docweave.server.doc.repository.DocContentRepository;
import com.docweave.server.doc.service.RagService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatDocumentRepository chatDocumentRepository;
    private final DocContentRepository docContentRepository;

    private static final int PARENT_CHUNK_SIZE = 1000;
    private static final int CHILD_CHUNK_SIZE = 300;

    private static final double SIMILARITY_THRESHOLD = 0.4;

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomDto> getChatRooms() {
        return chatRoomRepository.findAllByOrderByLastActiveAtDesc().stream()
                .map(room -> ChatRoomDto.builder()
                        .id(room.getId())
                        .title(room.getTitle())
                        .createdAt(room.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getChatMessages(Long roomId) {
        return chatMessageRepository.findAllByChatRoomIdOrderByCreatedAtAsc(roomId).stream()
                .map(msg -> ChatMessageDto.builder()
                        .role(msg.getRole().name().toLowerCase())
                        .content(msg.getContent())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChatRoomDto createChatRoom(MultipartFile file) {
        if (file.isEmpty()) throw new FileHandlingException(ErrorCode.FILE_EMPTY);
        if (!Objects.requireNonNull(file.getOriginalFilename()).toLowerCase().endsWith(".pdf"))
            throw new FileHandlingException(ErrorCode.INVALID_FILE_EXTENSION);

        try {
            // DB에 채팅방 생성
            ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.builder()
                    .title(file.getOriginalFilename())
                            .lastActiveAt(LocalDateTime.now())
                    .build());

            // Parent-Child 처리 로직 호출
            processDocument(chatRoom, file);

            // 첫 안내 메시지 저장
            chatMessageRepository.save(ChatMessage.builder()
                    .chatRoom(chatRoom)
                    .role(ChatMessage.MessageRole.AI)
                    .content("📂 **" + file.getOriginalFilename() + "** 분석이 완료되었습니다.\n질문해주세요!")
                    .build());

            return ChatRoomDto.builder()
                    .id(chatRoom.getId())
                    .title(chatRoom.getTitle())
                    .createdAt(chatRoom.getCreatedAt())
                    .build();

        } catch (Exception e) {
            log.error("Room Creation Error", e);
            throw new FileHandlingException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    @Transactional
    public void addDocumentToRoom(Long roomId, MultipartFile file) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ChatRoomFindingException(ErrorCode.CHATROOM_NOT_FOUND));

        chatRoom.updateLastActiveAt();

        try {
            // Parent-Child 처리 로직 호출
            processDocument(chatRoom, file);

            // 시스템 메시지 추가 (사용자에게 알림)
            chatMessageRepository.save(ChatMessage.builder()
                    .chatRoom(chatRoom)
                    .role(ChatMessage.MessageRole.AI)
                    .content("📎 **" + file.getOriginalFilename() + "** 문서가 추가되었습니다.")
                    .build());

        } catch (Exception e) {
            log.error("Add Document Error", e);
            throw new FileHandlingException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    @Transactional
    public ChatResponseDto ask(Long roomId, ChatRequestDto requestDto) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ChatRoomFindingException(ErrorCode.CHATROOM_NOT_FOUND));

        chatRoom.updateLastActiveAt();

        // 사용자 질문 DB 저장
        chatMessageRepository.save(ChatMessage.builder()
                .chatRoom(chatRoom)
                .role(ChatMessage.MessageRole.USER)
                .content(requestDto.getMessage())
                .build());

        try {
            // Vector Search: 질문과 유사한 'Child' 청크 검색
            List<Document> similarChildren = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(requestDto.getMessage())
                            .topK(3)
                            .filterExpression("roomId == " + roomId)
                            .build()
            );

            // Parent ID 추출
            Set<Long> parentIds = similarChildren.stream()
                    .map(doc -> {
                        Object pid = doc.getMetadata().get("parent_id");
                        return pid != null ? Long.valueOf(pid.toString()) : null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // RDB에서 Parent 조회
            String context = "";
            if (!parentIds.isEmpty()) {
                List<DocContent> parentContents = docContentRepository.findAllByIdIn(new ArrayList<>(parentIds));
                context = parentContents.stream()
                        .map(DocContent::getContent)
                        .collect(Collectors.joining("\n\n"));
            }

            // 프롬포트 생성
            PromptTemplate template = getPromptTemplate();
            Prompt prompt = template.create(Map.of("context", context, "message", requestDto.getMessage()));

            log.info("Generating answer for room: {}", roomId);
            String rawAnswer = chatClient.prompt(prompt).call().content();

            if (rawAnswer == null || rawAnswer.isBlank()) {
                throw new AiProcessingException(ErrorCode.AI_SERVICE_ERROR);
            }

            // 가드레일 검증 적용
            log.info("Validating answer quality for room: {}", roomId);
            boolean isValid = validateResponse(context, rawAnswer);

            if (!isValid) {
                log.warn("Guardrail validation failed. RoomId: {}, Input: {}", roomId, requestDto.getMessage());
                throw new GuardrailException(ErrorCode.GUARDRAIL_BLOCKED);
            }

            // 검증 통과 시 DB 저장
            chatMessageRepository.save(ChatMessage.builder()
                    .chatRoom(chatRoom)
                    .role(ChatMessage.MessageRole.AI)
                    .content(rawAnswer)
                    .build());

            return ChatResponseDto.builder()
                    .question(requestDto.getMessage())
                    .answer(rawAnswer)
                    .build();

        } catch (GuardrailException e) {
            throw e;

        } catch (Exception e) {
            log.error("AI Error", e);
            throw new AiProcessingException(ErrorCode.AI_SERVICE_ERROR);
        }
    }

    @Override
    public void deleteChatRoom(Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ChatRoomFindingException(ErrorCode.CHATROOM_NOT_FOUND));

        chatRoomRepository.delete(chatRoom);
    }

    private void processDocument(ChatRoom chatRoom, MultipartFile file) {
        // 파일 메타데이터 RDB 저장
        ChatDocument chatDocument = chatDocumentRepository.save(ChatDocument.builder()
                .chatRoom(chatRoom)
                .fileName(file.getOriginalFilename())
                .build());

        // PDF 파싱
        Resource resource = file.getResource();
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
        List<Document> rawDocuments = pdfReader.get();

        if (rawDocuments.isEmpty()) throw new FileHandlingException(ErrorCode.DOCUMENT_PARSING_ERROR);

        // Parent Chunking (1000 토큰)
        TokenTextSplitter parentSplitter = new TokenTextSplitter(PARENT_CHUNK_SIZE, 100, 10, 1000, true);
        List<Document> parentDocs = parentSplitter.apply(rawDocuments);

        List<Document> childDocsToEmbed = new ArrayList<>();

        // Parent 저장 및 Child 생성 루프
        for (Document pDoc : parentDocs) {
            // Parent를 RDB에 저장
            Object pageNumObj = pDoc.getMetadata().getOrDefault("page_number", 0);
            int pageNum = (pageNumObj instanceof Number) ? ((Number) pageNumObj).intValue() : 0;

            DocContent savedParent = docContentRepository.save(DocContent.builder()
                    .chatDocument(chatDocument)
                    .content(pDoc.getText())
                    .pageNumber(pageNum)
                    .build());

            // Child Chunking (300 토큰)
            TokenTextSplitter childSplitter = new TokenTextSplitter(CHILD_CHUNK_SIZE, 50, 10, 100, true);
            List<Document> childDocs = childSplitter.apply(Collections.singletonList(pDoc));

            // Child에 Parent ID 태깅
            for (Document cDoc : childDocs) {
                cDoc.getMetadata().put("parent_id", savedParent.getId());
                cDoc.getMetadata().put("roomId", chatRoom.getId());
                cDoc.getMetadata().put("source_file", file.getOriginalFilename());
                cDoc.getMetadata().put("page_number", pageNum);
            }
            childDocsToEmbed.addAll(childDocs);
        }

        // Child만 벡터 DB에 저장
        vectorStore.add(childDocsToEmbed);
    }

    private boolean validateResponse(String context, String answer) {
        // 규칙 기반 필터링 (빠른 차단)
        if (answer.contains("제공된 문서에서 해당 내용을 찾을 수 없습니다")) return true;
        if (answer.length() < 5) return false;

        try {
            // 임베딩 생성
            float[] contextVector = embeddingModel.embed(context);
            float[] answerVector = embeddingModel.embed(answer);

            // 코사인 유사도 계산
            double similarity = cosineSimilarity(contextVector, answerVector);
            log.debug("Validation Similarity Score: {}", similarity);

            // 임계값 비교
            return similarity >= SIMILARITY_THRESHOLD;

        } catch (Exception e) {
            log.error("Similarity Calculation Failed", e);
            return true;
        }
    }


    private double cosineSimilarity(float[] v1, float[] v2) {
        // 배열 유효성 검사
        if (v1 == null || v2 == null || v1.length != v2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += Math.pow(v1[i], 2);
            normB += Math.pow(v2[i], 2);
        }

        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static @NonNull PromptTemplate getPromptTemplate() {
        String promptText = """
            당신은 'DocWeave' 라는 지능형 문서 분석 AI 어시스턴트입니다.
            사용자의 질문에 대해 아래 제공된 [Context] 정보를 바탕으로 정확하고 전문적인 답변을 제공하세요.
            
            ## 지침 (Instructions)
            1. **근거 기반**: 오직 [Context]에 있는 내용만 사용하여 답변하세요. 외부 지식이나 상상을 섞지 마세요.
            2. **양심적 거절**: 만약 [Context]에 질문에 대한 답변이 포함되어 있지 않다면, 솔직하게 "제공된 문서에서 해당 내용을 찾을 수 없습니다."라고 답변하세요. 내용을 지어내지 마세요.
            3. **구조화된 답변**: 답변은 가독성이 좋게 **Markdown** 문법을 사용하세요.
               - 핵심 키워드는 **볼드체**로 강조하세요.
               - 나열되는 정보는 글머리 기호(-, 1.)를 사용하여 정리하세요.
               - 필요하다면 표(Table) 형식을 사용해도 좋습니다.
            4. **언어**: 한국어로 자연스럽고 정중하게(존댓말) 답변하세요. 답변에는 반드시 **한국어와 영어**만 사용하세요. 중국어, 일본어 등의 언어를 사용하지 마세요.
            
            [Context]
            {context}
            
            [Question]
            {message}
            """;

        return new PromptTemplate(promptText);
    }
}