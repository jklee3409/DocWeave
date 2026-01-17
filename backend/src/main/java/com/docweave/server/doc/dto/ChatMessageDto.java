package com.docweave.server.doc.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatMessageDto {
    private String role;
    public String content;
}
