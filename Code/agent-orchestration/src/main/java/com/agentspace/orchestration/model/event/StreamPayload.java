package com.agentspace.orchestration.model.event;

/**
 * executor.stdout / executor.stderr 事件载荷。单条需限制大小。见概要设计 §8.4。
 */
public record StreamPayload(
        String text
) {
}
