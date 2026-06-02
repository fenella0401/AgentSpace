package com.agentspace.orchestration.client.dto;

import com.agentspace.orchestration.model.AttemptStatus;

/**
 * Agent Core CancelAttempt 响应。RuntimeAttempt 不存在时返回可识别状态。见概要设计 §8.1(2)。
 */
public record CancelAttemptResponse(
        String attemptId,
        AttemptStatus status,
        boolean runtimeFound
) {
}
