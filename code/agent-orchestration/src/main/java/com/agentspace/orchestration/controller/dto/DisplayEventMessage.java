package com.agentspace.orchestration.controller.dto;

/**
 * 实时事件流推送体。见详细设计 §2.7。
 */
public record DisplayEventMessage(
        String eventId,
        String eventType,
        String category,
        Long sequenceNo,
        String payload
) {
}
