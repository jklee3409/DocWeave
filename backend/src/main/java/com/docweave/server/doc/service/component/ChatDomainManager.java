package com.docweave.server.doc.service.component;

import com.docweave.server.common.exception.ErrorCode;
import com.docweave.server.doc.dto.ChatMessageDto;
import com.docweave.server.doc.dto.ChatRoomDto;
import com.docweave.server.doc.entity.ChatDocument;
import com.docweave.server.doc.entity.ChatDocument.ProcessingStatus;
import com.docweave.server.doc.entity.ChatMessage;
import com.docweave.server.doc.entity.ChatRoom;
import com.docweave.server.doc.exception.ChatRoomFindingException;
import com.docweave.server.doc.repository.ChatDocumentRepository;
import com.docweave.server.doc.repository.ChatMessageRepository;
import com.docweave.server.doc.repository.ChatRoomRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChatDomainManager {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatDocumentRepository chatDocumentRepository;

    @Transactional(readOnly = true)
    public List<ChatRoomDto> getAllChatRooms() {
        return chatRoomRepository.findAllByOrderByLastActiveAtDesc().stream()
                .map(room -> ChatRoomDto.builder()
                        .id(room.getId())
                        .title(room.getTitle())
                        .createdAt(room.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getChatMessages(Long roomId) {
        return chatMessageRepository.findAllByChatRoomIdOrderByCreatedAtAscIdAsc(roomId).stream()
                .map(msg -> ChatMessageDto.builder()
                        .role(msg.getRole().name().toLowerCase())
                        .content(msg.getContent())
                        .build())
                .collect(Collectors.toList());
    }

    public ChatRoom createChatRoomEntity(String title) {
        return chatRoomRepository.save(ChatRoom.builder()
                .title(title)
                .lastActiveAt(LocalDateTime.now())
                .build());
    }

    public ChatDocument createChatDocument(ChatRoom chatRoom, String fileName) {
        return chatDocumentRepository.save(ChatDocument.builder()
                .chatRoom(chatRoom)
                .fileName(fileName)
                .status(ProcessingStatus.PENDING)
                .build());
    }

    public ChatRoom findChatRoomById(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ChatRoomFindingException(ErrorCode.CHATROOM_NOT_FOUND));
    }

    public void updateLastActiveAt(ChatRoom chatRoom) {
        chatRoom.updateLastActiveAt();
    }

    public void saveChatMessage(ChatMessage message) {
        chatMessageRepository.save(message);
    }

    public List<ChatMessage> getRecentChatHistory(Long roomId) {
        List<ChatMessage> chatHistoryList = chatMessageRepository.findTop6ByChatRoomIdOrderByCreatedAtDesc(roomId);
        Collections.reverse(chatHistoryList);
        return chatHistoryList;
    }

    public void deleteChatRoom(Long roomId) {
        ChatRoom chatRoom = findChatRoomById(roomId);
        chatRoomRepository.delete(chatRoom);
    }
}