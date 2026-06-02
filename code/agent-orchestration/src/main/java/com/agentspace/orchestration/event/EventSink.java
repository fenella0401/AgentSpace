package com.agentspace.orchestration.event;

import com.agentspace.orchestration.model.event.AgentExecutionEvent;

/**
 * 执行事件接收下游的抽象。Agent Core（或其 mock）通过此接口把 {@link AgentExecutionEvent}
 * 投递给 Agent-Orchestration。
 *
 * <p>解耦的目的：mock Agent Core 在 FE3 的 {@code POST /internal/agent-core/events} 端点
 * 落地之前即可工作——默认实现先记录日志；FE3 后切换到经该端点的真实投递。
 */
public interface EventSink {

    /**
     * 接收一个执行事件。实现须按 {@code eventId} 去重、校验归属（FE3 落地）。
     */
    void accept(AgentExecutionEvent event);
}
