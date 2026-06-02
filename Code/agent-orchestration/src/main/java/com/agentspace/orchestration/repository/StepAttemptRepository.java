package com.agentspace.orchestration.repository;

import com.agentspace.orchestration.model.entity.StepAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * step_attempt 持久化。(step_id, attempt_no) 唯一约束配合 CAS 实现 attempt 创建幂等。
 */
public interface StepAttemptRepository extends JpaRepository<StepAttempt, String> {

    List<StepAttempt> findByStepId(String stepId);

    Optional<StepAttempt> findByStepIdAndAttemptNo(String stepId, int attemptNo);
}
