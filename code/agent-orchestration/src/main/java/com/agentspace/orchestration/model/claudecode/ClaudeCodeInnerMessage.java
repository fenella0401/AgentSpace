package com.agentspace.orchestration.model.claudecode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Claude Code SDK 中 {@code assistant} / {@code user} 消息内嵌的 Anthropic message 体。
 *
 * <p>对应 SDK 输出里 {@code message: {...}} 字段：assistant 消息的 {@code content} 是
 * text / thinking / tool_use 块的数组；user 消息的 {@code content} 是 tool_result 块的数组。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeCodeInnerMessage(
        String id,
        String role,
        String model,
        @JsonProperty("stop_reason") String stopReason,
        List<ClaudeCodeContentBlock> content
) {
}
