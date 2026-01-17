package com.docweave.server.doc.service;

import com.docweave.server.common.exception.ErrorCode;
import com.docweave.server.doc.dto.ChatMessageDto;
import com.docweave.server.doc.dto.ChatRoomDto;
import com.docweave.server.doc.dto.request.ChatRequestDto;
import com.docweave.server.doc.dto.response.ChatResponseDto;
import com.docweave.server.doc.entity.ChatDocument;
import com.docweave.server.doc.entity.ChatMessage;
import com.docweave.server.doc.entity.ChatRoom;
import com.docweave.server.doc.exception.AiProcessingException;
import com.docweave.server.doc.exception.ChatRoomFindingException;
import com.docweave.server.doc.exception.FileHandlingException;
import com.docweave.server.doc.repository.ChatDocumentRepository;
import com.docweave.server.doc.repository.ChatMessageRepository;
import com.docweave.server.doc.repository.ChatRoomRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
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
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatDocumentRepository chatDocumentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomDto> getChatRooms() {
        return chatRoomRepository.findAllByOrderByCreatedAtDesc().stream()
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
    public ChatRoomDto createChatRoom(MultipartFile file) {
        if (file.isEmpty()) throw new FileHandlingException(ErrorCode.FILE_EMPTY);
        if (!file.getOriginalFilename().toLowerCase().endsWith(".pdf")) throw new FileHandlingException(ErrorCode.INVALID_FILE_EXTENSION);

        try {
            // DB에 채팅방 생성
            ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.builder()
                    .title(file.getOriginalFilename())
                    .build());

            // PDF 파싱
            Resource resource = file.getResource();
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
            List<Document> documents = pdfReader.get();

            if (documents.isEmpty()) throw new FileHandlingException(ErrorCode.DOCUMENT_PARSING_ERROR);

            // 문서 스플릿 및 메타데이터(roomId) 추가
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> splitDocuments = splitter.apply(documents);

            // 모든 문서 조각에 roomId를 태깅하여 저장
            for (Document doc : splitDocuments) {
                doc.getMetadata().put("roomId", chatRoom.getId());
            }

            vectorStore.add(splitDocuments);

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
    public ChatResponseDto ask(Long roomId, ChatRequestDto requestDto) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ChatRoomFindingException(ErrorCode.CHATROOM_NOT_FOUND));

        // 사용자 질문 DB 저장
        chatMessageRepository.save(ChatMessage.builder()
                .chatRoom(chatRoom)
                .role(ChatMessage.MessageRole.USER)
                .content(requestDto.getMessage())
                .build());

        try {
            // 벡터 검색 (roomId가 일치하는 문서만 검색)
            // Filter Expression: "roomId == 123"
            List<Document> similarDocuments = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(requestDto.getMessage())
                            .topK(5)
                            .filterExpression("roomId == " + roomId) // 필터링
                            .build()
            );

            String context = similarDocuments.isEmpty() ? "" :
                    similarDocuments.stream().map(Document::getText).collect(Collectors.joining("\n"));

            // 프롬포트 생성
            PromptTemplate template = getPromptTemplate();
            Prompt prompt = template.create(Map.of("context", context, "message", requestDto.getMessage()));

            // AI 응답 생성 및 저장
            String aiAnswer = chatClient.prompt(prompt).call().content();

            chatMessageRepository.save(ChatMessage.builder()
                    .chatRoom(chatRoom)
                    .role(ChatMessage.MessageRole.AI)
                    .content(aiAnswer)
                    .build());

            return ChatResponseDto.builder()
                    .question(requestDto.getMessage())
                    .answer(aiAnswer)
                    .build();

        } catch (Exception e) {
            log.error("AI Error", e);
            throw new AiProcessingException(ErrorCode.AI_SERVICE_ERROR);
        }
    }

    @Override
    public void addDocumentToRoom(Long roomId, MultipartFile file) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ChatRoomFindingException(ErrorCode.CHATROOM_NOT_FOUND));

        // 파일 정보 DB 저장
        chatDocumentRepository.save(ChatDocument.builder()
                .chatRoom(chatRoom)
                .fileName(file.getOriginalFilename())
                .build());

        // PDF 파싱 및 벡터 저장
        try {
            Resource resource = file.getResource();
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
            List<Document> documents = pdfReader.get();
            TokenTextSplitter  splitter = new TokenTextSplitter();
            List<Document> splitDocuments = splitter.apply(documents);

            // 기존 방 번호(roomId)를 그대로 태깅
            for (Document doc : splitDocuments) {
                doc.getMetadata().put("roomId", chatRoom.getId());
            }
            vectorStore.add(splitDocuments);

            // 시스템 메시지 추가 (사용자에게 알림)
            chatMessageRepository.save(ChatMessage.builder()
                    .chatRoom(chatRoom)
                    .role(ChatMessage.MessageRole.AI)
                    .content("📎 **" + file.getOriginalFilename() + "** 문서가 추가되었습니다.")
                    .build());

        } catch (Exception e) {
            throw new FileHandlingException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public void deleteChatRoom(Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ChatRoomFindingException(ErrorCode.CHATROOM_NOT_FOUND));

        chatRoomRepository.delete(chatRoom);
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
            4. **언어**: 한국어로 자연스럽고 정중하게(존댓말) 답변하세요.
            
            [Context]
            {context}
            
            [Question]
            {message}
            """;

        return new PromptTemplate(promptText);
    }
}
