package com.docweave.server.common.exception;

import com.docweave.server.common.dto.BaseResponseDto;
import com.docweave.server.common.dto.ErrorResponseDto;
import com.docweave.server.doc.exception.AiProcessingException;
import com.docweave.server.doc.exception.ChatRoomFindingException;
import com.docweave.server.doc.exception.FileHandlingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FileHandlingException.class)
    public BaseResponseDto<ErrorResponseDto> handleFileHandlingException(FileHandlingException e) {
        log.error("File Handling Error: {}", e.getMessage());
        return BaseResponseDto.fail(e.getErrorCode());
    }

    @ExceptionHandler(AiProcessingException.class)
    public BaseResponseDto<ErrorResponseDto> handleAiProcessingException(AiProcessingException e) {
        log.error("AI Processing Error: {}", e.getMessage());
        return BaseResponseDto.fail(e.getErrorCode());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public BaseResponseDto<ErrorResponseDto> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.error("File Size Exceeded: {}", e.getMessage());
        return BaseResponseDto.fail(ErrorCode.FILE_SIZE_EXCEEDED);
    }

    @ExceptionHandler(ChatRoomFindingException.class)
    public BaseResponseDto<ErrorResponseDto> handleFindingChatRoomException(ChatRoomFindingException e) {
        log.error("Can Not Found ChatRoom: {}", e.getMessage());
        return BaseResponseDto.fail(e.getErrorCode());
    }

    @ExceptionHandler(Exception.class)
    public BaseResponseDto<ErrorResponseDto> handleException(Exception e) {
        log.error("Unhandled Exception: ", e);
        return BaseResponseDto.fail(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}