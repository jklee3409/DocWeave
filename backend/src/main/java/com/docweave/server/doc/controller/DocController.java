package com.docweave.server.doc.controller;

import com.docweave.server.common.dto.BaseResponseDto;
import com.docweave.server.doc.dto.request.ChatRequestDto;
import com.docweave.server.doc.dto.response.ChatResponseDto;
import com.docweave.server.doc.dto.response.UploadResponseDto;
import com.docweave.server.doc.service.RagService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/doc")
public class DocController {

    private final RagService ragService;

    public DocController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/upload")
    public BaseResponseDto<UploadResponseDto> upload(@RequestParam("file") MultipartFile file) {
        UploadResponseDto result = ragService.uploadPdf(file);
        return BaseResponseDto.success(result);
    }

    @PostMapping("/chat")
    public BaseResponseDto<ChatResponseDto> chat(@RequestBody ChatRequestDto requestDto) {
        ChatResponseDto result = ragService.ask(requestDto);
        return BaseResponseDto.success(result);
    }
}
