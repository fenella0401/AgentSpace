package com.agentspace.orchestration.model.flow;

import jakarta.validation.constraints.NotBlank;

/**
 * AgentFlow DAG 边：from step → to step。见概要设计 §6。
 */
public record AgentFlowEdge(
        @NotBlank String from,
        @NotBlank String to
) {
}
