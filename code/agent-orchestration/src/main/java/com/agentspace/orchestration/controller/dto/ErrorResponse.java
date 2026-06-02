package com.agentspace.orchestration.controller.dto;

import java.util.Map;

/**
 * 统一错误响应体。见详细设计 §2 通用约定。
 */
public record ErrorResponse(
        String errorCode,
        String message,
        Map<String, Object> details
) {

    public static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(errorCode, message, null);
    }

    public static ErrorResponse of(String errorCode, String message, Map<String, Object> details) {
        return new ErrorResponse(errorCode, message, details);
    }
}
