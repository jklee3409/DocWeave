package com.docweave.server.doc.service.queue;

import com.docweave.server.doc.dto.request.DocumentIngestionRequestDto;
import com.docweave.server.doc.service.component.DocumentProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionWorker {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DocumentProcessor documentProcessor;
    private final ObjectMapper objectMapper;

    private static final String QUEUE_NAME = "doc_ingestion_queue";

    @Scheduled(fixedDelay = 1000)
    public void consume() {
        try {
            // 2초 동안 대기하며 큐에서 메시지 가져오기 (있으면 즉시 반환)
            Object rawMessage = redisTemplate.opsForList().leftPop(QUEUE_NAME, 2, TimeUnit.SECONDS);

            if (rawMessage != null) {
                DocumentIngestionRequestDto requestDto = objectMapper.convertValue(rawMessage,
                        DocumentIngestionRequestDto.class);
                log.info("Consumed ingestion task from Redis. docId: {}", requestDto.getDocumentId());

                documentProcessor.execute(requestDto);
            }
        } catch (Exception e) {
            log.error("Error processing message from Redis queue: ", e);
        }
    }
}
