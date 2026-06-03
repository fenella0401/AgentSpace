package com.agentspace.orchestration.service;

import com.agentspace.orchestration.controller.dto.EventPollResponse;
import com.agentspace.orchestration.model.ExecutorType;
import com.agentspace.orchestration.model.entity.WorkflowEvent;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.flow.AgentFlow;
import com.agentspace.orchestration.model.flow.AgentFlowStep;
import com.agentspace.orchestration.model.flow.AgentSpec;
import com.agentspace.orchestration.model.flow.PromptSpec;
import com.agentspace.orchestration.model.flow.RunRef;
import com.agentspace.orchestration.model.flow.TaskRef;
import com.agentspace.orchestration.model.flow.TenantRef;
import com.agentspace.orchestration.repository.WorkflowEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 展示类事件轮询（SSE 之外的轮询方案，见详细设计 §2.7）：游标推进、limit 截断 hasMore、空批游标不退、终态标记。
 *
 * <p>用 {@code behavior=TIMEOUT} 让 mock 不自动推送展示事件，从而手工 seed 可控序号、隔离断言。
 */
@SpringBootTest
@ActiveProfiles({"test", "mock"})
@TestPropertySource(properties = "mock.agent-core.behavior=TIMEOUT")
class EventPollServiceTest {

    @Autowired
    RunService runService;
    @Autowired
    WorkflowEventRepository eventRepo;

    private String startSingleStepRun(String idemKey) {
        AgentFlowStep step = new AgentFlowStep("a", "a",
                new AgentSpec(ExecutorType.CLAUDE_CODE, "agent-ref", List.of(), List.of()),
                new PromptSpec("do a", Map.of()), true);
        AgentFlow flow = new AgentFlow("1", "flow-1", "单步", "snap-" + idemKey,
                new RunRef("run-" + idemKey, idemKey),
                new TenantRef("team-1", "user-1"),
                new TaskRef("task-1", "proj-1"),
                null, null, null, Map.of(),
                List.of(step), List.of());
        return runService.startRun(flow).getId();
    }

    private void seedDisplayEvent(String runId, long seq) {
        WorkflowEvent e = new WorkflowEvent();
        e.setId("evt-" + UUID.randomUUID());
        e.setEventId("eid-" + runId + "-" + seq);
        e.setRunId(runId);
        e.setEventType("agent.message");
        e.setCategory("display");
        e.setSequenceNo(seq);
        e.setSource("EXECUTOR");
        e.setPayload("{\"text\":\"m" + seq + "\"}");
        e.setCreatedAt(OffsetDateTime.now());
        eventRepo.save(e);
    }

    @Test
    void pollAdvancesCursorAndFlagsHasMore() {
        String runId = startSingleStepRun("idem-poll-cursor");
        for (long s = 1; s <= 5; s++) {
            seedDisplayEvent(runId, s);
        }
        WorkflowRun run = runService.findRun(runId).orElseThrow();

        // 第一批 limit=2：seq 1、2，hasMore=true，游标推进到 2
        EventPollResponse batch1 = runService.pollDisplayEvents(run, 0L, 2);
        assertThat(batch1.events()).hasSize(2);
        assertThat(batch1.nextSequence()).isEqualTo(2);
        assertThat(batch1.hasMore()).isTrue();

        // 用上批游标续拉：seq 3、4
        EventPollResponse batch2 = runService.pollDisplayEvents(run, batch1.nextSequence(), 2);
        assertThat(batch2.events()).hasSize(2);
        assertThat(batch2.nextSequence()).isEqualTo(4);
        assertThat(batch2.hasMore()).isTrue();

        // 末批：只剩 seq 5，hasMore=false
        EventPollResponse batch3 = runService.pollDisplayEvents(run, batch2.nextSequence(), 2);
        assertThat(batch3.events()).hasSize(1);
        assertThat(batch3.nextSequence()).isEqualTo(5);
        assertThat(batch3.hasMore()).isFalse();

        // 再拉无新事件：空批，游标不退
        EventPollResponse batch4 = runService.pollDisplayEvents(run, batch3.nextSequence(), 2);
        assertThat(batch4.events()).isEmpty();
        assertThat(batch4.nextSequence()).isEqualTo(5);
        assertThat(batch4.hasMore()).isFalse();
    }

    @Test
    void pollFromNullSequenceStartsFromZero() {
        String runId = startSingleStepRun("idem-poll-null");
        seedDisplayEvent(runId, 1);
        seedDisplayEvent(runId, 2);
        WorkflowRun run = runService.findRun(runId).orElseThrow();

        EventPollResponse batch = runService.pollDisplayEvents(run, null, 100);
        assertThat(batch.events()).hasSize(2);
        assertThat(batch.nextSequence()).isEqualTo(2);
    }

    @Test
    void pollExposesRunStatusButNotTerminalWhileRunning() {
        String runId = startSingleStepRun("idem-poll-status");
        WorkflowRun run = runService.findRun(runId).orElseThrow();

        EventPollResponse batch = runService.pollDisplayEvents(run, 0L, 100);
        // TIMEOUT 行为下 run 仍在 RUNNING，未终态 → 前端应继续轮询
        assertThat(batch.runTerminal()).isFalse();
        assertThat(batch.runStatus()).isNotNull();
    }
}
