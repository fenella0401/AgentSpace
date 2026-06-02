package com.agentspace.orchestration.model.flow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * AgentFlow 内的一个逻辑步骤。见概要设计 §6。
 */
public record AgentFlowStep(
        @NotBlank String id,
        String name,
        @NotNull @Valid AgentSpec agent,
        @NotNull @Valid PromptSpec prompt,
        boolean requiresConfirmation
) {
}
