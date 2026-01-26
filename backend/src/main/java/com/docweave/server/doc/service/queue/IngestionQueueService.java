package com.docweave.server.doc.service.queue;

import com.docweave.server.doc.dto.request.DocumentIngestionRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionQueueService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String QUEUE_NAME = "doc_ingestion_queue";

    public void push(DocumentIngestionRequestDto requestDto) {
        log.info("Pushing document ingestion task to Redis. docId: {}", requestDto.getDocumentId());
        redisTemplate.opsForList().rightPush(QUEUE_NAME, requestDto);
    }
}
