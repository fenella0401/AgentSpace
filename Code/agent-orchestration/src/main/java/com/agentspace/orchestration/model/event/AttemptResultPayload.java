package com.agentspace.orchestration.model.event;

import java.util.List;

/**
 * attempt.result 事件载荷：executor 的语义结果。见概要设计 §8.4。
 *
 * <p>{@code sessionRef} 为对话上下文标识，供后续续聊作为 resumeFromSessionRef。
 */
public record AttemptResultPayload(
        AttemptResultStatus status,
        String summary,
        String result,
        List<String> artifactRefs,
        String sessionRef,
        String errorCode,
        String errorMessage
) {
}
