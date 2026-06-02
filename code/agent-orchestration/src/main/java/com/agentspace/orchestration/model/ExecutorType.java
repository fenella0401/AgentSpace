package com.agentspace.orchestration.model;

/**
 * Agent 执行器类型，决定 Agent Core 使用哪类执行后端。见概要设计 §6。
 */
public enum ExecutorType {
    CLAUDE_CODE,
    OPENCODE,
    CODEX
}
