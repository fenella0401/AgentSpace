package com.agentspace.orchestration.model.claudecode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Claude Code SDK message 内的一个 content 块。{@code type} 决定有效字段：
 * <ul>
 *   <li>{@code text}：{@code text} 为助手输出文本；</li>
 *   <li>{@code thinking}：{@code thinking} 为思考内容（仅取摘要，不透传原始 chain-of-thought）；</li>
 *   <li>{@code tool_use}：{@code id} / {@code name} / {@code input} 为工具调用；</li>
 *   <li>{@code tool_result}：{@code toolUseId} 关联调用，{@code content} 为结果（字符串或块数组），
 *       {@code isError} 标识失败。</li>
 * </ul>
 *
 * <p>{@code content} 用 {@link JsonNode} 承载，因为 SDK 中 tool_result 的内容既可能是纯字符串，
 * 也可能是 {@code [{type:text,text:...}]} 形式的块数组，交由 adapter 归一为文本。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeCodeContentBlock(
        String type,
        String text,
        String thinking,
        String id,
        String name,
        Map<String, Object> input,
        @JsonProperty("tool_use_id") String toolUseId,
        JsonNode content,
        @JsonProperty("is_error") Boolean isError
) {

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_THINKING = "thinking";
    public static final String TYPE_TOOL_USE = "tool_use";
    public static final String TYPE_TOOL_RESULT = "tool_result";
}
