package com.agentspace.orchestration.model.event;

/**
 * agent.thinking 事件载荷。只传思考摘要，不传原始 chain-of-thought。见概要设计 §8.4。
 */
public record AgentThinkingPayload(
        String summary
) {
}
