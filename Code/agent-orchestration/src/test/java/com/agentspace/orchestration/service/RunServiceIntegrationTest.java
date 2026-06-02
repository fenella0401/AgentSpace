package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.RunStatus;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 启动链路集成测试（test + mock profile，H2 + Flyway + mock Agent Core）。
 * 验证：提交三步 AgentFlow → run RUNNING、首 step RUNNING、attempt 创建；idempotencyKey 幂等。
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

    @Test
    void startRunDrivesFirstStepRunningAndCreatesAttempt() {
        WorkflowRun run = runService.startRun(threeStepFlow("idem-1"));

        assertThat(run.getStatus()).isEqualTo(RunStatus.RUNNING);

        List<WorkflowStep> steps = runService.findSteps(run.getId());
        assertThat(steps).hasSize(3);

        WorkflowStep analyze = steps.stream()
                .filter(s -> s.getStepKey().equals("analyze")).findFirst().orElseThrow();
        // 首 step 已渲染 prompt 并进入 RUNNING
        assertThat(analyze.getStatus()).isEqualTo(StepStatus.RUNNING);
        assertThat(analyze.getRenderedPrompt()).isEqualTo("分析 实现登录");

        // 下游 step 仍 PENDING
        WorkflowStep fix = steps.stream()
                .filter(s -> s.getStepKey().equals("fix")).findFirst().orElseThrow();
        assertThat(fix.getStatus()).isEqualTo(StepStatus.PENDING);

        // 首 step 已建 attempt 且经 mock StartAttempt 拿到 runtimeRef
        List<StepAttempt> attempts = runService.findAttempts(analyze.getId());
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getRuntimeAttemptRef()).startsWith("mock-rt-");
        assertThat(attempts.get(0).getStatus()).isEqualTo(AttemptStatus.STARTING);
    }

    @Test
    void startRunIsIdempotent() {
        WorkflowRun first = runService.startRun(threeStepFlow("idem-dup"));
        WorkflowRun second = runService.startRun(threeStepFlow("idem-dup"));

        assertThat(second.getId()).isEqualTo(first.getId());
        // 不重复建 step
        assertThat(runService.findSteps(first.getId())).hasSize(3);
    }
}
