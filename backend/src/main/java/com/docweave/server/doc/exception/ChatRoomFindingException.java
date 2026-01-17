package com.docweave.server.doc.exception;

import com.docweave.server.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class ChatRoomFindingException extends RuntimeException {
    private final ErrorCode errorCode;

    public ChatRoomFindingException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
