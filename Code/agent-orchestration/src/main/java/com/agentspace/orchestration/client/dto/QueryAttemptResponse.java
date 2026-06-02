package com.agentspace.orchestration.client.dto;

import com.agentspace.orchestration.model.AttemptStatus;

import java.time.Instant;

/**
 * Agent Core QueryAttempt 响应：用于重启恢复 / 对账。见概要设计 §8.1(3)。
 */
public record QueryAttemptResponse(
        String attemptId,
        String runtimeAttemptRef,
        AttemptStatus status,
        Instant lastHeartbeatAt,
        Integer exitCode,
        String failureReason
) {
}
