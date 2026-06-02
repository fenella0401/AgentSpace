package com.agentspace.orchestration.controller;

import com.agentspace.orchestration.controller.dto.ErrorResponse;
import com.agentspace.orchestration.service.AgentFlowValidationException;
import com.agentspace.orchestration.service.EventAttributionException;
import com.agentspace.orchestration.service.IdempotencyConflictException;
import com.agentspace.orchestration.service.PromptRenderException;
import com.agentspace.orchestration.service.StepActionConflictException;
import com.agentspace.orchestration.service.StepActionNotFoundException;
import com.agentspace.orchestration.service.StepActionValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 统一异常 → 错误码映射。见详细设计 §2 通用约定（401/403 随鉴权 MVP 暂缓）。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AgentFlowValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(AgentFlowValidationException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of("VALIDATION_ERROR", ex.getMessage(),
                        Map.of("violations", ex.violations())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of("VALIDATION_ERROR", "请求体校验失败: " + ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IdempotencyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(PromptRenderException.class)
    public ResponseEntity<ErrorResponse> handlePromptRender(PromptRenderException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of("PROMPT_RENDER_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(EventAttributionException.class)
    public ResponseEntity<ErrorResponse> handleAttribution(EventAttributionException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(StepActionConflictException.class)
    public ResponseEntity<ErrorResponse> handleStepConflict(StepActionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(StepActionValidationException.class)
    public ResponseEntity<ErrorResponse> handleStepValidation(StepActionValidationException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(StepActionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStepNotFound(StepActionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleStatus(ResponseStatusException ex) {
        String code = switch (ex.getStatusCode().value()) {
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            case 422 -> "VALIDATION_ERROR";
            case 429 -> "RESOURCE_EXHAUSTED";
            default -> "ERROR";
        };
        return ResponseEntity.status(ex.getStatusCode())
                .body(ErrorResponse.of(code, ex.getReason()));
    }
}
