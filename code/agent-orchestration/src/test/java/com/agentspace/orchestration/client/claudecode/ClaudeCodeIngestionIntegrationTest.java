package com.agentspace.orchestration.client.claudecode;

import com.agentspace.orchestration.event.EventSink;
import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.AttemptTrigger;
import com.agentspace.orchestration.model.ExecutorType;
import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.repository.StepAttemptRepository;
import com.agentspace.orchestration.repository.WorkflowEventRepository;
import com.agentspace.orchestration.repository.WorkflowRunRepository;
import com.agentspace.orchestration.repository.WorkflowStepRepository;
import com.agentspace.orchestration.service.AgentFlowCodec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.StringReader;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端：Claude Code SDK stream-json → 解析 → IngestingEventSink → EventIngestService → 状态机推进。
 * 证明适配层真正驱动编排：一段成功转写让 step 走到 COMPLETED、attempt 到 SUCCEEDED 并落 sessionRef。
 *
 * <p>用 STARTING 态的真实 attempt 作起点（绕过 mock 自动事件），喂入 SDK 转写，断言终态。
 */
@SpringBootTest
@ActiveProfiles({"test", "mock"})
class ClaudeCodeIngestionIntegrationTest {

    @Autowired
    WorkflowRunRepository runRepo;
    @Autowired
    WorkflowStepRepository stepRepo;
    @Autowired
    StepAttemptRepository attemptRepo;
    @Autowired
    WorkflowEventRepository eventRepo;
    @Autowired
    AgentFlowCodec codec;
    @Autowired
    ClaudeCodeStreamParser parser;
    @Autowired
    EventSink eventSink;

    private String flowJson(String runId) {
        var step = new com.agentspace.orchestration.model.flow.AgentFlowStep("a", "a",
                new com.agentspace.orchestration.model.flow.AgentSpec(
                        ExecutorType.CLAUDE_CODE, "agent-ref", java.util.List.of(), java.util.List.of()),
                new com.agentspace.orchestration.model.flow.PromptSpec("do a", java.util.Map.of()), false);
        var flow = new com.agentspace.orchestration.model.flow.AgentFlow("1", "f", "f", "s",
                new com.agentspace.orchestration.model.flow.RunRef(runId, "idem-" + runId),
                new com.agentspace.orchestration.model.flow.TenantRef("t", "u"),
                new com.agentspace.orchestration.model.flow.TaskRef("tk", "p"),
                null, null, null, java.util.Map.of(),
                java.util.List.of(step), java.util.List.of());
        return codec.toJson(flow);
    }

    private StepAttempt seedStartingAttempt(String runId) {
        OffsetDateTime now = OffsetDateTime.now();
        WorkflowRun run = new WorkflowRun();
        run.setId(runId);
        run.setIdempotencyKey("idem-" + runId);
        run.setStatus(RunStatus.RUNNING);
        run.setFlowId("f");
        run.setFlowSnapshotId("s");
        run.setSchemaVersion("1");
        run.setTeamId("t");
        run.setUserId("u");
        run.setTaskId("tk");
        run.setProjectId("p");
        run.setAgentFlowJson(flowJson(runId));
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runRepo.save(run);

        WorkflowStep step = new WorkflowStep();
        String stepId = "step-" + UUID.randomUUID();
        step.setId(stepId);
        step.setRunId(runId);
        step.setStepKey("a");
        step.setStatus(StepStatus.RUNNING);
        step.setOrderIndex(0);
        step.setExecutorType(ExecutorType.CLAUDE_CODE);
        step.setRetryCount(0);
        step.setCreatedAt(now);
        step.setUpdatedAt(now);
        stepRepo.save(step);

        StepAttempt attempt = new StepAttempt();
        attempt.setId("att-" + UUID.randomUUID());
        attempt.setRunId(runId);
        attempt.setStepId(stepId);
        attempt.setAttemptNo(1);
        attempt.setStatus(AttemptStatus.STARTING);
        attempt.setTrigger(AttemptTrigger.INITIAL);
        attempt.setCreatedAt(now);
        attempt.setUpdatedAt(now);
        return attemptRepo.save(attempt);
    }

    private String successTranscript() {
        return String.join("\n",
                "{\"type\":\"system\",\"subtype\":\"init\",\"session_id\":\"sess-e2e\",\"model\":\"claude-opus-4-8\"}",
                "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_1\",\"role\":\"assistant\",\"content\":["
                        + "{\"type\":\"text\",\"text\":\"working\"}]}}",
                "{\"type\":\"result\",\"subtype\":\"success\",\"is_error\":false,\"result\":\"all done\",\"session_id\":\"sess-e2e\"}");
    }

    @Test
    void successTranscriptDrivesStepToCompleted() {
        StepAttempt attempt = seedStartingAttempt("run-cc-e2e-ok");
        AttemptContext ctx = new AttemptContext(
                attempt.getRunId(), attempt.getStepId(), "a", attempt.getId(), 1);

        int dispatched = parser.parseAndDispatch(new StringReader(successTranscript()), ctx);
        assertThat(dispatched).isGreaterThan(0);

        StepAttempt after = attemptRepo.findById(attempt.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(AttemptStatus.SUCCEEDED);

        WorkflowStep step = stepRepo.findById(attempt.getStepId()).orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.COMPLETED);
        assertThat(step.getOutputResult()).isEqualTo("all done");
        assertThat(step.getSessionRef()).isEqualTo("sess-e2e");

        // 展示类事件（agent.message）已留存
        assertThat(eventRepo.findByRunIdOrderBySequenceNoAsc(attempt.getRunId()))
                .anyMatch(e -> e.getEventType().equals(
                        com.agentspace.orchestration.model.event.EventTypes.AGENT_MESSAGE));
    }

    @Test
    void rereadingTranscriptIsIdempotent() {
        StepAttempt attempt = seedStartingAttempt("run-cc-e2e-dup");
        AttemptContext ctx = new AttemptContext(
                attempt.getRunId(), attempt.getStepId(), "a", attempt.getId(), 1);

        parser.parseAndDispatch(new StringReader(successTranscript()), ctx);
        long afterFirst = eventRepo.findByRunIdOrderBySequenceNoAsc(attempt.getRunId()).size();
        // 断线重连重读同一段流：确定性 eventId 去重，不重复落库
        parser.parseAndDispatch(new StringReader(successTranscript()), ctx);
        long afterSecond = eventRepo.findByRunIdOrderBySequenceNoAsc(attempt.getRunId()).size();

        assertThat(afterSecond).isEqualTo(afterFirst);
    }
}
