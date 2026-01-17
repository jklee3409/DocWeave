package com.docweave.server.doc.controller;

import com.docweave.server.common.dto.BaseResponseDto;
import com.docweave.server.doc.dto.ChatMessageDto;
import com.docweave.server.doc.dto.ChatRoomDto;
import com.docweave.server.doc.dto.request.ChatRequestDto;
import com.docweave.server.doc.dto.response.ChatResponseDto;
import com.docweave.server.doc.dto.response.UploadResponseDto;
import com.docweave.server.doc.service.RagService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/doc")
public class DocController {

    private final RagService ragService;

    @GetMapping("/rooms")
    public BaseResponseDto<List<ChatRoomDto>> getRooms() {
        return BaseResponseDto.success(ragService.getChatRooms());
    }

    @PostMapping("/rooms")
    public BaseResponseDto<ChatRoomDto> createRoom(@RequestParam("file") MultipartFile file) {
        return BaseResponseDto.success(ragService.createChatRoom(file));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public BaseResponseDto<List<ChatMessageDto>> getMessages(@PathVariable Long roomId) {
        return BaseResponseDto.success(ragService.getChatMessages(roomId));
    }

    @PostMapping("/rooms/{roomId}/chat")
    public BaseResponseDto<ChatResponseDto> chat(
            @PathVariable Long roomId,
            @RequestBody ChatRequestDto requestDto) {
        return BaseResponseDto.success(ragService.ask(roomId, requestDto));
    }

    @PostMapping("/rooms/{roomId}/files")
    public BaseResponseDto<Void> addFile(
            @PathVariable Long roomId,
            @RequestParam("file") MultipartFile file) {
        ragService.addDocumentToRoom(roomId, file);
        return BaseResponseDto.voidSuccess();
    }
}

