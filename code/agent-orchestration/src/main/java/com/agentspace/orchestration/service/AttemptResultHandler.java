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
        this.metrics = metrics;
    }

    /**
     * attempt 成功：写 output。requiresConfirmation=false → step COMPLETED + 触发下游；
     * =true → step SUSPENDED（FE6 深化 confirm/continue）。
     */
    @Transactional
    public void onAttemptSucceeded(String attemptId, String summary, String result,
                                   String sessionRef, String artifactRefsJson) {
        StepAttempt attempt = attemptRepo.findById(attemptId).orElseThrow();
        if (StateTransitions.isTerminal(attempt.getStatus())) {
            log.warn("attempt {} 已终态 {}，忽略重复成功事件", attemptId, attempt.getStatus());
            return;
        }
        markAttemptTerminal(attempt, AttemptStatus.SUCCEEDED, null, null);

        WorkflowStep step = stepRepo.findById(attempt.getStepId()).orElseThrow();
        step.setOutputSummary(summary);
        step.setOutputResult(result);
        step.setOutputArtifactRefs(artifactRefsJson);
        step.setSessionRef(sessionRef);

        if (step.isRequiresConfirmation()) {
            step.setStatus(StepStatus.SUSPENDED);
            touch(step);
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

    /**
     * attempt 被取消（收到 runtime.cancelled）：attempt → CANCELLED，step → CANCELLED（不重试），run 重算。
     * 见详细设计 §8.4（runtime.cancelled 视为 cancelled）。
     */
    @Transactional
    public void onAttemptCancelled(String attemptId) {
        StepAttempt attempt = attemptRepo.findById(attemptId).orElseThrow();
        if (StateTransitions.isTerminal(attempt.getStatus())) {
            log.warn("attempt {} 已终态 {}，忽略 cancelled 事件", attemptId, attempt.getStatus());
            return;
        }
        markAttemptTerminal(attempt, AttemptStatus.CANCELLED, null, null);

        WorkflowStep step = stepRepo.findById(attempt.getStepId()).orElseThrow();
        if (step.getStatus() != StepStatus.COMPLETED && step.getStatus() != StepStatus.CANCELLED
                && step.getStatus() != StepStatus.FAILED) {
            step.setStatus(StepStatus.CANCELLED);
            step.setFinishedAt(OffsetDateTime.now());
            touch(step);
        }
        recalcRun(attempt.getRunId());
        log.info("attempt {} 收到 runtime.cancelled → CANCELLED", attemptId);
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
        // run 状态由 step 聚合驱动；Agent-Management 通过 GET /runs/{id} 主动查询终态（单存储，不回流）。
        runRepo.findById(runId).ifPresent(runRecalc::recalculate);
    }

    private void touch(WorkflowStep step) {
        step.setUpdatedAt(OffsetDateTime.now());
        stepRepo.save(step);
    }
}
