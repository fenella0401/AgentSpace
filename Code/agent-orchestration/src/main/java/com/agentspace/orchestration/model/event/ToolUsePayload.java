package com.agentspace.orchestration.model.event;

import java.util.Map;

/**
 * agent.tool_use 事件载荷。input 必须脱敏，不含 credential / token / secret。见概要设计 §8.4。
 */
public record ToolUsePayload(
        String toolCallId,
        String toolName,
        Map<String, Object> input
) {
}
