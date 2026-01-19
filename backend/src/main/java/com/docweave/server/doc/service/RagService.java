package com.docweave.server.doc.service;

import com.docweave.server.doc.dto.ChatMessageDto;
import com.docweave.server.doc.dto.ChatRoomDto;
import com.docweave.server.doc.dto.request.ChatRequestDto;
import com.docweave.server.doc.dto.response.ChatResponseDto;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

public interface RagService {
    List<ChatRoomDto> getChatRooms();
    List<ChatMessageDto> getChatMessages(Long roomId);
    ChatRoomDto createChatRoom(MultipartFile file);
    ChatResponseDto ask(Long roomId, ChatRequestDto requestDto);
    void addDocumentToRoom(Long roomId, MultipartFile file);
    void deleteChatRoom(Long roomId);
}
