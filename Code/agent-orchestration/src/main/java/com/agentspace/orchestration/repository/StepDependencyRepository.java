package com.agentspace.orchestration.repository;

import com.agentspace.orchestration.model.entity.StepDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * step_dependency 持久化。用于算 ready（查上游）与下游触发。
 */
public interface StepDependencyRepository extends JpaRepository<StepDependency, String> {

    List<StepDependency> findByRunId(String runId);

    List<StepDependency> findByRunIdAndToStepKey(String runId, String toStepKey);

    List<StepDependency> findByRunIdAndFromStepKey(String runId, String fromStepKey);
}
