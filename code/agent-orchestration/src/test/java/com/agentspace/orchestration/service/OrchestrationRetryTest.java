package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.ExecutorType;
import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.WorkflowStep;
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

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import com.agentspace.orchestration.service.run.AttemptResultHandler;
import com.agentspace.orchestration.service.run.RunService;

/**
 * 失败重试与幂等：直接驱动 {@link AttemptResultHandler}（不依赖 mock 行为），确定性验证
 * “自动重试到 maxRetries → step/run FAILED” 与 “终态事件幂等”。见详细设计 §3.1–3.3。
 */
@SpringBootTest
@ActiveProfiles({"test", "mock"})
class OrchestrationRetryTest {

    @Autowired
    RunService runService;
    @Autowired
    AttemptResultHandler resultHandler;

    private AgentFlow singleStepFlow(String idemKey) {
        AgentFlowStep step = new AgentFlowStep("a", "a",
                new AgentSpec(ExecutorType.CLAUDE_CODE, "agent-ref", List.of(), List.of()),
                new PromptSpec("do a", Map.of()), false);
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

    private String latestAttemptId(String stepId) {
        return runService.findAttempts(stepId).stream()
                .max(Comparator.comparingInt(StepAttempt::getAttemptNo)).orElseThrow().getId();
    }

    @Test
    void failureRetriesToLimitThenRunFailed() {
        String runId = runService.startRun(singleStepFlow("retry-fail")).getId();
        String stepId = stepA(runId).getId();

        // 等首个 attempt 建好（mock 异步，但 attempt 在 launch 同步建立，这里直接拿最新）
        await().atMost(Duration.ofSeconds(5)).until(() -> !runService.findAttempts(stepId).isEmpty());

        // maxRetries=2：手动注入 3 次失败（首次 + 2 次重试）
        for (int i = 0; i < 3; i++) {
            resultHandler.onAttemptFailed(latestAttemptId(stepId), "EXECUTOR_FAILED", "boom" + i);
        }

        WorkflowStep a = stepA(runId);
        assertThat(a.getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(a.getRetryCount()).isEqualTo(2);
        assertThat(runService.findRun(runId).orElseThrow().getStatus()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void duplicateTerminalEventIsIgnored() {
        String runId = runService.startRun(singleStepFlow("retry-dup")).getId();
        String stepId = stepA(runId).getId();
        await().atMost(Duration.ofSeconds(5)).until(() -> !runService.findAttempts(stepId).isEmpty());

        String attemptId = latestAttemptId(stepId);
        resultHandler.onAttemptSucceeded(attemptId, "done", "r", "s", null);
        // 重复成功事件：attempt 已终态，应被忽略
        resultHandler.onAttemptSucceeded(attemptId, "done", "r", "s", null);

        long completedA = runService.findSteps(runId).stream()
                .filter(s -> s.getStepKey().equals("a"))
                .filter(s -> s.getStatus() == StepStatus.COMPLETED).count();
        assertThat(completedA).isEqualTo(1);
    }
}
