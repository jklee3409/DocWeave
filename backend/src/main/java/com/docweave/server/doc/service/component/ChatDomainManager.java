package com.docweave.server.doc.service.component;

import com.docweave.server.auth.entity.User;
import com.docweave.server.auth.repository.UserRepository;
import com.docweave.server.auth.security.SecurityUtil;
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
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ChatRoomDto> getAllChatRooms() {
        Long userId = SecurityUtil.getCurrentUserId();
        return chatRoomRepository.findAllByUserIdOrderByLastActiveAtDesc(userId).stream()
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
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        return chatRoomRepository.save(ChatRoom.builder()
                .user(user)
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
        Long userId = SecurityUtil.getCurrentUserId();
        return chatRoomRepository.findByIdAndUserId(roomId, userId)
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