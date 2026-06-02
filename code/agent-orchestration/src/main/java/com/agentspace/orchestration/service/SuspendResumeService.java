package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.AttemptTrigger;
import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.model.flow.AgentFlow;
import com.agentspace.orchestration.repository.WorkflowRunRepository;
import com.agentspace.orchestration.repository.WorkflowStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * suspended step 操作（FE6）：confirm / continue / retry。见详细设计 §2.3–2.5、概要设计 §9.4、§10.3。
 *
 * <p>竞态：run 进入 CANCELLING/终态后，confirm/continue 一律拒绝（{@link StepActionConflictException} → 409）。
 * 幂等：基于 step 状态 CAS——并发操作只有一个能把 step 转出 SUSPENDED/FAILED，@Version 兜底。
 */
@Service
public class SuspendResumeService {

    private static final Logger log = LoggerFactory.getLogger(SuspendResumeService.class);

    private final WorkflowRunRepository runRepo;
    private final WorkflowStepRepository stepRepo;
    private final AgentFlowCodec codec;
    private final StepLauncher stepLauncher;
    private final DownstreamTrigger downstreamTrigger;
    private final RunStateRecalculator runRecalc;
    private final OutboxService outboxService;

    public SuspendResumeService(WorkflowRunRepository runRepo,
                                WorkflowStepRepository stepRepo,
                                AgentFlowCodec codec,
                                StepLauncher stepLauncher,
                                DownstreamTrigger downstreamTrigger,
                                RunStateRecalculator runRecalc,
                                OutboxService outboxService) {
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
        this.codec = codec;
        this.stepLauncher = stepLauncher;
        this.downstreamTrigger = downstreamTrigger;
        this.runRecalc = runRecalc;
        this.outboxService = outboxService;
    }

    /**
     * confirm：SUSPENDED step → COMPLETED，触发下游，run 重算。见 §2.3。
     */
    @Transactional
    public WorkflowStep confirm(String runId, String stepId) {
        WorkflowRun run = requireRunNotCancelling(runId);
        WorkflowStep step = requireStep(runId, stepId);
        if (step.getStatus() != StepStatus.SUSPENDED) {
            throw new StepActionConflictException("step 非 SUSPENDED，不能 confirm: " + step.getStatus());
        }
        step.setStatus(StepStatus.COMPLETED);
        step.setFinishedAt(OffsetDateTime.now());
        step.setUpdatedAt(OffsetDateTime.now());
        stepRepo.save(step);

        downstreamTrigger.triggerDownstream(step);
        recalcRun(run);
        log.info("confirm step {} → COMPLETED", step.getStepKey());
        return step;
    }

    /**
     * continue：SUSPENDED step → 起新 CONTINUE attempt（resumeFromSessionRef + feedback），回 RUNNING。见 §2.4。
     */
    @Transactional
    public StepAttempt continueStep(String runId, String stepId, String feedback, String actionKey) {
        if (feedback == null || feedback.isBlank()) {
            throw new StepActionValidationException("continue 的 feedback 不能为空");
        }
        WorkflowRun run = requireRunNotCancelling(runId);
        WorkflowStep step = requireStep(runId, stepId);
        if (step.getStatus() != StepStatus.SUSPENDED) {
            throw new StepActionConflictException("step 非 SUSPENDED，不能 continue: " + step.getStatus());
        }
        // 状态 CAS：转出 SUSPENDED，并发 continue 只有一个成功（@Version 兜底）
        step.setStatus(StepStatus.READY);
        step.setUpdatedAt(OffsetDateTime.now());
        stepRepo.save(step);

        AgentFlow flow = codec.fromJson(run.getAgentFlowJson());
        StepAttempt attempt = stepLauncher.launch(run, step, flow,
                AttemptTrigger.CONTINUE, step.getSessionRef(), feedback);
        log.info("continue step {} → 新 CONTINUE attempt no={}", step.getStepKey(), attempt.getAttemptNo());
        return attempt;
    }

    /**
     * retry：FAILED step → 起新 MANUAL_RETRY attempt。见 §2.5。
     */
    @Transactional
    public StepAttempt retry(String runId, String stepId, boolean resumeSession, String actionKey) {
        WorkflowRun run = requireRunNotCancelling(runId);
        WorkflowStep step = requireStep(runId, stepId);
        if (step.getStatus() != StepStatus.FAILED) {
            throw new StepActionConflictException("step 非 FAILED，不能 retry: " + step.getStatus());
        }
        step.setStatus(StepStatus.READY);
        step.setErrorCode(null);
        step.setErrorMessage(null);
        step.setUpdatedAt(OffsetDateTime.now());
        stepRepo.save(step);

        AgentFlow flow = codec.fromJson(run.getAgentFlowJson());
        String resumeRef = resumeSession ? step.getSessionRef() : null;
        StepAttempt attempt = stepLauncher.launch(run, step, flow,
                AttemptTrigger.MANUAL_RETRY, resumeRef, null);
        recalcRun(run);
        log.info("retry step {} → 新 MANUAL_RETRY attempt no={}", step.getStepKey(), attempt.getAttemptNo());
        return attempt;
    }

    private WorkflowRun requireRun(String runId) {
        return runRepo.findById(runId)
                .orElseThrow(() -> new StepActionNotFoundException("run 不存在: " + runId));
    }

    private WorkflowRun requireRunNotCancelling(String runId) {
        WorkflowRun run = requireRun(runId);
        if (run.getStatus() == RunStatus.CANCELLING
                || com.agentspace.orchestration.service.statemachine.StateTransitions.isTerminal(run.getStatus())) {
            throw new StepActionConflictException("run 处于 " + run.getStatus() + "，拒绝该操作");
        }
        return run;
    }

    private WorkflowStep requireStep(String runId, String stepId) {
        WorkflowStep step = stepRepo.findById(stepId)
                .orElseThrow(() -> new StepActionNotFoundException("step 不存在: " + stepId));
        if (!step.getRunId().equals(runId)) {
            throw new StepActionNotFoundException("step 不属于 run: " + stepId);
        }
        return step;
    }

    private void recalcRun(WorkflowRun run) {
        runRepo.findById(run.getId()).ifPresent(r -> {
            RunStatus before = r.getStatus();
            RunStatus after = runRecalc.recalculate(r);
            if (after != before && (after == RunStatus.COMPLETED || after == RunStatus.FAILED
                    || after == RunStatus.SUSPENDED || after == RunStatus.RUNNING)) {
                outboxService.enqueueStateChange(r.getId(), null, null,
                        "run." + after.name().toLowerCase(),
                        java.util.Map.of("runId", r.getId(), "status", after.name()));
            }
        });
    }
}
