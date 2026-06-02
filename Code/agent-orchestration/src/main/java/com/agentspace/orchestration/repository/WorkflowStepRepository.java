package com.agentspace.orchestration.repository;

import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * workflow_step 持久化。
 */
public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, String> {

    List<WorkflowStep> findByRunId(String runId);

    List<WorkflowStep> findByRunIdAndStatus(String runId, StepStatus status);

    Optional<WorkflowStep> findByRunIdAndStepKey(String runId, String stepKey);

    long countByRunIdAndStatus(String runId, StepStatus status);
}
