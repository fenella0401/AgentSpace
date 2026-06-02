package com.agentspace.orchestration.model.flow;

import jakarta.validation.constraints.NotBlank;

/**
 * 租户授权范围（团队 / 用户）。见概要设计 §6。鉴权 MVP 暂缓，字段保留备用。
 */
public record TenantRef(
        @NotBlank String teamId,
        @NotBlank String userId
) {
}
