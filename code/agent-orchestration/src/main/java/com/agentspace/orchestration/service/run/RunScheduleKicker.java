package com.agentspace.orchestration.service.run;

import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.repository.WorkflowStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Comparator;
import java.util.List;

/**
 * 直驱快路径：消费 {@link RunProgressedEvent}，在发布事务提交后立即重扫该 run 的 READY step 并启动，
 * 让流程无需等 {@link com.agentspace.orchestration.scheduler.StepScheduler} 下一轮轮询。见 README §2.5。
 *
 * <p>关键时序：{@code @TransactionalEventListener(AFTER_COMMIT)} + {@code @Async}——
 * <ul>
 *   <li><b>AFTER_COMMIT</b>：发布事件的事务（step 终态写入）已提交，kicker 读到的是已落库的 READY 状态；</li>
 *   <li><b>@Async</b>：在独立线程、由 {@link SchedulingService#tryLaunchReadyStep} 开新事务执行，
 *       使 {@link StepLauncher} 对 Agent Core 的<b>同步</b>调用不占用任何既有 DB 事务（见 README §11#1）。</li>
 * </ul>
 *
 * <p>并发与幂等：{@code tryLaunchReadyStep} 内含「仍 READY?」「同 run 无 RUNNING（串行单飞）?」校验 + CAS，
 * 故直驱与兜底轮询、多副本并发抢同一 step 都安全——只有一个 CAS 命中。串行单飞下本方法按 order_index
 * 升序尝试、启动到第一个成功即止；后续 step 由它完成时再次发出的 {@link RunProgressedEvent} 接力。
 *
 * <p>容错：直驱是「快」而非「可靠」保证。kicker 抛异常/副本崩溃/丢事件时，READY step 由轮询 sweeper 兜底，
 * 「不管怎么变成 READY，最终一定有人尝试启动」这一 liveness 由轮询保证，不依赖本类。
 */
@Component
public class RunScheduleKicker {

    private static final Logger log = LoggerFactory.getLogger(RunScheduleKicker.class);

    private final WorkflowStepRepository stepRepo;
    private final SchedulingService schedulingService;

    public RunScheduleKicker(WorkflowStepRepository stepRepo, SchedulingService schedulingService) {
        this.stepRepo = stepRepo;
        this.schedulingService = schedulingService;
    }

    /**
     * 事务提交后异步触发：扫描该 run 的 READY step（按 order_index 升序），逐个尝试启动。
     * 串行单飞下启到第一个成功即可——下一个由其完成事件再次驱动。
     */
    @Async("scheduleKickerExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunProgressed(RunProgressedEvent event) {
        try {
            List<WorkflowStep> ready = stepRepo.findByRunIdAndStatus(event.runId(), StepStatus.READY).stream()
                    .sorted(Comparator.comparingInt(WorkflowStep::getOrderIndex))
                    .toList();
            for (WorkflowStep step : ready) {
                if (schedulingService.tryLaunchReadyStep(step.getId())) {
                    log.debug("直驱启动 run {} step {}", event.runId(), step.getStepKey());
                    return; // 串行单飞：启动一个即止，余下由后续 RunProgressedEvent 接力
                }
            }
        } catch (Exception e) {
            // 直驱失败不影响正确性：READY step 由轮询 sweeper 兜底。仅记日志。
            log.warn("直驱启动 run {} 失败（由轮询兜底）：{}", event.runId(), e.getMessage());
        }
    }
}
