package com.agentspace.orchestration.model.claudecode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Claude Code SDK {@code stream-json} 输出的一行消息（envelope）。
 *
 * <p>SDK 以 newline-delimited JSON 输出，每行是一个本记录的实例。{@code type} 区分种类：
 * <ul>
 *   <li>{@code system} + {@code subtype=init}：会话初始化，携带 session_id / model / tools；</li>
 *   <li>{@code assistant}：包裹一条 Anthropic message，content 含 text / thinking / tool_use 块；</li>
 *   <li>{@code user}：工具结果回传，content 含 tool_result 块；</li>
 *   <li>{@code result}：本次执行终态，含 subtype（success / error_*）、is_error、result、session_id。</li>
 * </ul>
 *
 * <p>未知字段忽略，向前兼容 SDK 版本演进。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeCodeMessage(
        String type,
        String subtype,
        @JsonProperty("session_id") String sessionId,
        String model,
        ClaudeCodeInnerMessage message,
        @JsonProperty("parent_tool_use_id") String parentToolUseId,
        @JsonProperty("is_error") Boolean isError,
        String result,
        @JsonProperty("num_turns") Integer numTurns,
        @JsonProperty("duration_ms") Long durationMs,
        @JsonProperty("total_cost_usd") Double totalCostUsd
) {

    public static final String TYPE_SYSTEM = "system";
    public static final String TYPE_ASSISTANT = "assistant";
    public static final String TYPE_USER = "user";
    public static final String TYPE_RESULT = "result";
    public static final String TYPE_STREAM_EVENT = "stream_event";

    public static final String SUBTYPE_INIT = "init";
    public static final String SUBTYPE_SUCCESS = "success";
}
