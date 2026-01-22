package com.docweave.server.doc.service.impl;

import com.docweave.server.common.exception.ErrorCode;
import com.docweave.server.doc.dto.ChatMessageDto;
import com.docweave.server.doc.dto.ChatRoomDto;
import com.docweave.server.doc.dto.request.ChatRequestDto;
import com.docweave.server.doc.dto.request.DocumentIngestionRequestDto;
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
import com.docweave.server.doc.service.DocumentIngestionService;
import com.docweave.server.doc.service.RagService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;
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
    private final DocumentIngestionService documentIngestionService;

    // Feature Flag 주입
    @Value("${docweave.optimization.enabled}")
    private boolean useOptimization;

    @Value("classpath:prompts/system-rag-prompt.st")
    private Resource ragPromptResource;

    private static final int PARENT_CHUNK_SIZE = 800;
    private static final int CHILD_CHUNK_SIZE = 300;

    private static final double SIMILARITY_THRESHOLD = 0.4;
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

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
        validateFile(file);

        try {
            // DB에 채팅방 생성
            ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.builder()
                    .title(file.getOriginalFilename())
                    .lastActiveAt(LocalDateTime.now())
                    .build());

            // 파일 메타데이터 RDB 저장
            ChatDocument chatDocument = chatDocumentRepository.save(ChatDocument.builder()
                    .chatRoom(chatRoom)
                    .fileName(file.getOriginalFilename())
                    .status(ChatDocument.ProcessingStatus.PENDING)
                    .build());

            // 임시 파일 저장
            String tempFilePath = saveTempFile(file);

            DocumentIngestionRequestDto request = DocumentIngestionRequestDto.builder()
                    .roomId(chatRoom.getId())
                    .documentId(chatDocument.getId())
                    .tempFilePath(tempFilePath)
                    .originalFileName(file.getOriginalFilename())
                    .build();

            // 비동기 문서 처리 시작
            documentIngestionService.processDocument(request);

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
        validateFile(file);
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ChatRoomFindingException(ErrorCode.CHATROOM_NOT_FOUND));

        chatRoom.updateLastActiveAt();

        try {
            ChatDocument chatDocument = chatDocumentRepository.save(ChatDocument.builder()
                    .chatRoom(chatRoom)
                    .fileName(file.getOriginalFilename())
                    .status(ChatDocument.ProcessingStatus.PENDING)
                    .build());

            String tempFilePath = saveTempFile(file);

            DocumentIngestionRequestDto request = DocumentIngestionRequestDto.builder()
                    .roomId(roomId)
                    .documentId(chatDocument.getId())
                    .tempFilePath(tempFilePath)
                    .originalFileName(file.getOriginalFilename())
                    .build();

            documentIngestionService.processDocument(request);

            chatMessageRepository.save(ChatMessage.builder()
                    .chatRoom(chatRoom)
                    .role(ChatMessage.MessageRole.AI)
                    .content("📎 **" + file.getOriginalFilename() + "** 추가 분석을 시작합니다.")
                    .build());

        } catch (Exception e) {
            log.error("Add Document Error", e);
            throw new FileHandlingException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    @Transactional
    public ChatResponseDto ask(Long roomId, ChatRequestDto requestDto) {
        // 성능 측정을 위한 StopWatch 시작
        StopWatch stopWatch = new StopWatch("RAG Performance Check - Room " + roomId);

        stopWatch.start("1. Basic Setup & Retrieval");
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
            // 대화 내역 조회 (최대 6개)
            List<ChatMessage> chatHistoryList = chatMessageRepository.findTop6ByChatRoomIdOrderByCreatedAtDesc(roomId);
            Collections.reverse(chatHistoryList);

            // 대화 내역 포맷팅
            String conversationHistory = chatHistoryList.stream()
                    .map(msg -> String.format("%s: %s", msg.getRole(), msg.getContent()))
                    .collect(Collectors.joining("\n"));

            // Vector Search: 질문과 유사한 'Child' 청크 검색
            List<Document> similarChildren = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(requestDto.getMessage())
                            .topK(2)
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
            String contextStr = "";
            if (!parentIds.isEmpty()) {
                List<DocContent> parentContents = docContentRepository.findAllByIdIn(new ArrayList<>(parentIds));
                contextStr = parentContents.stream()
                        .map(DocContent::getContent)
                        .collect(Collectors.joining("\n\n"));
            }

            final String finalContext = contextStr;

            // 프롬포트 생성
            PromptTemplate template = new PromptTemplate(ragPromptResource);
            Prompt prompt = template.create(Map.of("history", conversationHistory, "context", finalContext, "message", requestDto.getMessage()));
            stopWatch.stop(); // 1. Setup 완료

            String rawAnswer = "";
            boolean isValid = false;

            // Feature Flag에 따른 로직 분기
            if (useOptimization) {
                log.info("🚀 [Mode: Optimized] Executing Parallel Processing...");
                stopWatch.start("2-A. Parallel Processing (LLM + Context Embed)");

                // AI 응답 생성 및 컨텍스트 임베딩 병렬 처리
                log.info("Generating answer for room: {}", roomId);
                CompletableFuture<String> answerFuture = CompletableFuture.supplyAsync(() ->
                        chatClient.prompt(prompt).call().content()
                );

                CompletableFuture<float[]> contextEmbeddingFuture = CompletableFuture.supplyAsync(() ->
                        embeddingModel.embed(finalContext)
                );

                CompletableFuture.allOf(answerFuture, contextEmbeddingFuture).join();
                stopWatch.stop();

                rawAnswer = answerFuture.get();
                float[] contextVector = contextEmbeddingFuture.get();

                if (rawAnswer == null || rawAnswer.isBlank()) {
                    throw new AiProcessingException(ErrorCode.AI_SERVICE_ERROR);
                }

                // 가드레일 검증 (Optimized: 이미 계산된 Vector 사용)
                log.info("Validating answer quality for room: {}", roomId);
                stopWatch.start("3-A. Validation (Optimized)");
                isValid = validateResponse(contextVector, rawAnswer);
                stopWatch.stop();

            } else {
                log.info("🐢 [Mode: Legacy] Executing Sequential Processing...");

                // 순차 처리: LLM 호출
                stopWatch.start("2-B. LLM Generation (Sequential)");
                rawAnswer = chatClient.prompt(prompt).call().content();
                stopWatch.stop();

                if (rawAnswer == null || rawAnswer.isBlank()) {
                    throw new AiProcessingException(ErrorCode.AI_SERVICE_ERROR);
                }

                // 가드레일 검증 (Legacy: 검증 시점에 Context 임베딩 수행)
                log.info("Validating answer quality for room: {}", roomId);
                stopWatch.start("3-B. Validation (Legacy)");
                isValid = validateResponseLegacy(finalContext, rawAnswer);
                stopWatch.stop();
            }

            // 로그 출력
            log.info(stopWatch.prettyPrint());

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

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) throw new FileHandlingException(ErrorCode.FILE_EMPTY);

        if (!Objects.requireNonNull(file.getOriginalFilename()).toLowerCase().endsWith(".pdf"))
            throw new FileHandlingException(ErrorCode.INVALID_FILE_EXTENSION);
    }

    private String saveTempFile(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        String tempFileName = UUID.randomUUID() + "_" + originalName;

        Path path = Path.of(TEMP_DIR, tempFileName);
        Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

        return path.toString();
    }

    // Optimized Validation (Context Vector를 인자로 받음)
    private boolean validateResponse(float[] contextVector, String answer) {
        // 규칙 기반 필터링
        if (answer.contains("제공된 문서에서 해당 내용을 찾을 수 없습니다")) return true;
        if (answer.length() < 5) return false;

        try {
            float[] answerVector = embeddingModel.embed(answer);

            double similarity = cosineSimilarity(contextVector, answerVector);
            log.debug("Validation Similarity Score: {}", similarity);

            return similarity >= SIMILARITY_THRESHOLD;
        } catch (Exception e) {
            log.error("Similarity Calculation Failed", e);
            return true;
        }
    }

    // Legacy Validation (Context String을 받아 내부에서 임베딩 - 순차 처리 시뮬레이션용)
    private boolean validateResponseLegacy(String contextStr, String answer) {
        // 규칙 기반 필터링
        if (answer.contains("제공된 문서에서 해당 내용을 찾을 수 없습니다")) return true;
        if (answer.length() < 5) return false;

        try {
            // Legacy: 여기서 Context Embedding을 수행
            float[] contextVector = embeddingModel.embed(contextStr);
            float[] answerVector = embeddingModel.embed(answer);

            double similarity = cosineSimilarity(contextVector, answerVector);
            log.debug("Validation Similarity Score: {}", similarity);

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
}