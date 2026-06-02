package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.ExecutorType;
import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.WorkflowRun;
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

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FE2 状态机与调度链路集成测试（test + mock profile）。
 * 验证：三步链全成功 → run COMPLETED；失败自动重试到上限 → step/run FAILED；串行单飞。
 */
@SpringBootTest
@ActiveProfiles({"test", "mock"})
class OrchestrationEngineIntegrationTest {

    @Autowired
    RunService runService;
    @Autowired
    AttemptResultHandler resultHandler;
    @Autowired
    SchedulingService schedulingService;

    private AgentFlowStep step(String id, boolean confirm) {
        return new AgentFlowStep(id, id,
                new AgentSpec(ExecutorType.CLAUDE_CODE, "agent-ref", List.of(), List.of()),
                new PromptSpec("do " + id, Map.of()),
                confirm);
    }

    private AgentFlow chainFlow(String idemKey) {
        return new AgentFlow("1", "flow-1", "链", "snap-" + idemKey,
                new RunRef("run-" + idemKey, idemKey),
                new TenantRef("team-1", "user-1"),
                new TaskRef("task-1", "proj-1"),
                null, null, null, Map.of(),
                List.of(step("a", false), step("b", false), step("c", false)),
                List.of(new AgentFlowEdge("a", "b"), new AgentFlowEdge("b", "c")));
    }

    private WorkflowStep runningStep(String runId) {
        return runService.findSteps(runId).stream()
                .filter(s -> s.getStatus() == StepStatus.RUNNING)
                .findFirst().orElseThrow(() -> new AssertionError("无 RUNNING step"));
    }

    private StepAttempt latestAttempt(String stepId) {
        return runService.findAttempts(stepId).stream()
                .max(Comparator.comparingInt(StepAttempt::getAttemptNo)).orElseThrow();
    }

    private long runningCount(String runId) {
        return runService.findSteps(runId).stream()
                .filter(s -> s.getStatus() == StepStatus.RUNNING).count();
    }

    @Test
    void chainAllSucceededDrivesRunCompleted() {
        WorkflowRun run = runService.startRun(chainFlow("chain-ok"));
        String runId = run.getId();

        // 串行：任意时刻最多一个 RUNNING
        assertThat(runningCount(runId)).isEqualTo(1);

        // step a 成功 → b 变 READY → 调度启动 b
        WorkflowStep a = runningStep(runId);
        assertThat(a.getStepKey()).isEqualTo("a");
        resultHandler.onAttemptSucceeded(latestAttempt(a.getId()).getId(), "a done", "ra", "sess-a");
        schedulingService.tryLaunchReadyStep(readyStepId(runId));
        assertThat(runningCount(runId)).isEqualTo(1);

        // step b 成功 → c
        WorkflowStep b = runningStep(runId);
        assertThat(b.getStepKey()).isEqualTo("b");
        resultHandler.onAttemptSucceeded(latestAttempt(b.getId()).getId(), "b done", "rb", "sess-b");
        schedulingService.tryLaunchReadyStep(readyStepId(runId));

        // step c 成功 → run COMPLETED
        WorkflowStep c = runningStep(runId);
        assertThat(c.getStepKey()).isEqualTo("c");
        resultHandler.onAttemptSucceeded(latestAttempt(c.getId()).getId(), "c done", "rc", "sess-c");

        assertThat(runService.findRun(runId).orElseThrow().getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(runService.findSteps(runId)).allMatch(s -> s.getStatus() == StepStatus.COMPLETED);
    }

    @Test
    void failureRetriesToLimitThenRunFailed() {
        WorkflowRun run = runService.startRun(chainFlow("chain-fail"));
        String runId = run.getId();

        WorkflowStep a = runningStep(runId);
        // maxRetries=2：首次失败→重试1，再失败→重试2，第三次失败→耗尽
        resultHandler.onAttemptFailed(latestAttempt(a.getId()).getId(), "EXECUTOR_FAILED", "boom1");
        assertThat(runningStep(runId).getStepKey()).isEqualTo("a"); // 重试后仍在跑 a
        resultHandler.onAttemptFailed(latestAttempt(a.getId()).getId(), "EXECUTOR_FAILED", "boom2");
        resultHandler.onAttemptFailed(latestAttempt(a.getId()).getId(), "EXECUTOR_FAILED", "boom3");

        WorkflowStep aFinal = runService.findSteps(runId).stream()
                .filter(s -> s.getStepKey().equals("a")).findFirst().orElseThrow();
        assertThat(aFinal.getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(aFinal.getRetryCount()).isEqualTo(2);
        assertThat(runService.findRun(runId).orElseThrow().getStatus()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void duplicateSuccessEventIsIdempotent() {
        WorkflowRun run = runService.startRun(chainFlow("chain-dup"));
        WorkflowStep a = runningStep(run.getId());
        String attemptId = latestAttempt(a.getId()).getId();

        resultHandler.onAttemptSucceeded(attemptId, "done", "r", "s");
        // 重复事件：attempt 已终态，应被忽略，不抛异常、不重复推进
        resultHandler.onAttemptSucceeded(attemptId, "done", "r", "s");

        long completed = runService.findSteps(run.getId()).stream()
                .filter(s -> s.getStepKey().equals("a"))
                .filter(s -> s.getStatus() == StepStatus.COMPLETED).count();
        assertThat(completed).isEqualTo(1);
    }

    private String readyStepId(String runId) {
        return runService.findSteps(runId).stream()
                .filter(s -> s.getStatus() == StepStatus.READY)
                .findFirst().map(WorkflowStep::getId).orElse("none");
    }
}
