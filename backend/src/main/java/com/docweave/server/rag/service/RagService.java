package com.docweave.server.rag.service;

import org.springframework.web.multipart.MultipartFile;

public interface RagService {
    void uploadPdf(MultipartFile multipartFile);
    String ask(String message);
}
