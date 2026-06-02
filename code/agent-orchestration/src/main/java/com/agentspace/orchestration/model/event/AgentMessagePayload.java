package com.agentspace.orchestration.model.event;

/**
 * agent.message 事件载荷。见概要设计 §8.4。
 */
public record AgentMessagePayload(
        MessageRole role,
        String text
) {
}
