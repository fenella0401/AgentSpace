package com.agentspace.orchestration.controller.dto;

import com.agentspace.orchestration.model.StepStatus;

/**
 * continue / retry 响应。见详细设计 §2.4、§2.5。
 */
public record StepAttemptResponse(
        String runId,
        String stepId,
        int attemptNo,
        StepStatus stepStatus
) {
}
