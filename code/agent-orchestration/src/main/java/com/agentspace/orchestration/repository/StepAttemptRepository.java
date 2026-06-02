package com.agentspace.orchestration.repository;

import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.entity.StepAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * step_attempt 持久化。(step_id, attempt_no) 唯一约束配合 CAS 实现 attempt 创建幂等。
 */
public interface StepAttemptRepository extends JpaRepository<StepAttempt, String> {

    List<StepAttempt> findByStepId(String stepId);

    Optional<StepAttempt> findByStepIdAndAttemptNo(String stepId, int attemptNo);

    /** run 内 in-flight（STARTING/RUNNING）attempt，用于取消级联与重启恢复。 */
    @Query("select a from StepAttempt a where a.runId = :runId and a.status in "
            + "(com.agentspace.orchestration.model.AttemptStatus.STARTING, "
            + "com.agentspace.orchestration.model.AttemptStatus.RUNNING)")
    List<StepAttempt> findByRunStatusInFlight(@Param("runId") String runId);

    /** 全局 in-flight attempt（重启恢复 reconcile 用）。 */
    @Query("select a from StepAttempt a where a.status in "
            + "(com.agentspace.orchestration.model.AttemptStatus.STARTING, "
            + "com.agentspace.orchestration.model.AttemptStatus.RUNNING)")
    List<StepAttempt> findAllInFlight();

    /** RUNNING 且心跳早于阈值的 attempt（watchdog 心跳丢失扫描）。 */
    List<StepAttempt> findByStatusAndLastHeartbeatAtBefore(AttemptStatus status, OffsetDateTime before);

    /** RUNNING 且启动早于阈值的 attempt（watchdog 硬超时扫描）。 */
    List<StepAttempt> findByStatusAndStartedAtBefore(AttemptStatus status, OffsetDateTime before);

    /** STARTING 且创建早于阈值的 attempt（watchdog 扫描启动卡死：StartAttempt 中断未确认）。 */
    List<StepAttempt> findByStatusAndCreatedAtBefore(AttemptStatus status, OffsetDateTime before);
}

