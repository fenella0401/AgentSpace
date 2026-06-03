package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.model.ExecutorType;
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
import static org.awaitility.Awaitility.await;
import com.agentspace.orchestration.service.run.RunService;

/**
 * M2 启动链路集成测试（test + mock profile）。mock 异步推事件，用 awaitility 等待收敛。
 * 验证：提交三步 AgentFlow → 落库 step、prompt 渲染、attempt 建立并最终成功；idempotencyKey 幂等。
 */
@SpringBootTest
@ActiveProfiles({"test", "mock"})
class RunServiceIntegrationTest {

    @Autowired
    RunService runService;

    private AgentFlowStep step(String id, String template, boolean confirm) {
        return new AgentFlowStep(id, id,
                new AgentSpec(ExecutorType.CLAUDE_CODE, "agent-ref", List.of(), List.of()),
                new PromptSpec(template, Map.of()),
                confirm);
    }

    private AgentFlow threeStepFlow(String idemKey) {
        return new AgentFlow("1", "flow-1", "三步流", "snap-" + idemKey,
                new RunRef("run-" + idemKey, idemKey),
                new TenantRef("team-1", "user-1"),
                new TaskRef("task-1", "proj-1"),
                null, null, null,
                Map.of("task", Map.of("title", "实现登录")),
                List.of(step("analyze", "分析 {{task.title}}", false),
                        step("fix", "修复", false),
                        step("verify", "验证", true)),
                List.of(new AgentFlowEdge("analyze", "fix"),
                        new AgentFlowEdge("fix", "verify")));
    }

    private WorkflowStep step(String runId, String key) {
        return runService.findSteps(runId).stream()
                .filter(s -> s.getStepKey().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void startRunPersistsStepsAndRendersFirstPrompt() {
        WorkflowRun run = runService.startRun(threeStepFlow("idem-1"));
        String runId = run.getId();

        List<WorkflowStep> steps = runService.findSteps(runId);
        assertThat(steps).hasSize(3);

        // 首 step 已渲染 prompt（启动时同步完成）
        assertThat(step(runId, "analyze").getRenderedPrompt()).isEqualTo("分析 实现登录");

        // 首 step 已建 attempt 并经 mock 拿到 runtimeRef
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<StepAttempt> attempts = runService.findAttempts(step(runId, "analyze").getId());
            assertThat(attempts).hasSize(1);
            assertThat(attempts.get(0).getRuntimeAttemptRef()).startsWith("mock-rt-");
        });

        // mock 异步成功 → analyze 最终 COMPLETED；下游 fix 变 READY
        await().atMost(Duration.ofSeconds(5))
                .until(() -> step(runId, "analyze").getStatus() == StepStatus.COMPLETED);
        await().atMost(Duration.ofSeconds(5))
                .until(() -> step(runId, "fix").getStatus() == StepStatus.READY);
    }

    @Test
    void startRunIsIdempotent() {
        WorkflowRun first = runService.startRun(threeStepFlow("idem-dup"));
        WorkflowRun second = runService.startRun(threeStepFlow("idem-dup"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(runService.findSteps(first.getId())).hasSize(3);
    }
}
