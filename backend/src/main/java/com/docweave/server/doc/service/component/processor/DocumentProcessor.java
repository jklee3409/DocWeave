package com.docweave.server.doc.service.component.processor;

import com.docweave.server.common.constant.EmbeddingConstant;
import com.docweave.server.doc.dto.request.DocumentIngestionRequestDto;
import com.docweave.server.doc.entity.ChatDocument;
import com.docweave.server.doc.entity.ChatMessage;
import com.docweave.server.doc.entity.ChatRoom;
import com.docweave.server.doc.entity.DocContent;
import com.docweave.server.doc.repository.ChatDocumentRepository;
import com.docweave.server.doc.repository.ChatMessageRepository;
import com.docweave.server.doc.repository.ChatRoomRepository;
import com.docweave.server.doc.repository.DocContentRepository;
import com.docweave.server.doc.service.component.parser.HtmlToMarkdownConverter;
import com.docweave.server.doc.service.component.parser.TikaClient;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProcessor {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatDocumentRepository chatDocumentRepository;
    private final DocContentRepository docContentRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final VectorStore vectorStore;

    private final TikaClient tikaClient;
    private final HtmlToMarkdownConverter htmlToMarkdownConverter;

    @Transactional
    public void execute(DocumentIngestionRequestDto request) {
        log.info("Starting document processing for docId: {}", request.getDocumentId());

        ChatDocument chatDocument = chatDocumentRepository.findById(request.getDocumentId())
                .orElse(null);

        if (chatDocument == null) {
            log.error("Document not found: {}", request.getDocumentId());
            return;
        }

        chatDocument.setStatus(ChatDocument.ProcessingStatus.PROCESSING);
        File tempFile = new File(request.getTempFilePath());

        try {
            // 1. Tika Parsing
            String xhtmlContent = tikaClient.parsePdfToXhtml(tempFile);

            // 2. HTML -> Markdown 변환
            String markdownContent = htmlToMarkdownConverter.convert(xhtmlContent);

            if (markdownContent == null || markdownContent.trim().isEmpty()) {
                log.warn("추출된 텍스트가 없습니다. docId: {}", request.getDocumentId());
                handleEmptyContent(chatDocument, request);
                return;
            }

            Document rawDocument = new Document(markdownContent, Map.of(
                    "source_file", request.getOriginalFileName(),
                    "roomId", request.getRoomId()
            ));

            List<Document> rawDocuments = Collections.singletonList(rawDocument);

            // 3. Chunking
            TokenTextSplitter parentSplitter = new TokenTextSplitter(EmbeddingConstant.PARENT_CHUNK_SIZE, 100, 10, 1000, true);
            List<Document> parentDocs = parentSplitter.apply(rawDocuments);
            List<Document> childDocsToEmbed = new ArrayList<>();

            Long userId = chatDocument.getChatRoom().getUser().getId();

            for (Document pDoc : parentDocs) {
                // Parent RDB 저장
                DocContent savedParent = docContentRepository.save(DocContent.builder()
                        .chatDocument(chatDocument)
                        .user(chatDocument.getChatRoom().getUser())
                        .content(pDoc.getText())
                        .pageNumber(0)
                        .build());

                // Child Chunking
                TokenTextSplitter childSplitter = new TokenTextSplitter(EmbeddingConstant.CHILD_CHUNK_SIZE, 50, 10, 100, true);
                List<Document> childDocs = childSplitter.apply(Collections.singletonList(pDoc));

                for (Document cDoc : childDocs) {
                    cDoc.getMetadata().put("parent_id", savedParent.getId());
                    cDoc.getMetadata().put("roomId", request.getRoomId());
                    cDoc.getMetadata().put("userId", userId);
                    cDoc.getMetadata().put("source_file", request.getOriginalFileName());
                    cDoc.getMetadata().put("page_number", 0);
                }
                childDocsToEmbed.addAll(childDocs);
            }

            if (!childDocsToEmbed.isEmpty()) {
                vectorStore.add(childDocsToEmbed);

                chatDocument.setStatus(ChatDocument.ProcessingStatus.COMPLETED);
                sendSystemMessage(request.getRoomId(), "✅ **" + request.getOriginalFileName() + "** 분석이 완료되었습니다. 이제 질문하실 수 있습니다!");

            } else {
                log.warn("청킹된 문서가 없습니다. docId: {}", request.getDocumentId());
                handleEmptyContent(chatDocument, request);
            }

            log.info("Document processing completed for docId: {}", request.getDocumentId());

        } catch (Exception e) {
            log.error("Document processing failed", e);
            chatDocument.setStatus(ChatDocument.ProcessingStatus.FAILED);
            sendSystemMessage(request.getRoomId(), "⚠️ **" + request.getOriginalFileName() + "** 처리 중 오류가 발생했습니다.");
        } finally {
            try {
                Files.deleteIfExists(Path.of(request.getTempFilePath()));
            } catch (Exception e) {
                log.warn("Failed to delete temp file: {}", request.getTempFilePath());
            }
        }
    }

    private void handleEmptyContent(ChatDocument chatDocument, DocumentIngestionRequestDto request) {
        chatDocument.setStatus(ChatDocument.ProcessingStatus.COMPLETED);
        sendSystemMessage(request.getRoomId(),
                "⚠️ **" + request.getOriginalFileName() + "** 에서 텍스트를 추출하지 못했습니다.\n(손글씨나 흐릿한 이미지는 인식이 안 될 수 있습니다.)");
    }

    private void sendSystemMessage(Long roomId, String content) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId).orElseThrow();
        chatMessageRepository.save(ChatMessage.builder()
                .chatRoom(chatRoom)
                .role(ChatMessage.MessageRole.AI)
                .content(content)
                .build());
    }
}