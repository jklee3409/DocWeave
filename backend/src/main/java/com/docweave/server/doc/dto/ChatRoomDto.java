package com.docweave.server.doc.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatRoomDto {
    private Long id;
    private String title;
    private LocalDateTime createdAt;
}
