package com.agentspace.orchestration.client.mock;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * mock Agent Core 行为配置（{@code mock} profile，前缀 {@code mock.agent-core}）。供 FE2+ 联调。
 *
 * @param behavior       默认行为：SUCCEED / FAIL / TIMEOUT
 * @param startDelayMs   StartAttempt 返回前的模拟延迟
 * @param emitDisplayEvents 是否在执行过程中推送展示类事件（thinking/message/...）
 * @param eventCount     SUCCEED 时推送的展示类事件条数
 * @param failureReason  FAIL 时回报的 failureReason
 */
@ConfigurationProperties(prefix = "mock.agent-core")
public record MockAgentCoreProperties(
        Behavior behavior,
        long startDelayMs,
        boolean emitDisplayEvents,
        int eventCount,
        String failureReason
) {

    public MockAgentCoreProperties {
        if (behavior == null) {
            behavior = Behavior.SUCCEED;
        }
        if (eventCount <= 0) {
            eventCount = 3;
        }
        if (failureReason == null || failureReason.isBlank()) {
            failureReason = "EXECUTOR_FAILED";
        }
    }

    public enum Behavior {
        SUCCEED,
        FAIL,
        TIMEOUT
    }
}
