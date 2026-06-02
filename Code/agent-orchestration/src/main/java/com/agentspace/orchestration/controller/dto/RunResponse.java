package com.agentspace.orchestration.controller.dto;

import com.agentspace.orchestration.model.RunStatus;

/**
 * POST /runs / cancel 等返回的 run 概要。见详细设计 §2.1、§2.2。
 */
public record RunResponse(
        String runId,
        RunStatus status
) {
}
