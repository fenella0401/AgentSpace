package com.agentspace.orchestration.service;

import com.agentspace.orchestration.client.AgentCoreClient;
import com.agentspace.orchestration.client.dto.QueryAttemptResponse;
import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.repository.StepAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 重启恢复 reconcile（FE7）：进程重启后扫 in-flight attempt，调 Agent Core QueryAttempt 对齐。
 * 见详细设计 §3.5、概要设计 §11#1。
 *
 * <p>Agent Core 报终态 → 推进（成功/失败）；仍在跑 → 留给 watchdog 与后续事件；查询失败 → 跳过，
 * 由 watchdog 兜底。push 为主、query 为辅。
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final StepAttemptRepository attemptRepo;
    private final AttemptResultHandler resultHandler;
    private final ObjectProvider<AgentCoreClient> agentCoreClientProvider;

    public ReconciliationService(StepAttemptRepository attemptRepo,
                                 AttemptResultHandler resultHandler,
                                 ObjectProvider<AgentCoreClient> agentCoreClientProvider) {
        this.attemptRepo = attemptRepo;
        this.resultHandler = resultHandler;
        this.agentCoreClientProvider = agentCoreClientProvider;
    }

    /**
     * 对齐所有 in-flight attempt。返回处理条数。
     */
    @Transactional
    public int reconcileInFlight() {
        List<StepAttempt> inFlight = attemptRepo.findAllInFlight();
        if (inFlight.isEmpty()) {
            return 0;
        }
        log.info("reconcile：发现 {} 个 in-flight attempt，开始对齐", inFlight.size());
        int aligned = 0;
        for (StepAttempt attempt : inFlight) {
            try {
                QueryAttemptResponse resp = agentCoreClientProvider.getObject().queryAttempt(attempt.getId());
                if (resp == null) {
                    continue;
                }
                if (resp.status() == AttemptStatus.SUCCEEDED) {
                    resultHandler.onAttemptSucceeded(attempt.getId(), "reconciled", null, null, null);
                    aligned++;
                } else if (resp.status() == AttemptStatus.FAILED) {
                    resultHandler.onAttemptFailed(attempt.getId(),
                            resp.failureReason() != null ? resp.failureReason() : "UNKNOWN",
                            "reconciled failed");
                    aligned++;
                }
                // RUNNING/STARTING：仍在跑，留给 watchdog 与后续事件
            } catch (Exception e) {
                log.warn("reconcile attempt {} 查询失败，留给 watchdog 兜底：{}", attempt.getId(), e.getMessage());
            }
        }
        log.info("reconcile 完成，对齐 {} 个 attempt", aligned);
        return aligned;
    }
}
