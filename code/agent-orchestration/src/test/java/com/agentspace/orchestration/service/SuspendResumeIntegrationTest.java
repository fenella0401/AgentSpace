package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.ExecutorType;
import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.model.flow.AgentFlow;
import com.agentspace.orchestration.model.flow.AgentFlowEdge;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * FE6 suspend-resume：confirm 推进、continue 续聊回 SUSPENDED、cancel 竞态、空 feedback 校验。
 * 见详细设计 §2.2–2.5、§10.3。mock SUCCEED + requiresConfirmation 让 step 停在 SUSPENDED。
 */
@SpringBootTest
@ActiveProfiles({"test", "mock"})
class SuspendResumeIntegrationTest {

    @Autowired
    RunService runService;
    @Autowired
    SuspendResumeService suspendResume;
    @Autowired
    RunCancellationService cancellation;

    private AgentFlowStep step(String id, boolean confirm) {
        return new AgentFlowStep(id, id,
                new AgentSpec(ExecutorType.CLAUDE_CODE, "agent-ref", List.of(), List.of()),
                new PromptSpec("do " + id, Map.of()), confirm);
    }

    /** 两步：a 需确认（停 SUSPENDED），b 普通。 */
    private AgentFlow twoStepFlow(String idemKey) {
        return new AgentFlow("1", "flow-1", "确认流", "snap-" + idemKey,
                new RunRef("run-" + idemKey, idemKey),
                new TenantRef("team-1", "user-1"),
                new TaskRef("task-1", "proj-1"),
                null, null, null, Map.of(),
                List.of(step("a", true), step("b", false)),
                List.of(new AgentFlowEdge("a", "b")));
    }

    private AgentFlow singleConfirmFlow(String idemKey) {
        return new AgentFlow("1", "flow-1", "单确认", "snap-" + idemKey,
                new RunRef("run-" + idemKey, idemKey),
                new TenantRef("team-1", "user-1"),
                new TaskRef("task-1", "proj-1"),
                null, null, null, Map.of(),
                List.of(step("a", true)), List.of());
    }

    private WorkflowStep step(String runId, String key) {
        return runService.findSteps(runId).stream()
                .filter(s -> s.getStepKey().equals(key)).findFirst().orElseThrow();
    }

    private String awaitSuspended(String runId, String key) {
        await().atMost(Duration.ofSeconds(5))
                .until(() -> step(runId, key).getStatus() == StepStatus.SUSPENDED);
        return step(runId, key).getId();
    }

    @Test
    void confirmAdvancesToDownstream() {
        String runId = runService.startRun(twoStepFlow("sr-confirm")).getId();
        String stepId = awaitSuspended(runId, "a");

        WorkflowStep a = suspendResume.confirm(runId, stepId);
        assertThat(a.getStatus()).isEqualTo(StepStatus.COMPLETED);
        // 下游 b 变 READY
        await().atMost(Duration.ofSeconds(5))
                .until(() -> step(runId, "b").getStatus() == StepStatus.READY
                        || step(runId, "b").getStatus() == StepStatus.RUNNING
                        || step(runId, "b").getStatus() == StepStatus.SUSPENDED
                        || step(runId, "b").getStatus() == StepStatus.COMPLETED);
    }

    @Test
    void confirmOnNonSuspendedStepConflicts() {
        String runId = runService.startRun(singleConfirmFlow("sr-conf-conflict")).getId();
        String stepId = awaitSuspended(runId, "a");
        suspendResume.confirm(runId, stepId);
        // 再次 confirm（已 COMPLETED）→ 409
        assertThatThrownBy(() -> suspendResume.confirm(runId, stepId))
                .isInstanceOf(StepActionConflictException.class);
    }

    @Test
    void continueStartsNewAttemptThenBackToSuspended() {
        String runId = runService.startRun(singleConfirmFlow("sr-continue")).getId();
        String stepId = awaitSuspended(runId, "a");

        suspendResume.continueStep(runId, stepId, "请补充并发测试", null);
        // 续聊 attempt（mock 成功 + requiresConfirmation）跑完回 SUSPENDED
        await().atMost(Duration.ofSeconds(5))
                .until(() -> step(runId, "a").getStatus() == StepStatus.SUSPENDED);
        // 已有 2 个 attempt（首次 + continue）
        assertThat(runService.findAttempts(stepId).size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void continueWithBlankFeedbackRejected() {
        String runId = runService.startRun(singleConfirmFlow("sr-blank")).getId();
        String stepId = awaitSuspended(runId, "a");
        assertThatThrownBy(() -> suspendResume.continueStep(runId, stepId, "  ", null))
                .isInstanceOf(StepActionValidationException.class);
    }

    @Test
    void cancelThenConfirmConflicts() {
        String runId = runService.startRun(singleConfirmFlow("sr-cancel")).getId();
        String stepId = awaitSuspended(runId, "a");

        cancellation.cancel(runId);
        // run 进入 CANCELLING/CANCELLED 后 confirm → 409
        assertThatThrownBy(() -> suspendResume.confirm(runId, stepId))
                .isInstanceOf(StepActionConflictException.class);
    }
}
