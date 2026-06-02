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
    private final AgentFlowCodec codec;
    private final OrchestrationProperties props;

    public AttemptResultHandler(WorkflowRunRepository runRepo,
                                WorkflowStepRepository stepRepo,
                                StepAttemptRepository attemptRepo,
                                StepDependencyRepository depRepo,
                                RunStateRecalculator runRecalc,
                                StepLauncher stepLauncher,
                                AgentFlowCodec codec,
                                OrchestrationProperties props) {
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
        this.attemptRepo = attemptRepo;
        this.depRepo = depRepo;
        this.runRecalc = runRecalc;
        this.stepLauncher = stepLauncher;
        this.codec = codec;
        this.props = props;
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
            log.info("step {} 成功且需确认 → SUSPENDED", step.getStepKey());
        } else {
            step.setStatus(StepStatus.COMPLETED);
            step.setFinishedAt(OffsetDateTime.now());
            touch(step);
            triggerDownstream(step);
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

    /** 下游 step 的全部上游都 COMPLETED → 置 READY。 */
    private void triggerDownstream(WorkflowStep completedStep) {
        String runId = completedStep.getRunId();
        List<StepDependency> downstream = depRepo.findByRunIdAndFromStepKey(runId, completedStep.getStepKey());
        for (StepDependency dep : downstream) {
            stepRepo.findByRunIdAndStepKey(runId, dep.getToStepKey()).ifPresent(target -> {
                if (target.getStatus() == StepStatus.PENDING && allUpstreamCompleted(runId, target.getStepKey())) {
                    target.setStatus(StepStatus.READY);
                    touch(target);
                    log.info("step {} 全部上游完成 → READY", target.getStepKey());
                }
            });
        }
    }

    private boolean allUpstreamCompleted(String runId, String stepKey) {
        List<StepDependency> upstream = depRepo.findByRunIdAndToStepKey(runId, stepKey);
        for (StepDependency dep : upstream) {
            WorkflowStep from = stepRepo.findByRunIdAndStepKey(runId, dep.getFromStepKey()).orElse(null);
            if (from == null || from.getStatus() != StepStatus.COMPLETED) {
                return false;
            }
        }
        return true;
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
        runRepo.findById(runId).ifPresent(runRecalc::recalculate);
    }

    private void touch(WorkflowStep step) {
        step.setUpdatedAt(OffsetDateTime.now());
        stepRepo.save(step);
    }
}
