package com.docweave.server.doc.service.impl;

import com.docweave.server.common.exception.ErrorCode;
import com.docweave.server.doc.dto.ChatMessageDto;
import com.docweave.server.doc.dto.ChatRoomDto;
import com.docweave.server.doc.dto.request.ChatRequestDto;
import com.docweave.server.doc.dto.request.DocumentIngestionRequestDto;
import com.docweave.server.doc.dto.response.ChatResponseDto;
import com.docweave.server.doc.entity.ChatDocument;
import com.docweave.server.doc.entity.ChatMessage;
import com.docweave.server.doc.entity.ChatMessage.MessageRole;
import com.docweave.server.doc.entity.ChatRoom;
import com.docweave.server.doc.exception.AiProcessingException;
import com.docweave.server.doc.exception.FileHandlingException;
import com.docweave.server.doc.exception.GuardrailException;
import com.docweave.server.doc.service.DocumentIngestionService;
import com.docweave.server.doc.service.RagService;
import com.docweave.server.doc.service.component.ChatDomainManager;
import com.docweave.server.doc.service.component.FileHandler;
import com.docweave.server.doc.service.component.RagProcessor;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final DocumentIngestionService documentIngestionService;
    private final ChatDomainManager chatDomainManager;
    private final FileHandler fileHandler;
    private final RagProcessor ragProcessor;

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomDto> getChatRooms() {
        return chatDomainManager.getAllChatRooms();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getChatMessages(Long roomId) {
        return chatDomainManager.getChatMessages(roomId);
    }

    @Override
    @Transactional
    public ChatRoomDto createChatRoom(MultipartFile file) {
        fileHandler.validateFile(file);

        try {
            // DB에 채팅방 생성
            ChatRoom chatRoom = chatDomainManager.createChatRoomEntity(file.getOriginalFilename());

            // 파일 메타데이터 RDB 저장
            ChatDocument chatDocument = chatDomainManager.createChatDocument(chatRoom, file.getOriginalFilename());

            // 임시 파일 저장
            String tempFilePath = fileHandler.saveTempFile(file);

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
        fileHandler.validateFile(file);
        ChatRoom chatRoom = chatDomainManager.findChatRoomById(roomId);

        chatDomainManager.updateLastActiveAt(chatRoom);

        try {
            ChatDocument chatDocument = chatDomainManager.createChatDocument(chatRoom, file.getOriginalFilename());

            String tempFilePath = fileHandler.saveTempFile(file);

            DocumentIngestionRequestDto request = DocumentIngestionRequestDto.builder()
                    .roomId(roomId)
                    .documentId(chatDocument.getId())
                    .tempFilePath(tempFilePath)
                    .originalFileName(file.getOriginalFilename())
                    .build();

            documentIngestionService.processDocument(request);

            chatDomainManager.saveChatMessage(ChatMessage.builder()
                    .chatRoom(chatRoom)
                    .role(MessageRole.AI)
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
        ChatRoom chatRoom = chatDomainManager.findChatRoomById(roomId);

        chatDomainManager.updateLastActiveAt(chatRoom);

        // 사용자 질문 DB 저장
        chatDomainManager.saveChatMessage(ChatMessage.builder()
                .chatRoom(chatRoom)
                .role(MessageRole.USER)
                .content(requestDto.getMessage())
                .build());

        try {
            // 대화 내역 조회 (최대 6개)
            List<ChatMessage> chatHistoryList = chatDomainManager.getRecentChatHistory(roomId);

            // 대화 내역 포맷팅
            String conversationHistory = chatHistoryList.stream()
                    .map(msg -> String.format("%s: %s", msg.getRole(), msg.getContent()))
                    .collect(Collectors.joining("\n"));

            // RagProcessor 호출 - 임베딩 검색, LLM 응답 생성, 검증 포함
            String rawAnswer = ragProcessor.executeRag(roomId, requestDto.getMessage(), conversationHistory, stopWatch);

            log.info(stopWatch.prettyPrint());

            // 검증 통과 시 DB 저장
            chatDomainManager.saveChatMessage(ChatMessage.builder()
                    .chatRoom(chatRoom)
                    .role(MessageRole.AI)
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
        chatDomainManager.deleteChatRoom(roomId);
    }
}