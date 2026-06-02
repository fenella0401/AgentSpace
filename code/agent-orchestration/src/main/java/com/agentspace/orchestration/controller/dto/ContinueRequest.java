package com.agentspace.orchestration.controller.dto;

/**
 * POST /runs/{runId}/steps/{stepId}/continue 请求体。见详细设计 §2.4。
 */
public record ContinueRequest(
        String feedback,
        String actionKey
) {
}
