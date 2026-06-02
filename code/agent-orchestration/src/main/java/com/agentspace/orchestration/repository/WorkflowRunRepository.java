package com.agentspace.orchestration.repository;

import com.agentspace.orchestration.model.entity.WorkflowRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * workflow_run 持久化。idempotency_key 唯一约束实现 POST /runs 幂等。
 */
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, String> {

    Optional<WorkflowRun> findByIdempotencyKey(String idempotencyKey);
}
