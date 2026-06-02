package com.agentspace.orchestration.service;

import java.util.List;

/**
 * AgentFlow schema / DAG 校验失败。对应 {@code POST /runs} 的 422 VALIDATION_ERROR。见详细设计 §2.1。
 */
public class AgentFlowValidationException extends RuntimeException {

    private final List<String> violations;

    public AgentFlowValidationException(List<String> violations) {
        super("AgentFlow 校验失败: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
