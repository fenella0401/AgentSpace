package com.agentspace.orchestration.service.exception;

/**
 * 同一 Idempotency-Key 但请求体不一致。对应 POST /runs 的 409 CONFLICT。见详细设计 §2.1。
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
