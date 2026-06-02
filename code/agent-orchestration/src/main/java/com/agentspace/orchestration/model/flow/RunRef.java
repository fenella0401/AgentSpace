package com.agentspace.orchestration.model.flow;

import jakarta.validation.constraints.NotBlank;

/**
 * run 标识与幂等键。见概要设计 §6。
 */
public record RunRef(
        @NotBlank String runId,
        @NotBlank String idempotencyKey
) {
}
