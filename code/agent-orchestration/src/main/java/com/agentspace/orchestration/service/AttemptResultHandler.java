package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.AttemptTrigger;
import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.StepDependency;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.model.flow.AgentFlow;
import com.agentspace.orchestration.repository.StepAttemptRepository;
import com.agentspace.orchestration.repository.StepDependencyRepository;
import com.agentspace.orchestration.repository.WorkflowRunRepository;
import com.agentspace.orchestration.repository.WorkflowStepRepository;
import com.agentspace.orchestration.config.OrchestrationProperties;
import com.agentspace.orchestration.service.statemachine.StateTransitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * attempt 终态 → step / run 状态推进。承载 FE2 的核心转换：成功完成、自动重试、失败、下游 ready。
 * 见详细设计 §3.1–3.3、§8.4。
 *
 * <p>本步由测试或（FE3 后）事件端点调用 {@link #onAttemptSucceeded}/{@link #onAttemptFailed}。
 */
@Component
public class AttemptResultHandler {

    private static final Logger log = LoggerFactory.getLogger(AttemptResultHandler.class);

    private final WorkflowRunRepository runRepo;
    private final WorkflowStepRepository stepRepo;
    private final StepAttemptRepository attemptRepo;
    private final StepDependencyRepository depRepo;
    private final RunStateRecalculator runRecalc;
    private final StepLauncher stepLauncher;
    private final DownstreamTrigger downstreamTrigger;
    private final AgentFlowCodec codec;
    private final OrchestrationProperties props;
    private final OutboxService outboxService;
    private final OrchestrationMetrics metrics;

    public AttemptResultHandler(WorkflowRunRepository runRepo,
                                WorkflowStepRepository stepRepo,
                                StepAttemptRepository attemptRepo,
                                StepDependencyRepository depRepo,
                                RunStateRecalculator runRecalc,
                                StepLauncher stepLauncher,
                                DownstreamTrigger downstreamTrigger,
                                AgentFlowCodec codec,
                                OrchestrationProperties props,
                                OutboxService outboxService,
                                OrchestrationMetrics metrics) {
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
        this.attemptRepo = attemptRepo;
        this.depRepo = depRepo;
        this.runRecalc = runRecalc;
        this.stepLauncher = stepLauncher;
        this.downstreamTrigger = downstreamTrigger;
        this.codec = codec;
        this.props = props;
        this.outboxService = outboxService;
        this.metrics = metrics;
    }

    /**
     * attempt 成功：写 output。requiresConfirmation=false → step COMPLETED + 触发下游；
     * =true → step SUSPENDED（FE6 深化 confirm/continue）。
     */
    @Transactional
    public void onAttemptSucceeded(String attemptId, String summary, String result, String sessionRef) {
        StepAttempt attempt = attemptRepo.findById(attemptId).orElseThrow();
        if (StateTransitions.isTerminal(attempt.getStatus())) {
            log.warn("attempt {} 已终态 {}，忽略重复成功事件", attemptId, attempt.getStatus());
            return;
        }
        markAttemptTerminal(attempt, AttemptStatus.SUCCEEDED, null, null);

        WorkflowStep step = stepRepo.findById(attempt.getStepId()).orElseThrow();
        step.setOutputSummary(summary);
        step.setOutputResult(result);
        step.setSessionRef(sessionRef);

        if (step.isRequiresConfirmation()) {
            step.setStatus(StepStatus.SUSPENDED);
            touch(step);
            outboxService.enqueueStateChange(attempt.getRunId(), step.getId(), attemptId,
                    "step.suspended",
                    java.util.Map.of("stepId", step.getId(), "stepKey", step.getStepKey()));
            log.info("step {} 成功且需确认 → SUSPENDED", step.getStepKey());
        } else {
            step.setStatus(StepStatus.COMPLETED);
            step.setFinishedAt(OffsetDateTime.now());
            touch(step);
            downstreamTrigger.triggerDownstream(step);
        }
        recalcRun(attempt.getRunId());
    }

    /**
     * attempt 失败：未超 maxRetries → 自动重试（起新 AUTO_RETRY attempt）；超限 → step FAILED → run 重算。
     */
    @Transactional
    public void onAttemptFailed(String attemptId, String failureReason, String errorMessage) {
        StepAttempt attempt = attemptRepo.findById(attemptId).orElseThrow();
        if (StateTransitions.isTerminal(attempt.getStatus())) {
            log.warn("attempt {} 已终态 {}，忽略重复失败事件", attemptId, attempt.getStatus());
            return;
        }
        markAttemptTerminal(attempt, AttemptStatus.FAILED, failureReason, errorMessage);
        metrics.recordFailure(failureReason);

        WorkflowStep step = stepRepo.findById(attempt.getStepId()).orElseThrow();
        int maxRetries = props.maxRetriesFor(step.getExecutorType());

        if (step.getRetryCount() < maxRetries) {
            step.setRetryCount(step.getRetryCount() + 1);
            step.setStatus(StepStatus.READY);   // 经 READY 再由调度器/launcher 起新 attempt
            touch(step);
            WorkflowRun run = runRepo.findById(attempt.getRunId()).orElseThrow();
            AgentFlow flow = codec.fromJson(run.getAgentFlowJson());
            stepLauncher.launch(run, step, flow, AttemptTrigger.AUTO_RETRY, null, null);
            log.info("step {} 自动重试 {}/{}", step.getStepKey(), step.getRetryCount(), maxRetries);
        } else {
            step.setStatus(StepStatus.FAILED);
            step.setErrorCode(failureReason);
            step.setErrorMessage(errorMessage);
            step.setFinishedAt(OffsetDateTime.now());
            touch(step);
            log.info("step {} 重试耗尽 → FAILED", step.getStepKey());
        }
        recalcRun(attempt.getRunId());
    }

    private void markAttemptTerminal(StepAttempt attempt, AttemptStatus status,
                                     String failureReason, String errorMessage) {
        attempt.setStatus(status);
        attempt.setFailureReason(failureReason);
        attempt.setErrorMessage(errorMessage);
        attempt.setFinishedAt(OffsetDateTime.now());
        attempt.setUpdatedAt(OffsetDateTime.now());
        attemptRepo.save(attempt);
    }

    private void recalcRun(String runId) {
        runRepo.findById(runId).ifPresent(run -> {
            com.agentspace.orchestration.model.RunStatus before = run.getStatus();
            com.agentspace.orchestration.model.RunStatus after = runRecalc.recalculate(run);
            // run 状态变更回流 Agent-Management（控制类，始终入 outbox）
            if (after != before
                    && (after == com.agentspace.orchestration.model.RunStatus.COMPLETED
                        || after == com.agentspace.orchestration.model.RunStatus.FAILED
                        || after == com.agentspace.orchestration.model.RunStatus.SUSPENDED)) {
                outboxService.enqueueStateChange(runId, null, null,
                        "run." + after.name().toLowerCase(),
                        java.util.Map.of("runId", runId, "status", after.name()));
            }
        });
    }

    private void touch(WorkflowStep step) {
        step.setUpdatedAt(OffsetDateTime.now());
        stepRepo.save(step);
    }
}
