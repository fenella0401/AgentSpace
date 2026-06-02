package com.agentspace.orchestration.service;

/**
 * 非法状态转换。接口层映射为 409；内部事件触发时记为告警并丢弃。见详细设计 §3。
 */
public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(String message) {
        super(message);
    }
}
