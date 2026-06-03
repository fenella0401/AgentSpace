package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.ExecutorType;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.model.event.AgentExecutionEvent;
import com.agentspace.orchestration.model.event.EventSource;
import com.agentspace.orchestration.model.event.EventTypes;
import com.agentspace.orchestration.model.flow.AgentFlow;
import com.agentspace.orchestration.model.flow.AgentFlowStep;
import com.agentspace.orchestration.model.flow.AgentSpec;
import com.agentspace.orchestration.model.flow.PromptSpec;
import com.agentspace.orchestration.model.flow.RunRef;
import com.agentspace.orchestration.model.flow.TaskRef;
import com.agentspace.orchestration.model.flow.TenantRef;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import com.agentspace.orchestration.service.run.RunCancellationService;
import com.agentspace.orchestration.service.run.SuspendResumeService;
import com.agentspace.orchestration.service.run.RunService;
import com.agentspace.orchestration.service.event.EventIngestService;
import com.agentspace.orchestration.service.exception.StepActionConflictException;
import com.agentspace.orchestration.service.exception.IdempotencyConflictException;

/**
 * 覆盖 spec 审计后修复的真问题：幂等 body 冲突、retry 竞态、PROMPT_RENDER_ERROR、
 * runtime.cancelled、artifactRefs 落库。mock TIMEOUT 隔离自动事件，手工驱动。
 */
@SpringBootTest
@ActiveProfiles({"test", "mock"})
@TestPropertySource(properties = "mock.agent-core.behavior=TIMEOUT")
class SpecFixesTest {

    @Autowired
    RunService runService;
    @Autowired
    EventIngestService ingestService;
    @Autowired
    SuspendResumeService suspendResume;
    @Autowired
    RunCancellationService cancellation;

    private AgentFlowStep step(String id, String template, boolean confirm) {
        return new AgentFlowStep(id, id,
                new AgentSpec(ExecutorType.CLAUDE_CODE, "agent-ref", List.of(), List.of()),
                new PromptSpec(template, Map.of()), confirm);
    }

    private AgentFlow flow(String idemKey, String template, boolean confirm, Map<String, Object> vars) {
        return new AgentFlow("1", "flow-1", "f", "snap-" + idemKey,
                new RunRef("run-" + idemKey, idemKey),
                new TenantRef("team-1", "user-1"),
                new TaskRef("task-1", "proj-1"),
                null, null, null, vars,
                List.of(step("a", template, confirm)), List.of());
    }

    private WorkflowStep stepA(String runId) {
        return runService.findSteps(runId).stream()
                .filter(s -> s.getStepKey().equals("a")).findFirst().orElseThrow();
    }

    private StepAttempt latestAttempt(String stepId) {
        return runService.findAttempts(stepId).stream()
                .max(Comparator.comparingInt(StepAttempt::getAttemptNo)).orElseThrow();
    }

    private AgentExecutionEvent ev(String type, EventSource src, String runId, String stepId,
                                   String attemptId, long seq, Map<String, Object> payload) {
        return new AgentExecutionEvent("eid-" + type + "-" + seq, type, runId, stepId, "a",
                attemptId, 1, Instant.now(), seq, src, payload);
    }

    // #3 同 Idempotency-Key 但 body 不一致 → 409
    @Test
    void duplicateKeyDifferentBodyConflicts() {
        runService.startRun(flow("idem-x", "do a", false, Map.of()));
        assertThatThrownBy(() ->
                runService.startRun(flow("idem-x", "DIFFERENT prompt", false, Map.of())))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    // #3 同 key 同 body → 幂等复用
    @Test
    void duplicateKeySameBodyReuses() {
        WorkflowRun a = runService.startRun(flow("idem-same", "do a", false, Map.of()));
        WorkflowRun b = runService.startRun(flow("idem-same", "do a", false, Map.of()));
        assertThat(b.getId()).isEqualTo(a.getId());
    }

    // #1 prompt 引用不存在变量 → step FAILED(PROMPT_RENDER_ERROR)，run FAILED，不无限重试
    @Test
    void promptRenderErrorFailsStepNotInfiniteRetry() {
        String runId = runService.startRun(flow("idem-render", "用 {{missing.var}}", false, Map.of())).getId();
        WorkflowStep a = stepA(runId);
        assertThat(a.getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(a.getErrorCode()).isEqualTo("PROMPT_RENDER_ERROR");
        StepAttempt attempt = latestAttempt(a.getId());
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.FAILED);
        assertThat(attempt.getFailureReason()).isEqualTo("PROMPT_RENDER_ERROR");
        assertThat(runService.findRun(runId).orElseThrow().getStatus()).isEqualTo(RunStatus.FAILED);
    }

    // #5 cancel 后 retry FAILED step → 409
    @Test
    void retryAfterCancelConflicts() {
        String runId = runService.startRun(flow("idem-retry", "用 {{missing.var}}", false, Map.of())).getId();
        String stepId = stepA(runId).getId();
        // step 已 FAILED(render error)；run 已 FAILED。cancel 后 retry 应 409
        cancellation.cancel(runId);
        assertThatThrownBy(() -> suspendResume.retry(runId, stepId, false, null))
                .isInstanceOf(StepActionConflictException.class);
    }

    // #7 artifactRefs 经事件落库到 step
    @Test
    void artifactRefsPersistedToStep() {
        String runId = runService.startRun(flow("idem-art", "do a", false, Map.of())).getId();
        String stepId = stepA(runId).getId();
        await().atMost(Duration.ofSeconds(5)).until(() -> !runService.findAttempts(stepId).isEmpty());
        String attemptId = latestAttempt(stepId).getId();

        ingestService.ingest(ev(EventTypes.ATTEMPT_RESULT, EventSource.AGENT_CORE, runId, stepId, attemptId, 10L,
                Map.of("status", "SUCCEEDED", "summary", "ok",
                        "artifactRefs", List.of("s3://a", "s3://b"))));
        ingestService.ingest(ev(EventTypes.RUNTIME_COMPLETED, EventSource.RUNTIME, runId, stepId, attemptId, 11L, Map.of()));

        await().atMost(Duration.ofSeconds(5))
                .until(() -> stepA(runId).getStatus() == StepStatus.COMPLETED);
        String refs = stepA(runId).getOutputArtifactRefs();
        assertThat(refs).contains("s3://a").contains("s3://b");
    }

    // #6 runtime.cancelled → attempt/step CANCELLED
    @Test
    void runtimeCancelledFinalizesCancelled() {
        String runId = runService.startRun(flow("idem-rtc", "do a", false, Map.of())).getId();
        String stepId = stepA(runId).getId();
        await().atMost(Duration.ofSeconds(5)).until(() -> !runService.findAttempts(stepId).isEmpty());
        String attemptId = latestAttempt(stepId).getId();

        ingestService.ingest(ev(EventTypes.RUNTIME_CANCELLED, EventSource.RUNTIME, runId, stepId, attemptId, 20L, Map.of()));

        await().atMost(Duration.ofSeconds(5))
                .until(() -> latestAttempt(stepId).getStatus() == AttemptStatus.CANCELLED);
        assertThat(stepA(runId).getStatus()).isEqualTo(StepStatus.CANCELLED);
    }
}
