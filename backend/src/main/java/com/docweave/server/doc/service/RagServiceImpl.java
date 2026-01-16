package com.docweave.server.doc.service;

import com.docweave.server.common.exception.ErrorCode;
import com.docweave.server.doc.dto.request.ChatRequestDto;
import com.docweave.server.doc.dto.response.ChatResponseDto;
import com.docweave.server.doc.dto.response.UploadResponseDto;
import com.docweave.server.doc.exception.AiProcessingException;
import com.docweave.server.doc.exception.FileHandlingException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    @Override
    public UploadResponseDto uploadPdf(MultipartFile file) {
        if (file.isEmpty()) throw new FileHandlingException(ErrorCode.FILE_EMPTY);

        if (!Objects.requireNonNull(file.getOriginalFilename()).toLowerCase().endsWith(".pdf")) throw new FileHandlingException(ErrorCode.INVALID_FILE_EXTENSION);

        try {
            Resource resource = file.getResource();
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
            List<Document> documents = pdfReader.get();

            if (documents.isEmpty()) throw new FileHandlingException(ErrorCode.DOCUMENT_PARSING_ERROR);

            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> splitDocuments = splitter.apply(documents);

            vectorStore.add(splitDocuments);

            return UploadResponseDto.builder()
                    .fileName(file.getOriginalFilename())
                    .message("문서가 성공적으로 학습되었습니다.")
                    .build();

        } catch (Exception e) {
            log.error("PDF Parsing Error", e);
            throw new FileHandlingException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public ChatResponseDto ask(ChatRequestDto requestDto) {
        try {
            // 1. 유사도 검색
            List<Document> similarDocuments = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(requestDto.getMessage())
                            .topK(3)
                            .build()
            );

            // 2. 컨텍스트 조합
            String context = similarDocuments.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n"));

            // 3. 프롬프트 생성
            PromptTemplate template = getPromptTemplate();
            Prompt prompt = template.create(Map.of(
                    "context", context,
                    "message", requestDto.getMessage()
            ));

            // 4. AI 호출
            String aiAnswer = chatClient.prompt(prompt).call().content();

            return ChatResponseDto.builder()
                    .question(requestDto.getMessage())
                    .answer(aiAnswer)
                    .build();

        } catch (Exception e) {
            log.error("AI Inference Error", e);
            throw new AiProcessingException(ErrorCode.AI_SERVICE_ERROR);
        }
    }

    private static @NonNull PromptTemplate getPromptTemplate() {
        String promptText = """
            당신은 'DocWeave' 라는 지능형 문서 분석 AI 어시스턴트입니다.
            사용자의 질문에 대해 아래 제공된 [Context] 정보를 바탕으로 정확하고 전문적인 답변을 제공하세요.
            
            ## 지침 (Instructions)
            1. **근거 기반**: 오직 [Context]에 있는 내용만 사용하여 답변하세요. 외부 지식이나 상상을 섞지 마세요.
            2. **양심적 거절**: 만약 [Context]에 질문에 대한 답변이 포함되어 있지 않다면, 솔직하게 "제공된 문서에서 해당 내용을 찾을 수 없습니다."라고 답변하세요. 내용을 지어내지 마세요.
            3. **구조화된 답변**: 답변은 가독성이 좋게 **Markdown** 문법을 사용하세요.
               - 핵심 키워드는 **볼드체**로 강조하세요.
               - 나열되는 정보는 글머리 기호(-, 1.)를 사용하여 정리하세요.
               - 필요하다면 표(Table) 형식을 사용해도 좋습니다.
            4. **언어**: 한국어로 자연스럽고 정중하게(존댓말) 답변하세요.
            
            [Context]
            {context}
            
            [Question]
            {message}
            """;

        return new PromptTemplate(promptText);
    }
}
