package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.repository.WorkflowRunRepository;
import com.agentspace.orchestration.repository.WorkflowStepRepository;
import com.agentspace.orchestration.service.statemachine.StateTransitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * run 取消（FE6 标记 + 竞态；级联删 in-flight attempt 在 FE7）。见详细设计 §2.2、§9.3、§3.4。
 *
 * <p>幂等：已终态 run 取消为 no-op，返回当前状态。进入 CANCELLING 后，confirm/continue 被
 * {@link SuspendResumeService} 拒绝。无 in-flight attempt 时直接转 CANCELLED。
 */
@Service
public class RunCancellationService {

    private static final Logger log = LoggerFactory.getLogger(RunCancellationService.class);

    private final WorkflowRunRepository runRepo;
    private final WorkflowStepRepository stepRepo;

    public RunCancellationService(WorkflowRunRepository runRepo, WorkflowStepRepository stepRepo) {
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
    }

    /**
     * 取消 run。非终态 → CANCELLING；无 RUNNING step（无 in-flight）则直接 CANCELLED；
     * 已终态 → no-op 返回当前状态。
     */
    @Transactional
    public WorkflowRun cancel(String runId) {
        WorkflowRun run = runRepo.findById(runId)
                .orElseThrow(() -> new StepActionNotFoundException("run 不存在: " + runId));

        if (StateTransitions.isTerminal(run.getStatus())) {
            return run; // no-op
        }
        if (run.getStatus() == RunStatus.CANCELLING) {
            return run; // 幂等
        }

        run.setStatus(RunStatus.CANCELLING);
        run.setUpdatedAt(OffsetDateTime.now());
        runRepo.save(run);

        // 取消非终态 step（FE7 将对 RUNNING step 的 in-flight attempt 调 Agent Core CancelAttempt）
        List<WorkflowStep> steps = stepRepo.findByRunId(runId);
        boolean hasInFlight = false;
        for (WorkflowStep step : steps) {
            if (step.getStatus() == StepStatus.RUNNING) {
                hasInFlight = true;
            }
            if (step.getStatus() != StepStatus.COMPLETED && step.getStatus() != StepStatus.CANCELLED
                    && step.getStatus() != StepStatus.FAILED) {
                step.setStatus(StepStatus.CANCELLED);
                step.setUpdatedAt(OffsetDateTime.now());
                stepRepo.save(step);
            }
        }

        if (!hasInFlight) {
            run.setStatus(RunStatus.CANCELLED);
            run.setFinishedAt(OffsetDateTime.now());
            run.setUpdatedAt(OffsetDateTime.now());
            runRepo.save(run);
            log.info("run {} 无 in-flight，直接 CANCELLED", runId);
        } else {
            log.info("run {} → CANCELLING（in-flight attempt 级联取消在 FE7）", runId);
        }
        return run;
    }
}
