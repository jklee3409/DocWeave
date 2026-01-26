package com.docweave.server.doc.service.impl;

import com.docweave.server.doc.dto.request.DocumentIngestionRequestDto;
import com.docweave.server.doc.service.DocumentIngestionService;
import com.docweave.server.doc.service.queue.IngestionQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

    private final IngestionQueueService ingestionQueueService;

    @Override
    @Transactional
    public void processDocument(DocumentIngestionRequestDto request) {
        ingestionQueueService.push(request);
    }
}
