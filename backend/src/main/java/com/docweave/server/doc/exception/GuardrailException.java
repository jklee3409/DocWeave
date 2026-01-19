package com.docweave.server.doc.exception;

import com.docweave.server.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class GuardrailException extends RuntimeException {
    private final ErrorCode errorCode;

    public GuardrailException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}