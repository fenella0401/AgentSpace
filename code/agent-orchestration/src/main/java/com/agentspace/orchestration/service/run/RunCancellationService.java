package com.agentspace.orchestration.service.run;

import com.agentspace.orchestration.client.AgentCoreClient;
import com.agentspace.orchestration.client.dto.CancelAttemptRequest;
import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.repository.StepAttemptRepository;
import com.agentspace.orchestration.repository.WorkflowRunRepository;
import com.agentspace.orchestration.repository.WorkflowStepRepository;
import com.agentspace.orchestration.service.statemachine.StateTransitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import com.agentspace.orchestration.service.exception.StepActionNotFoundException;

/**
 * run 取消与级联（FE6 标记 + FE7 级联删 in-flight attempt）。见详细设计 §2.2、§9.3、§3.4。
 *
 * <p>幂等：已终态 no-op。进入 CANCELLING 后 confirm/continue 被拒（§3.4）。对 in-flight attempt
 * 调 Agent Core CancelAttempt，attempt→CANCELLED，最终 run→CANCELLED。
 * lease 释放归 Agent-Management（§11#6），此处仅释放引用、不删卷。
 */
@Service
public class RunCancellationService {

    private static final Logger log = LoggerFactory.getLogger(RunCancellationService.class);

    private final WorkflowRunRepository runRepo;
    private final WorkflowStepRepository stepRepo;
    private final StepAttemptRepository attemptRepo;
    private final ObjectProvider<AgentCoreClient> agentCoreClientProvider;

    public RunCancellationService(WorkflowRunRepository runRepo,
                                  WorkflowStepRepository stepRepo,
                                  StepAttemptRepository attemptRepo,
                                  ObjectProvider<AgentCoreClient> agentCoreClientProvider) {
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
        this.attemptRepo = attemptRepo;
        this.agentCoreClientProvider = agentCoreClientProvider;
    }

    /**
     * 取消 run。非终态 → CANCELLING → 级联取消 in-flight attempt → CANCELLED；已终态 no-op。
     */
    @Transactional
    public WorkflowRun cancel(String runId) {
        WorkflowRun run = runRepo.findById(runId)
                .orElseThrow(() -> new StepActionNotFoundException("run 不存在: " + runId));

        if (StateTransitions.isTerminal(run.getStatus()) || run.getStatus() == RunStatus.CANCELLING) {
            return run; // no-op / 幂等
        }

        run.setStatus(RunStatus.CANCELLING);
        run.setUpdatedAt(OffsetDateTime.now());
        runRepo.save(run);

        // 取消 in-flight attempt（STARTING/RUNNING）
        for (StepAttempt attempt : attemptRepo.findByRunStatusInFlight(runId)) {
            try {
                agentCoreClientProvider.getObject().cancelAttempt(
                        new CancelAttemptRequest(attempt.getId(), true));
            } catch (Exception e) {
                log.warn("取消 attempt {} 时 Agent Core 调用失败（继续）：{}", attempt.getId(), e.getMessage());
            }
            attempt.setStatus(AttemptStatus.CANCELLED);
            attempt.setFinishedAt(OffsetDateTime.now());
            attempt.setUpdatedAt(OffsetDateTime.now());
            attemptRepo.save(attempt);
        }

        // 非终态 step → CANCELLED
        for (WorkflowStep step : stepRepo.findByRunId(runId)) {
            if (step.getStatus() != StepStatus.COMPLETED && step.getStatus() != StepStatus.CANCELLED
                    && step.getStatus() != StepStatus.FAILED) {
                step.setStatus(StepStatus.CANCELLED);
                step.setUpdatedAt(OffsetDateTime.now());
                stepRepo.save(step);
            }
        }

        run.setStatus(RunStatus.CANCELLED);
        run.setFinishedAt(OffsetDateTime.now());
        run.setUpdatedAt(OffsetDateTime.now());
        runRepo.save(run);
        log.info("run {} → CANCELLED（级联取消 in-flight attempt 完成）", runId);
        return run;
    }
}
