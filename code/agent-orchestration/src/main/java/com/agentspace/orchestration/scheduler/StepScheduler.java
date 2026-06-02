package com.agentspace.orchestration.scheduler;

import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.service.SchedulingService;
import com.agentspace.orchestration.repository.WorkflowStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 调度器：定时轮询 READY step 并启动（MVP 串行单飞）。见概要设计 §2.3、§7.3。
 *
 * <p>多副本：openGauss 用 {@code FOR UPDATE SKIP LOCKED} 减少竞争，CAS（@Version）保证
 * 正确性——两个副本同时抢同一 step 时只有一个 CAS 成功，另一个回滚重试。本类只做触发，
 * 启动与并发仲裁逻辑在 {@link SchedulingService}。
 */
@Component
public class StepScheduler {

    private static final Logger log = LoggerFactory.getLogger(StepScheduler.class);

    private final WorkflowStepRepository stepRepo;
    private final SchedulingService schedulingService;

    public StepScheduler(WorkflowStepRepository stepRepo, SchedulingService schedulingService) {
        this.stepRepo = stepRepo;
        this.schedulingService = schedulingService;
    }

    /** 轮询间隔由 orchestration.scheduler-poll-interval-ms 配置（默认 2000ms）。 */
    @Scheduled(fixedDelayString = "${orchestration.scheduler-poll-interval-ms:2000}")
    public void pollAndSchedule() {
        List<WorkflowStep> readySteps = stepRepo.findAll().stream()
                .filter(s -> s.getStatus() == StepStatus.READY)
                .toList();
        for (WorkflowStep step : readySteps) {
            try {
                schedulingService.tryLaunchReadyStep(step.getId());
            } catch (Exception e) {
                log.debug("调度 step {} 跳过: {}", step.getId(), e.getMessage());
            }
        }
    }
}
