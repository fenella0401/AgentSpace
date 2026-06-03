package com.agentspace.orchestration.client.claudecode;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Claude Code 消息适配配置（前缀 {@code claude-code.adapter}）。见概要设计 §8.4 约束。
 *
 * @param thinkingSummaryMaxChars agent.thinking 只传摘要：thinking 文本超过此长度即截断，
 *                                避免透传完整 chain-of-thought
 * @param toolOutputMaxChars      agent.tool_result.output 单条大小上限，超限截断（超大内容应改写 artifact）
 * @param synthesizeRuntimeTerminal 是否由 SDK 的 {@code result} 行合成 runtime.completed/failed。
 *                                  Claude Code 直连作执行器+运行时时为 true；若上层另有运行时层
 *                                  自行上报 runtime.* 则设 false，避免重复
 */
@ConfigurationProperties(prefix = "claude-code.adapter")
public record ClaudeCodeAdapterProperties(
        int thinkingSummaryMaxChars,
        int toolOutputMaxChars,
        boolean synthesizeRuntimeTerminal
) {

    public ClaudeCodeAdapterProperties {
        if (thinkingSummaryMaxChars <= 0) {
            thinkingSummaryMaxChars = 2000;
        }
        if (toolOutputMaxChars <= 0) {
            toolOutputMaxChars = 8000;
        }
    }

    /** 提供合理默认，便于非 Spring 上下文（如单测）直接构造。 */
    public static ClaudeCodeAdapterProperties defaults() {
        return new ClaudeCodeAdapterProperties(2000, 8000, true);
    }
}
