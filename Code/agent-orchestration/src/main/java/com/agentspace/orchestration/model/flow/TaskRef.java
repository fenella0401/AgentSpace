package com.agentspace.orchestration.model.flow;

/**
 * 任务 / 项目上下文。见概要设计 §6。
 */
public record TaskRef(
        String taskId,
        String projectId
) {
}
