package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.repository.WorkflowRunRepository;
import com.agentspace.orchestration.repository.WorkflowStepRepository;
import com.agentspace.orchestration.service.statemachine.StateTransitions;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 由 step 状态聚合驱动 run 状态。见详细设计 §3.3。
 *
 * <ul>
 *   <li>有 step SUSPENDED → run SUSPENDED；</li>
 *   <li>否则有 step RUNNING/READY/PENDING → run RUNNING；</li>
 *   <li>全 COMPLETED → run COMPLETED；</li>
 *   <li>有 step FAILED 且无可继续 → run FAILED。</li>
 * </ul>
 */
@Component
public class RunStateRecalculator {

    private final WorkflowStepRepository stepRepo;
    private final WorkflowRunRepository runRepo;

    public RunStateRecalculator(WorkflowStepRepository stepRepo, WorkflowRunRepository runRepo) {
        this.stepRepo = stepRepo;
        this.runRepo = runRepo;
    }

    /**
     * 重算并持久化 run 状态。返回新状态。已处于终态的 run 不再变更。
     */
    public RunStatus recalculate(WorkflowRun run) {
        if (StateTransitions.isTerminal(run.getStatus())) {
            return run.getStatus();
        }
        List<WorkflowStep> steps = stepRepo.findByRunId(run.getId());

        boolean anySuspended = steps.stream().anyMatch(s -> s.getStatus() == StepStatus.SUSPENDED);
        // 可推进：正在跑或就绪。PENDING 不计入——其上游若已失败则永不就绪（串行死节点）。
        boolean anyProgressable = steps.stream().anyMatch(s ->
                s.getStatus() == StepStatus.RUNNING || s.getStatus() == StepStatus.READY);
        boolean anyActive = anyProgressable
                || steps.stream().anyMatch(s -> s.getStatus() == StepStatus.PENDING);
        boolean anyFailed = steps.stream().anyMatch(s -> s.getStatus() == StepStatus.FAILED);
        boolean allCompleted = !steps.isEmpty()
                && steps.stream().allMatch(s -> s.getStatus() == StepStatus.COMPLETED);

        RunStatus next;
        if (anySuspended) {
            next = RunStatus.SUSPENDED;
        } else if (anyFailed && !anyProgressable) {
            // 有失败且无正在跑/就绪的 step → 剩余 PENDING 被失败阻断，run 失败
            next = RunStatus.FAILED;
        } else if (allCompleted) {
            next = RunStatus.COMPLETED;
        } else if (anyActive) {
            next = RunStatus.RUNNING;
        } else {
            next = run.getStatus();
        }

        if (next != run.getStatus()) {
            run.setStatus(next);
            run.setUpdatedAt(OffsetDateTime.now());
            if (next == RunStatus.COMPLETED || next == RunStatus.FAILED) {
                run.setFinishedAt(OffsetDateTime.now());
            }
            runRepo.save(run);
        }
        return next;
    }
}
