package com.docweave.server.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // Global (10000 - 19999)
    INTERNAL_SERVER_ERROR(10000, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT_VALUE(10001, "INVALID_INPUT_VALUE", "입력값이 올바르지 않습니다."),

    // File Upload (20000 ~)
    FILE_EMPTY(20000, "FILE_EMPTY", "업로드된 파일이 비어있습니다."),
    FILE_UPLOAD_FAILED(20001, "FILE_UPLOAD_FAILED", "파일 업로드 처리에 실패했습니다."),
    INVALID_FILE_EXTENSION(20002, "INVALID_FILE_EXTENSION", "지원하지 않는 파일 형식입니다. PDF만 가능합니다."),
    FILE_SIZE_EXCEEDED(20003, "FILE_SIZE_EXCEEDED", "파일 크기가 허용 범위를 초과했습니다."),

    // AI & RAG (30000 ~)
    AI_SERVICE_ERROR(30000, "AI_SERVICE_ERROR", "AI 모델 호출 중 오류가 발생했습니다."),
    VECTOR_STORE_ERROR(30001, "VECTOR_STORE_ERROR", "벡터 데이터베이스 저장/검색 중 오류가 발생했습니다."),
    DOCUMENT_PARSING_ERROR(30002, "DOCUMENT_PARSING_ERROR", "문서 내용을 읽는 도중 오류가 발생했습니다."),
    GUARDRAIL_BLOCKED(30003, "GUARDRAIL_BLOCKED", "AI 답변이 신뢰성 기준을 충족하지 못해 차단되었습니다."),

    // ChatRoom (40000 ~)
    CHATROOM_NOT_FOUND(40000, "CHATROOM_NOT_FOUND", "존재하지 않는 채팅방입니다.");

    private final int code;
    private final String name;
    private final String message;
}