package com.docweave.server.doc.repository;

import com.docweave.server.doc.entity.DocContent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocContentRepository extends JpaRepository<DocContent, Long> {
    List<DocContent> findAllByIdIn(List<Long> ids);
}
