package com.agentspace.orchestration.service;

import com.agentspace.orchestration.client.mock.MockAgentCoreClient;
import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.ExecutorType;
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

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import com.agentspace.orchestration.service.run.ReconciliationService;
import com.agentspace.orchestration.service.run.RunService;

/**
 * Agent Core API 中断（升级/网络）后的恢复：StartAttempt 中断不回滚、不重复起 attempt；
 * reconcile 据 query found? 判定——未送达→失败重试，已送达在跑→对齐 RUNNING。见详细设计 §3.5、§11#1。
 */
@SpringBootTest
@ActiveProfiles({"test", "mock"})
class ApiInterruptionRecoveryTest {

    @Autowired
    RunService runService;
    @Autowired
    ReconciliationService reconciliation;
    @Autowired
    MockAgentCoreClient mockAgentCore;

    private AgentFlow singleStepFlow(String idemKey) {
        AgentFlowStep step = new AgentFlowStep("a", "a",
                new AgentSpec(ExecutorType.CLAUDE_CODE, "agent-ref", List.of(), List.of()),
                new PromptSpec("do a", Map.of()), false);
        return new AgentFlow("1", "flow-1", "f", "snap-" + idemKey,
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

    @Test
    void startAttemptInterruptedKeepsStartingNoDuplicate() {
        // StartAttempt 未送达即中断
        mockAgentCore.setFailStart(true);
        try {
            String runId = runService.startRun(singleStepFlow("intr-start")).getId();
            WorkflowStep a = stepA(runId);
            // step 不退回 READY（仍 RUNNING），attempt 留 STARTING、无 runtimeRef，且只有 1 个（不重复起）
            assertThat(a.getStatus()).isEqualTo(StepStatus.RUNNING);
            List<StepAttempt> attempts = runService.findAttempts(a.getId());
            assertThat(attempts).hasSize(1);
            assertThat(attempts.get(0).getStatus()).isEqualTo(AttemptStatus.STARTING);
            assertThat(attempts.get(0).getRuntimeAttemptRef()).isNull();
        } finally {
            mockAgentCore.setFailStart(false);
        }
    }

    @Test
    void reconcileFailsUndeliveredStartThenRetries() {
        mockAgentCore.setFailStart(true);
        String runId;
        String stepId;
        try {
            runId = runService.startRun(singleStepFlow("intr-undelivered")).getId();
            stepId = stepA(runId).getId();
        } finally {
            mockAgentCore.setFailStart(false);
        }
        // attempt 卡 STARTING；Agent Core 无此 attempt（query not-found）
        StepAttempt stuck = latestAttempt(stepId);
        assertThat(stuck.getStatus()).isEqualTo(AttemptStatus.STARTING);

        // reconcile：not-found → RUNTIME_CREATE_FAILED → step 自动重试起新 attempt（attemptNo 递增）
        reconciliation.reconcileOne(stuck.getId());

        assertThat(runService.findAttempts(stepId).size()).isGreaterThanOrEqualTo(2);
        // 原 attempt 已终态 FAILED
        StepAttempt original = runService.findAttempts(stepId).stream()
                .filter(at -> at.getId().equals(stuck.getId())).findFirst().orElseThrow();
        assertThat(original.getStatus()).isEqualTo(AttemptStatus.FAILED);
        assertThat(original.getFailureReason()).isEqualTo("RUNTIME_CREATE_FAILED");
    }

    @Test
    void reconcileAlignsRunningWhenStartResponseLost() {
        // StartAttempt 已送达、Agent Core 已起 attempt，但响应丢失
        mockAgentCore.setFailStartAfterRegister(true);
        String runId;
        String stepId;
        try {
            runId = runService.startRun(singleStepFlow("intr-resp-lost")).getId();
            stepId = stepA(runId).getId();
        } finally {
            mockAgentCore.setFailStartAfterRegister(false);
        }
        StepAttempt stuck = latestAttempt(stepId);
        assertThat(stuck.getStatus()).isEqualTo(AttemptStatus.STARTING);

        // reconcile：query found 且 RUNNING → 本地对齐成 RUNNING、补 runtimeRef，不重复起 attempt
        reconciliation.reconcileOne(stuck.getId());

        StepAttempt aligned = latestAttempt(stepId);
        assertThat(aligned.getId()).isEqualTo(stuck.getId());
        assertThat(aligned.getStatus()).isEqualTo(AttemptStatus.RUNNING);
        assertThat(aligned.getRuntimeAttemptRef()).isNotNull();
        assertThat(runService.findAttempts(stepId)).hasSize(1);
    }
}
