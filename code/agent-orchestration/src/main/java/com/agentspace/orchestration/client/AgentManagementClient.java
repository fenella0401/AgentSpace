package com.agentspace.orchestration.client;

import com.agentspace.orchestration.client.dto.OutboundEvent;

/**
 * 回流 Agent-Management 的出站客户端。见详细设计 §2.9、§9.8。
 *
 * <p>由 outbox worker 调用，至少一次投递；Agent-Management 按 outboxId 去重。
 * 投递失败应抛异常，由 worker 走指数退避重试。
 */
public interface AgentManagementClient {

    /**
     * 投递一个出站事件给 Agent-Management。失败抛异常（worker 据此重试）。
     */
    void sendEvent(OutboundEvent event);
}
