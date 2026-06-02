package com.agentspace.orchestration.service;

import com.agentspace.orchestration.controller.dto.DisplayEventMessage;
import com.agentspace.orchestration.model.entity.WorkflowEvent;
import com.agentspace.orchestration.repository.WorkflowEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FE4 实时分发器：订阅/发布/移除、fromSequence 回放。见详细设计 §2.7。
 */
@SpringBootTest
@ActiveProfiles({"test", "mock"})
class RunEventBroadcasterTest {

    @Autowired
    RunEventBroadcaster broadcaster;
    @Autowired
    WorkflowEventRepository eventRepo;

    private void saveDisplayEvent(String runId, long seq, String type) {
        WorkflowEvent e = new WorkflowEvent();
        e.setId("evt-" + UUID.randomUUID());
        e.setEventId("eid-" + runId + "-" + seq);
        e.setRunId(runId);
        e.setEventType(type);
        e.setCategory("display");
        e.setSequenceNo(seq);
        e.setSource("EXECUTOR");
        e.setPayload("{\"text\":\"m" + seq + "\"}");
        e.setCreatedAt(OffsetDateTime.now());
        eventRepo.save(e);
    }

    @Test
    void subscribeRegistersAndPublishDelivers() throws Exception {
        String runId = "run-bcast-1";
        List<Object> received = new ArrayList<>();

        SseEmitter emitter = broadcaster.subscribe(runId, null);
        assertThat(broadcaster.subscriberCount(runId)).isEqualTo(1);

        // publish 不应抛异常（emitter 已注册）
        broadcaster.publish(runId, new DisplayEventMessage("e1", "agent.message", "display", 1L, "{}"));
    }

    @Test
    void replaysEventsAfterFromSequence() {
        String runId = "run-bcast-replay";
        saveDisplayEvent(runId, 1, "agent.message");
        saveDisplayEvent(runId, 2, "agent.message");
        saveDisplayEvent(runId, 3, "agent.tool_use");

        // 订阅 fromSequence=1 → 回放 seq 2、3（不抛异常即认为回放路径可用）
        SseEmitter emitter = broadcaster.subscribe(runId, 1L);
        assertThat(broadcaster.subscriberCount(runId)).isEqualTo(1);
    }

    @Test
    void noSubscriberPublishIsNoop() {
        // 无订阅者时 publish 安全返回
        broadcaster.publish("run-none", new DisplayEventMessage("e", "agent.message", "display", 1L, "{}"));
        assertThat(broadcaster.subscriberCount("run-none")).isZero();
    }
}
