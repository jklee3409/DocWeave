package com.docweave.server.doc.exception;

import com.docweave.server.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class FileHandlingException extends RuntimeException {
    private final ErrorCode errorCode;

    public FileHandlingException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
