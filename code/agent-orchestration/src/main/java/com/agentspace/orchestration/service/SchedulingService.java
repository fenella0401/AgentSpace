package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.AttemptTrigger;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.model.flow.AgentFlow;
import com.agentspace.orchestration.repository.WorkflowRunRepository;
import com.agentspace.orchestration.repository.WorkflowStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 调度决策：对一个 READY step 判断是否可启动（MVP 串行单飞：同 run 任意时刻最多一个 RUNNING），
 * 可启动则委托 {@link StepLauncher}。见概要设计 §7.3。
 *
 * <p>并发正确性靠 CAS：launcher 内 step READY→RUNNING 的 save 带 @Version，
 * 多副本/多线程同时抢同一 step 只有一个成功。
 */
@Service
public class SchedulingService {

    private static final Logger log = LoggerFactory.getLogger(SchedulingService.class);

    private final WorkflowRunRepository runRepo;
    private final WorkflowStepRepository stepRepo;
    private final AgentFlowCodec codec;
    private final StepLauncher stepLauncher;

    public SchedulingService(WorkflowRunRepository runRepo,
                             WorkflowStepRepository stepRepo,
                             AgentFlowCodec codec,
                             StepLauncher stepLauncher) {
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
        this.codec = codec;
        this.stepLauncher = stepLauncher;
    }

    /**
     * 尝试启动一个 READY step。若 step 已非 READY，或同 run 已有 RUNNING step（串行），则跳过。
     *
     * @return true 表示本次成功启动
     */
    @Transactional
    public boolean tryLaunchReadyStep(String stepId) {
        WorkflowStep step = stepRepo.findById(stepId).orElse(null);
        if (step == null || step.getStatus() != StepStatus.READY) {
            return false;
        }
        // 串行单飞：同 run 已有 RUNNING step 则不启动
        long running = stepRepo.countByRunIdAndStatus(step.getRunId(), StepStatus.RUNNING);
        if (running > 0) {
            return false;
        }
        WorkflowRun run = runRepo.findById(step.getRunId()).orElse(null);
        if (run == null) {
            return false;
        }
        AgentFlow flow = codec.fromJson(run.getAgentFlowJson());
        stepLauncher.launch(run, step, flow, AttemptTrigger.INITIAL, null, null);
        log.info("调度启动 step {} (run {})", step.getStepKey(), run.getId());
        return true;
    }
}
