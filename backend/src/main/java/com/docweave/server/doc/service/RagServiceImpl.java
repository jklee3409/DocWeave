package com.docweave.server.doc.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    @Override
    public void uploadPdf(MultipartFile file) {
        Resource resource = file.getResource();
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
        List<Document> documents = pdfReader.get();

        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> splitDocuments = splitter.apply(documents);

        vectorStore.add(splitDocuments);
    }

    @Override
    public String ask(String message) {
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(message)
                        .topK(3)
                        .build()
        );

        String context = similarDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        String promptText = """
                너는 'DocWeave' 라는 지능형 문서 비서야.
                아래 [Context] 정보를 바탕으로 사용자의 질문에 답변해.
                만약 [Context]에 없는 내용이라면 '문서에 해당 내용이 없습니다'라고 대답해.
                
                [Context]
                {context}
                
                [Question]
                {message}
                """;

        PromptTemplate template = new PromptTemplate(promptText);
        Prompt prompt = template.create(Map.of(
                "context", context,
                "message", message
        ));

        return chatClient.prompt(prompt).call().content();
    }
}
