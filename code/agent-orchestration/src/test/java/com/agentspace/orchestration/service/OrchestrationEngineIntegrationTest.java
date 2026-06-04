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
import static org.awaitility.Awaitility.await;
import com.agentspace.orchestration.service.run.RunService;

/**
 * FE2+FE3 端到端集成测试（test + mock profile）。mock 异步推事件经
 * IngestingEventSink → EventIngestService 驱动状态机；RunScheduleKicker 直驱推进多 step
 * （轮询在 test profile 下间隔极大，几乎不介入，故链条自驱动即验证了直驱快路径）。
 * 用 awaitility 等待异步状态收敛。
 */
@SpringBootTest
@ActiveProfiles({"test", "mock"})
class OrchestrationEngineIntegrationTest {

    @Autowired
    RunService runService;

    private AgentFlowStep step(String id) {
        return new AgentFlowStep(id, id,
                new AgentSpec(ExecutorType.CLAUDE_CODE, "agent-ref", List.of(), List.of()),
                new PromptSpec("do " + id, Map.of()),
                false);
    }

    private AgentFlow chainFlow(String idemKey) {
        return new AgentFlow("1", "flow-1", "链", "snap-" + idemKey,
                new RunRef("run-" + idemKey, idemKey),
                new TenantRef("team-1", "user-1"),
                new TaskRef("task-1", "proj-1"),
                null, null, null, Map.of(),
                List.of(step("a"), step("b"), step("c")),
                List.of(new AgentFlowEdge("a", "b"), new AgentFlowEdge("b", "c")));
    }

    private StepStatus stepStatus(String runId, String key) {
        return runService.findSteps(runId).stream()
                .filter(s -> s.getStepKey().equals(key)).findFirst()
                .map(WorkflowStep::getStatus).orElseThrow();
    }

    @Test
    void chainAllSucceededDrivesRunCompleted() {
        String runId = runService.startRun(chainFlow("e2e-ok")).getId();

        // 全程不手动驱动调度：a 完成 → 直驱启 b → b 完成 → 直驱启 c → 全 COMPLETED。
        await().atMost(Duration.ofSeconds(10))
                .until(() -> stepStatus(runId, "a") == StepStatus.COMPLETED);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> stepStatus(runId, "b") == StepStatus.COMPLETED);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> stepStatus(runId, "c") == StepStatus.COMPLETED);

        await().atMost(Duration.ofSeconds(10))
                .until(() -> runService.findRun(runId).orElseThrow().getStatus() == RunStatus.COMPLETED);
        assertThat(runService.findSteps(runId)).allMatch(s -> s.getStatus() == StepStatus.COMPLETED);
    }

    @Test
    void firstStepEventuallySucceeds() {
        String runId = runService.startRun(chainFlow("e2e-first")).getId();
        await().atMost(Duration.ofSeconds(5))
                .until(() -> stepStatus(runId, "a") == StepStatus.COMPLETED);
        assertThat(runService.findRun(runId).orElseThrow().getStatus())
                .isIn(RunStatus.RUNNING, RunStatus.COMPLETED);
    }
}
