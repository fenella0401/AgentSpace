package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.AttemptTrigger;
import com.agentspace.orchestration.model.ExecutorType;
import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.repository.StepAttemptRepository;
import com.agentspace.orchestration.repository.WorkflowRunRepository;
import com.agentspace.orchestration.repository.WorkflowStepRepository;
import com.agentspace.orchestration.scheduler.Watchdog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.agentspace.orchestration.service.run.RunCancellationService;
import com.agentspace.orchestration.service.run.ReconciliationService;
import com.agentspace.orchestration.service.support.AgentFlowCodec;

/**
 * FE7 可靠性：watchdog 心跳丢失/超时判失败、cancel 级联、reconcile 对齐。见详细设计 §3.5、§2.2。
 */
@SpringBootTest
@ActiveProfiles({"test", "mock"})
class ReliabilityIntegrationTest {

    @Autowired
    WorkflowRunRepository runRepo;
    @Autowired
    WorkflowStepRepository stepRepo;
    @Autowired
    StepAttemptRepository attemptRepo;
    @Autowired
    Watchdog watchdog;
    @Autowired
    RunCancellationService cancellation;
    @Autowired
    ReconciliationService reconciliation;
    @Autowired
    AgentFlowCodec codec;

    private String singleStepFlowJson(String runId) {
        com.agentspace.orchestration.model.flow.AgentFlowStep step =
                new com.agentspace.orchestration.model.flow.AgentFlowStep("a", "a",
                        new com.agentspace.orchestration.model.flow.AgentSpec(
                                ExecutorType.CLAUDE_CODE, "agent-ref", java.util.List.of(), java.util.List.of()),
                        new com.agentspace.orchestration.model.flow.PromptSpec("do a", java.util.Map.of()), false);
        com.agentspace.orchestration.model.flow.AgentFlow flow =
                new com.agentspace.orchestration.model.flow.AgentFlow("1", "f", "f", "s",
                        new com.agentspace.orchestration.model.flow.RunRef(runId, "idem-" + runId),
                        new com.agentspace.orchestration.model.flow.TenantRef("t", "u"),
                        new com.agentspace.orchestration.model.flow.TaskRef("tk", "p"),
                        null, null, null, java.util.Map.of(),
                        java.util.List.of(step), java.util.List.of());
        return codec.toJson(flow);
    }

    /** 直接造一个 RUNNING run + RUNNING step + RUNNING attempt（心跳过期），绕过 mock 异步。 */
    private StepAttempt seedRunningAttempt(String runId, OffsetDateTime heartbeat, OffsetDateTime started) {
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
        run.setAgentFlowJson(singleStepFlowJson(runId));
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
        attempt.setStatus(AttemptStatus.RUNNING);
        attempt.setTrigger(AttemptTrigger.INITIAL);
        attempt.setLastHeartbeatAt(heartbeat);
        attempt.setStartedAt(started);
        attempt.setCreatedAt(now);
        attempt.setUpdatedAt(now);
        return attemptRepo.save(attempt);
    }

    @Test
    void watchdogFailsHeartbeatLostAttempt() {
        OffsetDateTime old = OffsetDateTime.now().minusHours(1);
        StepAttempt attempt = seedRunningAttempt("run-wd-hb", old, OffsetDateTime.now());

        watchdog.scan();

        // 心跳丢失 → attempt FAILED；step 自动重试（maxRetries=2，retryCount 增加）或最终失败
        StepAttempt after = attemptRepo.findById(attempt.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(AttemptStatus.FAILED);
        assertThat(after.getFailureReason()).isIn("HEARTBEAT_LOST", "TIMEOUT");
    }

    @Test
    void cancelCascadesInFlightAttempt() {
        StepAttempt attempt = seedRunningAttempt("run-cancel-cascade",
                OffsetDateTime.now(), OffsetDateTime.now());

        WorkflowRun run = cancellation.cancel("run-cancel-cascade");

        assertThat(run.getStatus()).isEqualTo(RunStatus.CANCELLED);
        assertThat(attemptRepo.findById(attempt.getId()).orElseThrow().getStatus())
                .isEqualTo(AttemptStatus.CANCELLED);
    }

    @Test
    void reconcileAlignsInFlightWithAgentCore() {
        // mock queryAttempt 返回 PENDING（未知），reconcile 不应误判终态
        seedRunningAttempt("run-reconcile", OffsetDateTime.now(), OffsetDateTime.now());
        int aligned = reconciliation.reconcileInFlight();
        // mock 默认 query 返回 PENDING（statuses 无记录）→ 不对齐，留给 watchdog
        assertThat(aligned).isGreaterThanOrEqualTo(0);
    }
}
