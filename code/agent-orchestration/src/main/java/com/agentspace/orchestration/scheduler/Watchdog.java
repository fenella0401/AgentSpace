package com.agentspace.orchestration.scheduler;

import com.agentspace.orchestration.client.AgentCoreClient;
import com.agentspace.orchestration.client.dto.CancelAttemptRequest;
import com.agentspace.orchestration.config.OrchestrationProperties;
import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.ExecutorType;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.repository.StepAttemptRepository;
import com.agentspace.orchestration.repository.WorkflowStepRepository;
import com.agentspace.orchestration.service.run.AttemptResultHandler;
import com.agentspace.orchestration.service.run.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * watchdog（FE7）：扫描 RUNNING attempt 的硬超时与心跳丢失，命中则调 Agent Core CancelAttempt
 * 并判失败（走 step 重试/失败逻辑）。见详细设计 §3.5。
 *
 * <p>CAS 仲裁：失败推进复用 {@link AttemptResultHandler#onAttemptFailed}，其对已终态 attempt
 * 幂等忽略，与正常 result 事件并发安全。
 */
@Component
public class Watchdog {

    private static final Logger log = LoggerFactory.getLogger(Watchdog.class);

    private final StepAttemptRepository attemptRepo;
    private final WorkflowStepRepository stepRepo;
    private final OrchestrationProperties props;
    private final AttemptResultHandler resultHandler;
    private final ReconciliationService reconciliationService;
    private final ObjectProvider<AgentCoreClient> agentCoreClientProvider;

    public Watchdog(StepAttemptRepository attemptRepo,
                    WorkflowStepRepository stepRepo,
                    OrchestrationProperties props,
                    AttemptResultHandler resultHandler,
                    ReconciliationService reconciliationService,
                    ObjectProvider<AgentCoreClient> agentCoreClientProvider) {
        this.attemptRepo = attemptRepo;
        this.stepRepo = stepRepo;
        this.props = props;
        this.resultHandler = resultHandler;
        this.reconciliationService = reconciliationService;
        this.agentCoreClientProvider = agentCoreClientProvider;
    }

    @Scheduled(fixedDelayString = "${orchestration.watchdog.poll-interval-ms:5000}")
    public void scan() {
        OffsetDateTime now = OffsetDateTime.now();
        Set<StepAttempt> stale = new LinkedHashSet<>();

        // 按全局默认阈值粗筛（per-executorType 阈值在逐条判定时再精确比较）
        stale.addAll(attemptRepo.findByStatusAndLastHeartbeatAtBefore(
                AttemptStatus.RUNNING, now.minus(props.heartbeatTimeout())));
        stale.addAll(attemptRepo.findByStatusAndStartedAtBefore(
                AttemptStatus.RUNNING, now.minus(props.stepTimeout())));

        for (StepAttempt attempt : stale) {
            handleStale(attempt, now);
        }

        // STARTING 卡死（StartAttempt 中断未确认）：超 startTimeout 调 reconcile 对齐（query found? → 失败重试/对齐 RUNNING）
        for (StepAttempt attempt : attemptRepo.findByStatusAndCreatedAtBefore(
                AttemptStatus.STARTING, now.minus(props.startTimeout()))) {
            log.warn("watchdog 命中卡死 STARTING attempt {}，触发 reconcile 对齐", attempt.getId());
            reconciliationService.reconcileOne(attempt.getId());
        }
    }

    private void handleStale(StepAttempt attempt, OffsetDateTime now) {
        WorkflowStep step = stepRepo.findById(attempt.getStepId()).orElse(null);
        ExecutorType executor = step == null ? null : step.getExecutorType();

        boolean heartbeatLost = attempt.getLastHeartbeatAt() != null
                && attempt.getLastHeartbeatAt().isBefore(now.minus(heartbeatTimeout(executor)));
        boolean hardTimeout = attempt.getStartedAt() != null
                && attempt.getStartedAt().isBefore(now.minus(stepTimeout(executor)));

        if (!heartbeatLost && !hardTimeout) {
            return; // 粗筛命中但精确阈值未到（per-executor 覆盖更宽松）
        }

        String reason = hardTimeout ? "TIMEOUT" : "HEARTBEAT_LOST";
        try {
            agentCoreClientProvider.getObject().cancelAttempt(new CancelAttemptRequest(attempt.getId(), true));
        } catch (Exception e) {
            log.warn("watchdog 取消 attempt {} 失败（继续判失败）：{}", attempt.getId(), e.getMessage());
        }
        log.warn("watchdog 命中 attempt {} reason={}", attempt.getId(), reason);
        resultHandler.onAttemptFailed(attempt.getId(), reason, "watchdog: " + reason);
    }

    private java.time.Duration heartbeatTimeout(ExecutorType e) {
        return e == null ? props.heartbeatTimeout() : props.heartbeatTimeoutFor(e);
    }

    private java.time.Duration stepTimeout(ExecutorType e) {
        return e == null ? props.stepTimeout() : props.stepTimeoutFor(e);
    }
}
