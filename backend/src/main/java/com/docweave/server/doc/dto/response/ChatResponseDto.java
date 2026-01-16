package com.docweave.server.doc.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatResponseDto {
    private String question;
    private String answer;
}
