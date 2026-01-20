package com.docweave.server.doc.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentIngestionRequestDto {
    private Long roomId;
    private Long documentId;
    private String tempFilePath;
    private String originalFileName;
}
