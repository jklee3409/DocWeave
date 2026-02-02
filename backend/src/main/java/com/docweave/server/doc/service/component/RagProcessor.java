package com.docweave.server.doc.service.component;

import com.docweave.server.common.exception.ErrorCode;
import com.docweave.server.doc.entity.DocContent;
import com.docweave.server.doc.exception.AiProcessingException;
import com.docweave.server.doc.exception.GuardrailException;
import com.docweave.server.doc.repository.DocContentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagProcessor {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final DocContentRepository docContentRepository;

    @Value("classpath:prompts/system-rag-prompt.st")
    private Resource ragPromptResource;

    private static final double SIMILARITY_THRESHOLD = 0.4;

    public String executeRag(Long userId, Long roomId, String message, String conversationHistory, StopWatch stopWatch) {
        // Vector Search: 질문과 유사한 'Child' 청크 검색 (사용자 격리 적용)
        List<Document> similarChildren = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(message)
                        .topK(2)
                        .filterExpression(String.format("roomId == '%s' && userId == '%s'", roomId, userId))
                        .build()
        );

        // Parent ID 추출
        Set<Long> parentIds = similarChildren.stream()
                .map(doc -> {
                    Object pid = doc.getMetadata().get("parent_id");
                    return pid != null ? Long.valueOf(pid.toString()) : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // RDB에서 Parent 조회
        String contextStr = "";
        if (!parentIds.isEmpty()) {
            List<DocContent> parentContents = docContentRepository.findAllByIdIn(new ArrayList<>(parentIds));
            contextStr = parentContents.stream()
                    .map(DocContent::getContent)
                    .collect(Collectors.joining("\n\n"));
        }

        final String finalContext = contextStr;

        // 프롬포트 생성
        PromptTemplate template = new PromptTemplate(ragPromptResource);
        Prompt prompt = template.create(Map.of("history", conversationHistory, "context", finalContext, "message", message));
        stopWatch.stop();

        // 병렬 처리 시작
        log.info("🚀 [Mode: Parallel] Executing Parallel Processing...");
        stopWatch.start("2. Parallel Processing (LLM + Context Embed)");

        // AI 응답 생성 및 컨텍스트 임베딩 병렬 처리
        log.info("Generating answer for room: {}", roomId);
        CompletableFuture<String> answerFuture = CompletableFuture.supplyAsync(() ->
                chatClient.prompt(prompt).call().content()
        );

        CompletableFuture<float[]> contextEmbeddingFuture = CompletableFuture.supplyAsync(() ->
                embeddingModel.embed(finalContext)
        );

        // 두 작업이 모두 완료될 때까지 대기
        CompletableFuture.allOf(answerFuture, contextEmbeddingFuture).join();
        stopWatch.stop();

        try {
            String rawAnswer = answerFuture.get();
            float[] contextVector = contextEmbeddingFuture.get();

            if (rawAnswer == null || rawAnswer.isBlank()) {
                throw new AiProcessingException(ErrorCode.AI_SERVICE_ERROR);
            }

            // 가드레일 검증 (병렬 처리된 Vector 사용)
            log.info("Validating answer quality for room: {}", roomId);
            stopWatch.start("3. Validation (Optimized)");
            boolean isValid = validateResponse(contextVector, rawAnswer);
            stopWatch.stop();

            if (!isValid) {
                log.warn("Guardrail validation failed. RoomId: {}, Input: {}", roomId, message);
                throw new GuardrailException(ErrorCode.GUARDRAIL_BLOCKED);
            }

            return rawAnswer;

        } catch (GuardrailException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI Processing Error", e);
            throw new AiProcessingException(ErrorCode.AI_SERVICE_ERROR);
        }
    }

    private boolean validateResponse(float[] contextVector, String answer) {
        // 규칙 기반 필터링
        if (answer.contains("제공된 문서에서 해당 내용을 찾을 수 없습니다")) return true;
        if (answer.length() < 5) return false;

        try {
            float[] answerVector = embeddingModel.embed(answer);

            double similarity = cosineSimilarity(contextVector, answerVector);
            log.debug("Validation Similarity Score: {}", similarity);

            return similarity >= SIMILARITY_THRESHOLD;
        } catch (Exception e) {
            log.error("Similarity Calculation Failed", e);
            return true;
        }
    }

    private double cosineSimilarity(float[] v1, float[] v2) {
        // 배열 유효성 검사
        if (v1 == null || v2 == null || v1.length != v2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += Math.pow(v1[i], 2);
            normB += Math.pow(v2[i], 2);
        }

        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}