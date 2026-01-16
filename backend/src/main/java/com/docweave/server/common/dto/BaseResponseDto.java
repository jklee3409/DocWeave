package com.docweave.server.common.dto;

import com.docweave.server.common.exception.ErrorCode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BaseResponseDto<T> {
    private Integer statusCode;
    private String message;
    private T data;

    public static <T> BaseResponseDto<T> success(T data) {
        return BaseResponseDto.<T>builder()
                .statusCode(200)
                .message("Success")
                .data(data)
                .build();
    }

    public static BaseResponseDto<Void> voidSuccess() {
        return BaseResponseDto.<Void>builder()
                .statusCode(200)
                .message("Success")
                .data(null)
                .build();
    }

    public static BaseResponseDto<ErrorResponseDto> fail(ErrorCode errorCode) {
        return BaseResponseDto.<ErrorResponseDto>builder()
                .statusCode(errorCode.getCode())
                .message("Fail")
                .data(ErrorResponseDto.of(errorCode))
                .build();
    }
}
