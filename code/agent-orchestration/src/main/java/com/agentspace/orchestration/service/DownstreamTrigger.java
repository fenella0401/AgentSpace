package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.StepDependency;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.repository.StepDependencyRepository;
import com.agentspace.orchestration.repository.WorkflowStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 下游 ready 触发：一个 step 完成后，将「全部上游已 COMPLETED」的下游 PENDING step 置 READY。
 * 供 {@link AttemptResultHandler}（attempt 成功）与 {@link SuspendResumeService}（confirm）复用。
 * 见详细设计 §3.2、概要设计 §7.3。
 */
@Component
public class DownstreamTrigger {

    private static final Logger log = LoggerFactory.getLogger(DownstreamTrigger.class);

    private final WorkflowStepRepository stepRepo;
    private final StepDependencyRepository depRepo;

    public DownstreamTrigger(WorkflowStepRepository stepRepo, StepDependencyRepository depRepo) {
        this.stepRepo = stepRepo;
        this.depRepo = depRepo;
    }

    /** 触发 completedStep 的下游：全部上游 COMPLETED 的 PENDING 下游 → READY。 */
    public void triggerDownstream(WorkflowStep completedStep) {
        String runId = completedStep.getRunId();
        List<StepDependency> downstream = depRepo.findByRunIdAndFromStepKey(runId, completedStep.getStepKey());
        for (StepDependency dep : downstream) {
            stepRepo.findByRunIdAndStepKey(runId, dep.getToStepKey()).ifPresent(target -> {
                if (target.getStatus() == StepStatus.PENDING && allUpstreamCompleted(runId, target.getStepKey())) {
                    target.setStatus(StepStatus.READY);
                    target.setUpdatedAt(OffsetDateTime.now());
                    stepRepo.save(target);
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
}
