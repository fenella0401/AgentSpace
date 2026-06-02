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

import java.time.OffsetDateTime;
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
            if (reconcileOne(attempt.getId())) {
                aligned++;
            }
        }
        log.info("reconcile 完成，对齐 {} 个 attempt", aligned);
        return aligned;
    }

    /**
     * 对齐单个 attempt（供重启 reconcile 与 watchdog 启动卡死处理复用）。
     * 返回 true 表示已推进/对齐其状态。见详细设计 §3.5、§8.4。
     *
     * <ul>
     *   <li>Agent Core 未找到（StartAttempt 未送达）→ RUNTIME_CREATE_FAILED（走 step 重试）；</li>
     *   <li>终态 SUCCEEDED/FAILED → 推进；</li>
     *   <li>Agent Core 在跑但本地仍 STARTING（响应丢失）→ 对齐成 RUNNING，补 runtimeAttemptRef；</li>
     *   <li>查询失败 → 返回 false，留 watchdog 兜底。</li>
     * </ul>
     */
    @Transactional
    public boolean reconcileOne(String attemptId) {
        StepAttempt attempt = attemptRepo.findById(attemptId).orElse(null);
        if (attempt == null) {
            return false;
        }
        try {
            QueryAttemptResponse resp = agentCoreClientProvider.getObject().queryAttempt(attemptId);
            if (resp == null) {
                return false;
            }
            if (!resp.found()) {
                resultHandler.onAttemptFailed(attemptId, "RUNTIME_CREATE_FAILED",
                        "reconcile: Agent Core 未找到该 attempt（StartAttempt 未送达）");
                return true;
            }
            if (resp.status() == AttemptStatus.SUCCEEDED) {
                resultHandler.onAttemptSucceeded(attemptId, "reconciled", null, null, null);
                return true;
            }
            if (resp.status() == AttemptStatus.FAILED) {
                resultHandler.onAttemptFailed(attemptId,
                        resp.failureReason() != null ? resp.failureReason() : "UNKNOWN",
                        "reconciled failed");
                return true;
            }
            if (resp.status() == AttemptStatus.RUNNING && attempt.getStatus() == AttemptStatus.STARTING) {
                // Agent Core 在跑但本地仍 STARTING（StartAttempt 响应丢失）：补对齐成 RUNNING，留事件/watchdog
                attempt.setStatus(AttemptStatus.RUNNING);
                if (attempt.getRuntimeAttemptRef() == null) {
                    attempt.setRuntimeAttemptRef(resp.runtimeAttemptRef());
                }
                if (attempt.getStartedAt() == null) {
                    attempt.setStartedAt(OffsetDateTime.now());
                }
                attempt.setUpdatedAt(OffsetDateTime.now());
                attemptRepo.save(attempt);
                return true;
            }
            // 其余 RUNNING/STARTING：仍在跑，留给 watchdog 与后续事件
            return false;
        } catch (Exception e) {
            log.warn("reconcile attempt {} 查询失败，留给 watchdog 兜底：{}", attemptId, e.getMessage());
            return false;
        }
    }
}
