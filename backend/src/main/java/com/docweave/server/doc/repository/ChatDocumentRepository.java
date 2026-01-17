package com.docweave.server.doc.repository;

import com.docweave.server.doc.entity.ChatDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatDocumentRepository extends JpaRepository<ChatDocument, Long> {
}
