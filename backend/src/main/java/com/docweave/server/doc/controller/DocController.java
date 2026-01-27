package com.docweave.server.doc.controller;

import com.docweave.server.auth.dto.common.CustomUserDetailsDto;
import com.docweave.server.common.dto.BaseResponseDto;
import com.docweave.server.doc.dto.ChatMessageDto;
import com.docweave.server.doc.dto.ChatRoomDto;
import com.docweave.server.doc.dto.request.ChatRequestDto;
import com.docweave.server.doc.dto.response.ChatResponseDto;
import com.docweave.server.doc.service.RagService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    public BaseResponseDto<List<ChatRoomDto>> getRooms(@AuthenticationPrincipal CustomUserDetailsDto customUserDetailsDto) {
        return BaseResponseDto.success(ragService.getChatRooms(customUserDetailsDto.getId()));
    }

    @PostMapping("/rooms")
    public BaseResponseDto<ChatRoomDto> createRoom(
            @AuthenticationPrincipal CustomUserDetailsDto customUserDetailsDto,
            @RequestParam("file") MultipartFile file
    ) {
        return BaseResponseDto.success(ragService.createChatRoom(customUserDetailsDto.getId(), file));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public BaseResponseDto<List<ChatMessageDto>> getMessages(@PathVariable Long roomId) {
        return BaseResponseDto.success(ragService.getChatMessages(roomId));
    }

    @PostMapping(value = "/rooms/{roomId}/chat")
    public BaseResponseDto<ChatResponseDto> chat(
            @AuthenticationPrincipal CustomUserDetailsDto customUserDetailsDto,
            @PathVariable Long roomId,
            @RequestBody ChatRequestDto requestDto) {
        return BaseResponseDto.success(ragService.ask(customUserDetailsDto.getId(), roomId, requestDto));
    }

    @PostMapping("/rooms/{roomId}/files")
    public BaseResponseDto<Void> addFile(
            @AuthenticationPrincipal CustomUserDetailsDto customUserDetailsDto,
            @PathVariable Long roomId,
            @RequestParam("file") MultipartFile file) {
        ragService.addDocumentToRoom(customUserDetailsDto.getId(), roomId, file);
        return BaseResponseDto.voidSuccess();
    }

    @DeleteMapping("/rooms/{roomId}")
    public BaseResponseDto<Void> deleteRoom(
            @AuthenticationPrincipal CustomUserDetailsDto customUserDetailsDto,
            @PathVariable Long roomId
    ) {
        ragService.deleteChatRoom(customUserDetailsDto.getId(), roomId);
        return BaseResponseDto.voidSuccess();
    }
}

