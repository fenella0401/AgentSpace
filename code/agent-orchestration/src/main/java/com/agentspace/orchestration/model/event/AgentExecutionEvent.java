package com.agentspace.orchestration.model.event;

import java.time.Instant;
import java.util.Map;

/**
 * Agent Core 上报的统一执行事件 envelope。见概要设计 §8.3。
 *
 * <p>{@code payload} 为弱类型 map，具体结构按 {@code eventType} 对应 {@code *Payload} 解释。
 */
public record AgentExecutionEvent(
        String eventId,
        String eventType,
        String runId,
        String stepId,
        String stepKey,
        String attemptId,
        int attemptNo,
        Instant timestamp,
        long sequence,
        EventSource source,
        Map<String, Object> payload
) {
}
