package com.agentspace.orchestration.client.dto;

import com.agentspace.orchestration.model.AttemptStatus;

import java.time.Instant;

/**
 * Agent Core QueryAttempt 响应：用于重启恢复 / 对账。见概要设计 §8.1(3)。
 *
 * @param found 是否在 Agent Core 侧找到该 RuntimeAttempt；false 表示未送达/已回收，
 *              其余字段无意义（reconcile 据此判 RUNTIME_CREATE_FAILED）。
 */
public record QueryAttemptResponse(
        String attemptId,
        boolean found,
        String runtimeAttemptRef,
        AttemptStatus status,
        Instant lastHeartbeatAt,
        Integer exitCode,
        String failureReason
) {
}
