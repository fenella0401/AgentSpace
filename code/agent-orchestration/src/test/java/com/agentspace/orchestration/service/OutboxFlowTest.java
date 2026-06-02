package com.agentspace.orchestration.service;

import com.agentspace.orchestration.client.mock.MockAgentManagementClient;
import com.agentspace.orchestration.model.entity.OutboxMessage;
import com.agentspace.orchestration.repository.OutboxMessageRepository;
import com.agentspace.orchestration.scheduler.OutboxWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FE5 状态回流与背压：outbox 写入、worker 投递成功/失败重试、背压降采样。见详细设计 §2.9、§9.8。
 */
@SpringBootTest
@ActiveProfiles({"test", "mock"})
class OutboxFlowTest {

    @Autowired
    OutboxService outboxService;
    @Autowired
    OutboxWorker outboxWorker;
    @Autowired
    OutboxMessageRepository outboxRepo;
    @Autowired
    MockAgentManagementClient amClient;

    @BeforeEach
    void clean() {
        outboxRepo.deleteAll();
        amClient.clear();
    }

    @Test
    void stateChangeEnqueuesAndWorkerDelivers() {
        outboxService.enqueueStateChange("run-1", null, null, "run.completed",
                Map.of("runId", "run-1", "status", "COMPLETED"));
        assertThat(outboxRepo.countByStatus("PENDING")).isEqualTo(1);

        outboxWorker.dispatchPending();

        assertThat(outboxRepo.countByStatus("SENT")).isEqualTo(1);
        assertThat(amClient.received()).hasSize(1);
        assertThat(amClient.received().get(0).type()).isEqualTo("run.completed");
        assertThat(amClient.received().get(0).outboxId()).isNotBlank();
    }

    @Test
    void deliveryFailureSchedulesRetryNotLost() {
        amClient.setFailNext(true);
        outboxService.enqueueStateChange("run-2", null, null, "run.failed",
                Map.of("runId", "run-2"));
        String id = outboxRepo.findByStatus("PENDING").get(0).getId();

        outboxWorker.dispatchOne(id);

        OutboxMessage msg = outboxRepo.findById(id).orElseThrow();
        // 失败后仍 PENDING（未丢），retry_count 增加，next_retry_at 推后
        assertThat(msg.getStatus()).isEqualTo("PENDING");
        assertThat(msg.getRetryCount()).isEqualTo(1);

        // 恢复后再投递成功
        amClient.setFailNext(false);
        outboxWorker.dispatchOne(id);
        assertThat(outboxRepo.findById(id).orElseThrow().getStatus()).isEqualTo("SENT");
        assertThat(amClient.received()).hasSize(1);
    }

    @Test
    void displayBackpressureDropsWhenPendingExceedsThreshold() {
        // test profile max-pending=5：先塞满 5 条 PENDING
        for (int i = 0; i < 5; i++) {
            outboxService.enqueueStateChange("run-bp", null, null, "run.x", Map.of("i", i));
        }
        assertThat(outboxRepo.countByStatus("PENDING")).isEqualTo(5);

        // 展示事件应被降采样丢弃（返回 false，不入队）
        boolean enqueued = outboxService.enqueueDisplay("run-bp", "step-1", "att-1", Map.of("e", "x"));
        assertThat(enqueued).isFalse();
        assertThat(outboxRepo.countByStatus("PENDING")).isEqualTo(5);
    }

    @Test
    void displayEnqueuedWhenUnderThreshold() {
        boolean enqueued = outboxService.enqueueDisplay("run-ok", "step-1", "att-1", Map.of("e", "x"));
        assertThat(enqueued).isTrue();
        assertThat(outboxRepo.countByStatus("PENDING")).isEqualTo(1);
    }
}
