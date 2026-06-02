package com.agentspace.orchestration.model.flow;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Prompt 模板与局部变量。渲染由 Agent-Orchestration 完成，Agent Core 不二次渲染。见概要设计 §6、§7.1。
 */
public record PromptSpec(
        @NotNull String template,
        Map<String, Object> variables
) {
}
