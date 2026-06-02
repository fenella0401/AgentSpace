package com.agentspace.orchestration.client.dto;

/**
 * 回流 Agent-Management 的出站事件体。见详细设计 §2.9、§9.8。
 *
 * @param outboxId 幂等键，Agent-Management 按此去重
 * @param type     事件类型，如 run.completed / run.failed / step.suspended / display
 */
public record OutboundEvent(
        String outboxId,
        String type,
        String runId,
        String stepId,
        String attemptId,
        String payload
) {
}
