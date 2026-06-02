package com.agentspace.orchestration.repository;

import com.agentspace.orchestration.model.entity.WorkflowEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * workflow_event 持久化。控制类全量留存，展示类按需。
 */
public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, String> {

    List<WorkflowEvent> findByRunIdOrderBySequenceNoAsc(String runId);
}
