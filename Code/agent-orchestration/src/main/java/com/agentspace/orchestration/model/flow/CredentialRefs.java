package com.agentspace.orchestration.model.flow;

import java.util.List;

/**
 * 凭证引用集合（仅引用，不含明文）。见概要设计 §6。
 */
public record CredentialRefs(
        String gitCredentialRef,
        String llmCredentialRef,
        List<String> mcpCredentialRefs
) {
}
