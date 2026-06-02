package com.agentspace.orchestration.client.mock;

import com.agentspace.orchestration.client.AgentManagementClient;
import com.agentspace.orchestration.client.dto.OutboundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * mock Agent-Management 出站客户端（{@code mock} profile）。默认成功并记录收到的事件，
 * 供联调与测试断言；可切换为失败以验证 outbox 重试。
 */
@Component
@Profile("mock")
public class MockAgentManagementClient implements AgentManagementClient {

    private static final Logger log = LoggerFactory.getLogger(MockAgentManagementClient.class);

    private final List<OutboundEvent> received = new CopyOnWriteArrayList<>();
    private final AtomicBoolean failNext = new AtomicBoolean(false);

    @Override
    public void sendEvent(OutboundEvent event) {
        if (failNext.get()) {
            throw new IllegalStateException("[mock-am] 模拟投递失败");
        }
        received.add(event);
        log.info("[mock-am] 收到出站事件 outboxId={} type={} run={}",
                event.outboxId(), event.type(), event.runId());
    }

    /** 测试用：设置后续投递是否失败。 */
    public void setFailNext(boolean fail) {
        failNext.set(fail);
    }

    /** 测试用：已收到的出站事件。 */
    public List<OutboundEvent> received() {
        return received;
    }

    public void clear() {
        received.clear();
        failNext.set(false);
    }
}
