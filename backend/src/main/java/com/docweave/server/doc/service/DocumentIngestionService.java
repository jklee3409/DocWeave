package com.docweave.server.doc.service;

import com.docweave.server.doc.dto.request.DocumentIngestionRequestDto;

public interface DocumentIngestionService {
    void processDocument(DocumentIngestionRequestDto requestDto);
}
