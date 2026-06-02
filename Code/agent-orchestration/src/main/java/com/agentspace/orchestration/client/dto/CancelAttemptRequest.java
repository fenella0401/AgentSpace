package com.agentspace.orchestration.client.dto;

/**
 * Agent Core CancelAttempt 请求。cancel 必须幂等。见概要设计 §8.1(2)。
 *
 * @param attemptId       目标 attempt
 * @param keepSessionContext 是否保留对话上下文（sessionRef 指向的内容），默认 true 以便后续恢复
 */
public record CancelAttemptRequest(
        String attemptId,
        boolean keepSessionContext
) {
}
