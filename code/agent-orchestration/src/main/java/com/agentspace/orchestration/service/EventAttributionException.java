package com.agentspace.orchestration.service;

/**
 * 入站事件归属校验失败（run/step/attempt 不匹配）。对应 POST /internal/agent-core/events 的 422。
 * 见详细设计 §2.8。
 */
public class EventAttributionException extends RuntimeException {

    public EventAttributionException(String message) {
        super(message);
    }
}
