package com.agentspace.orchestration.client.dto;

import com.agentspace.orchestration.model.AttemptStatus;

/**
 * Agent Core StartAttempt 响应。见概要设计 §8.1。
 */
public record StartAttemptResponse(
        String runtimeAttemptRef,
        AttemptStatus initialStatus
) {
}
