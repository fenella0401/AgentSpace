package com.agentspace.orchestration.controller.dto;

import com.agentspace.orchestration.model.flow.AgentFlow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * POST /runs 请求体。见详细设计 §2.1。
 */
public record CreateRunRequest(
        @NotNull @Valid AgentFlow agentFlow
) {
}
