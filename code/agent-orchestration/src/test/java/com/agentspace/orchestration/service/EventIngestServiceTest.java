package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.ExecutorType;
import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.StepAttempt;
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
import com.agentspace.orchestration.service.run.RunService;
import com.agentspace.orchestration.service.event.EventIngestService;
import com.agentspace.orchestration.service.exception.EventAttributionException;

/**
 * FE3 事件接入：eventId 去重、归属校验、attempt.result 与 runtime.* 乱序合并判定终态。
 * 见详细设计 §2.8、§8.4。用单步 flow + 直接注入事件，避免 mock 自动事件干扰。
 *
 * <p>注：mock 全局 SUCCEED，单步会异步自动跑完。这里用 requiresConfirmation=true 让首 step
 * 停在 SUSPENDED，从而隔离地手工注入事件验证 ingest 逻辑。
 */
@SpringBootTest
@ActiveProfiles({"test", "mock"})
@TestPropertySource(properties = "mock.agent-core.behavior=TIMEOUT")
class EventIngestServiceTest {

    @Autowired
    RunService runService;
    @Autowired
    EventIngestService ingestService;

    private AgentFlow singleStepFlow(String idemKey, boolean confirm) {
        AgentFlowStep step = new AgentFlowStep("a", "a",
                new AgentSpec(ExecutorType.CLAUDE_CODE, "agent-ref", List.of(), List.of()),
                new PromptSpec("do a", Map.of()), confirm);
        return new AgentFlow("1", "flow-1", "单步", "snap-" + idemKey,
                new RunRef("run-" + idemKey, idemKey),
                new TenantRef("team-1", "user-1"),
                new TaskRef("task-1", "proj-1"),
                null, null, null, Map.of(),
                List.of(step), List.of());
    }

    private WorkflowStep stepA(String runId) {
        return runService.findSteps(runId).stream()
                .filter(s -> s.getStepKey().equals("a")).findFirst().orElseThrow();
    }

    private StepAttempt latestAttempt(String stepId) {
        return runService.findAttempts(stepId).stream()
                .max(Comparator.comparingInt(StepAttempt::getAttemptNo)).orElseThrow();
    }

    private AgentExecutionEvent event(String type, EventSource src, String runId, String stepId,
                                      String attemptId, long seq, Map<String, Object> payload) {
        return new AgentExecutionEvent("evt-" + type + "-" + seq, type, runId, stepId, "a",
                attemptId, 1, Instant.now(), seq, src, payload);
    }

    @Test
    void duplicateEventIdIsIdempotent() {
        String runId = runService.startRun(singleStepFlow("ev-dup", true)).getId();
        await().atMost(Duration.ofSeconds(5)).until(() -> !runService.findAttempts(stepA(runId).getId()).isEmpty());
        String stepId = stepA(runId).getId();
        String attemptId = latestAttempt(stepId).getId();

        AgentExecutionEvent hb = new AgentExecutionEvent("dup-evt-1", EventTypes.ATTEMPT_HEARTBEAT,
                runId, stepId, "a", attemptId, 1, Instant.now(), 99L, EventSource.AGENT_CORE, Map.of());
        ingestService.ingest(hb);
        // 重复同一 eventId 不抛异常
        ingestService.ingest(hb);
    }

    @Test
    void unknownAttemptThrowsAttributionError() {
        AgentExecutionEvent ev = event(EventTypes.ATTEMPT_HEARTBEAT, EventSource.AGENT_CORE,
                "run-x", "step-x", "attempt-does-not-exist", 1L, Map.of());
        assertThatThrownBy(() -> ingestService.ingest(ev))
                .isInstanceOf(EventAttributionException.class);
    }

    @Test
    void mismatchedRunIdThrowsAttributionError() {
        String runId = runService.startRun(singleStepFlow("ev-mismatch", true)).getId();
        await().atMost(Duration.ofSeconds(5)).until(() -> !runService.findAttempts(stepA(runId).getId()).isEmpty());
        String stepId = stepA(runId).getId();
        String attemptId = latestAttempt(stepId).getId();

        AgentExecutionEvent ev = event(EventTypes.ATTEMPT_HEARTBEAT, EventSource.AGENT_CORE,
                "WRONG-RUN", stepId, attemptId, 1L, Map.of());
        assertThatThrownBy(() -> ingestService.ingest(ev))
                .isInstanceOf(EventAttributionException.class);
    }

    @Test
    void resultBeforeRuntimeStillFinalizesSucceeded() {
        // confirm=false 但用直接注入控制顺序：先 result(success) 后 runtime.completed
        String runId = runService.startRun(singleStepFlow("ev-order1", true)).getId();
        await().atMost(Duration.ofSeconds(5)).until(() -> !runService.findAttempts(stepA(runId).getId()).isEmpty());
        String stepId = stepA(runId).getId();
        String attemptId = latestAttempt(stepId).getId();

        ingestService.ingest(event(EventTypes.ATTEMPT_RESULT, EventSource.AGENT_CORE, runId, stepId, attemptId, 10L,
                Map.of("status", "SUCCEEDED", "summary", "ok", "sessionRef", "s1")));
        // 只有 result，runtime 未到 → 尚未判终态（step 仍 RUNNING/SUSPENDED 之外）
        ingestService.ingest(event(EventTypes.RUNTIME_COMPLETED, EventSource.RUNTIME, runId, stepId, attemptId, 11L, Map.of()));

        // 两信号齐 → 成功；step requiresConfirmation=true → SUSPENDED
        await().atMost(Duration.ofSeconds(5))
                .until(() -> stepA(runId).getStatus() == StepStatus.SUSPENDED);
    }

    @Test
    void runtimeFailedFinalizesFailedWithoutResult() {
        String runId = runService.startRun(singleStepFlow("ev-rtfail", true)).getId();
        await().atMost(Duration.ofSeconds(5)).until(() -> !runService.findAttempts(stepA(runId).getId()).isEmpty());
        String stepId = stepA(runId).getId();
        String attemptId = latestAttempt(stepId).getId();

        // 只 runtime.failed，无 result → 直接失败（maxRetries=2，会重试，最终若干次后耗尽）
        ingestService.ingest(event(EventTypes.RUNTIME_FAILED, EventSource.RUNTIME, runId, stepId, attemptId, 20L,
                Map.of("failureReason", "RUNTIME_FAILED")));

        // 首次失败后自动重试（step 回到 RUNNING，retryCount 增加）
        await().atMost(Duration.ofSeconds(5))
                .until(() -> stepA(runId).getRetryCount() >= 1);
    }
}
