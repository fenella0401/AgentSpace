package com.agentspace.orchestration.model.event;

/**
 * agent.tool_result 事件载荷。output 必须脱敏并限制大小，超限写 artifact。见概要设计 §8.4。
 */
public record ToolResultPayload(
        String toolCallId,
        String toolName,
        ToolResultStatus status,
        String output,
        String errorMessage
) {
}
