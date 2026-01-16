package com.docweave.server.doc.service;

import com.docweave.server.doc.dto.request.ChatRequestDto;
import com.docweave.server.doc.dto.response.ChatResponseDto;
import com.docweave.server.doc.dto.response.UploadResponseDto;
import org.springframework.web.multipart.MultipartFile;

public interface RagService {
    UploadResponseDto uploadPdf(MultipartFile multipartFile);
    ChatResponseDto ask(ChatRequestDto requestDto);
}
