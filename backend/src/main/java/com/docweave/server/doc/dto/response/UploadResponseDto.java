package com.docweave.server.doc.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadResponseDto {
    private String fileName;
    private String message;
}
