package com.agentspace.orchestration.controller.dto;

import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;

/**
 * confirm 响应。见详细设计 §2.3。
 */
public record ConfirmResponse(
        String runId,
        String stepId,
        StepStatus stepStatus,
        RunStatus runStatus
) {
}
