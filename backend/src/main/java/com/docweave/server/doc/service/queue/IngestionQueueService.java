package com.docweave.server.doc.service.queue;

import com.docweave.server.common.constant.RedisConstant;
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

    public void push(DocumentIngestionRequestDto requestDto) {
        log.info("Pushing document ingestion task to Redis. docId: {}", requestDto.getDocumentId());
        redisTemplate.opsForList().rightPush(RedisConstant.DOC_INGESTION_QUEUE, requestDto);
    }
}
