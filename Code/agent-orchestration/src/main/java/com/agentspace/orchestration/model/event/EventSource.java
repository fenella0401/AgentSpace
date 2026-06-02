package com.agentspace.orchestration.model.event;

/**
 * AgentExecutionEvent 来源。见概要设计 §8.3。
 */
public enum EventSource {
    AGENT_CORE,
    EXECUTOR,
    RUNTIME
}
