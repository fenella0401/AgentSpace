package com.agentspace.orchestration.controller.dto;

/**
 * POST /runs/{runId}/steps/{stepId}/retry 请求体。见详细设计 §2.5。
 */
public record RetryRequest(
        boolean resumeSession,
        String actionKey
) {
}
