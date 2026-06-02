package com.agentspace.orchestration.controller.dto;

import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /runs/{runId} 响应：run + step 状态快照。见详细设计 §2.6。
 */
public record RunDetailResponse(
        RunView run,
        List<StepView> steps
) {

    public record RunView(
            String runId,
            RunStatus status,
            OffsetDateTime startedAt,
            String errorCode
    ) {
    }

    public record StepView(
            String stepId,
            String stepKey,
            StepStatus status,
            String outputSummary,
            Integer currentAttemptNo,
            OffsetDateTime lastHeartbeatAt
    ) {
    }
}
