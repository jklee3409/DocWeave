package com.docweave.server.doc.service.component.processor;

import com.docweave.server.common.constant.EmbeddingConstant;
import com.docweave.server.common.exception.ErrorCode;
import com.docweave.server.doc.dto.request.DocumentIngestionRequestDto;
import com.docweave.server.doc.entity.ChatDocument;
import com.docweave.server.doc.entity.ChatMessage;
import com.docweave.server.doc.entity.ChatRoom;
import com.docweave.server.doc.entity.DocContent;
import com.docweave.server.doc.exception.FileHandlingException;
import com.docweave.server.doc.repository.ChatDocumentRepository;
import com.docweave.server.doc.repository.ChatMessageRepository;
import com.docweave.server.doc.repository.ChatRoomRepository;
import com.docweave.server.doc.repository.DocContentRepository;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
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
            // PDF 파싱
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(new FileSystemResource(tempFile));
            List<Document> rawDocuments = pdfReader.get();

            if (rawDocuments.isEmpty())
                throw new FileHandlingException(ErrorCode.FILE_EMPTY);

            // Parent-Child Chunking
            TokenTextSplitter parentSplitter = new TokenTextSplitter(EmbeddingConstant.PARENT_CHUNK_SIZE, 100, 10, 1000, true);
            List<Document> parentDocs = parentSplitter.apply(rawDocuments);
            List<Document> childDocsToEmbed = new ArrayList<>();

            Long userId = chatDocument.getChatRoom().getUser().getId();

            for (Document pDoc : parentDocs) {
                // Parent RDB 저장
                int pageNum = (int) pDoc.getMetadata().getOrDefault("page_number", 0);
                DocContent savedParent = docContentRepository.save(DocContent.builder()
                        .chatDocument(chatDocument)
                        .user(chatDocument.getChatRoom().getUser())
                        .content(pDoc.getText())
                        .pageNumber(pageNum)
                        .build());

                // Child Chunking & Tagging
                TokenTextSplitter childSplitter = new TokenTextSplitter(EmbeddingConstant.CHILD_CHUNK_SIZE, 50, 10, 100, true);
                List<Document> childDocs = childSplitter.apply(Collections.singletonList(pDoc));

                for (Document cDoc : childDocs) {
                    cDoc.getMetadata().put("parent_id", savedParent.getId());
                    cDoc.getMetadata().put("roomId", request.getRoomId());
                    cDoc.getMetadata().put("userId", userId);
                    cDoc.getMetadata().put("source_file", request.getOriginalFileName());
                    cDoc.getMetadata().put("page_number", pageNum);
                }
                childDocsToEmbed.addAll(childDocs);
            }

            // Vector Store 저장
            vectorStore.add(childDocsToEmbed);

            // 처리 완료 상태 업데이트
            chatDocument.setStatus(ChatDocument.ProcessingStatus.COMPLETED);

            ChatRoom chatRoom = chatRoomRepository.findById(request.getRoomId()).orElseThrow();
            chatMessageRepository.save(ChatMessage.builder()
                    .chatRoom(chatRoom)
                    .role(ChatMessage.MessageRole.AI)
                    .content("✅ **" + request.getOriginalFileName() + "** 분석이 완료되었습니다. 이제 질문하실 수 있습니다!")
                    .build());

            log.info("Document processing completed for docId: {}", request.getDocumentId());

        } catch (Exception e) {
            log.error("Document processing failed", e);
            chatDocument.setStatus(ChatDocument.ProcessingStatus.FAILED);

            ChatRoom chatRoom = chatRoomRepository.findById(request.getRoomId()).orElseThrow();
            chatMessageRepository.save(ChatMessage.builder()
                    .chatRoom(chatRoom)
                    .role(ChatMessage.MessageRole.AI)
                    .content("⚠️ **" + request.getOriginalFileName() + "** 처리 중 오류가 발생했습니다.")
                    .build());
        } finally {
            // 임시 파일 삭제
            try {
                Files.deleteIfExists(Path.of(request.getTempFilePath()));
            } catch (Exception e) {
                log.warn("Failed to delete temp file: {}", request.getTempFilePath());
            }
        }
    }
}