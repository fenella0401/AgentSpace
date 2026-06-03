package com.agentspace.orchestration.repository;

import com.agentspace.orchestration.model.entity.WorkflowEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * workflow_event 持久化。控制类全量留存，展示类按需。
 */
public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, String> {

    List<WorkflowEvent> findByRunIdOrderBySequenceNoAsc(String runId);

    /**
     * 前端轮询用：取某 run 在 {@code afterSequence} 之后的展示类事件，按 sequenceNo 升序、分页限量。
     * 命中 {@code idx_event_run_seq (run_id, sequence_no)} 索引，增量拉取代价低。
     */
    @Query("select e from WorkflowEvent e where e.runId = :runId and e.category = 'display' "
            + "and e.sequenceNo > :afterSequence order by e.sequenceNo asc")
    List<WorkflowEvent> findDisplayEventsAfter(@Param("runId") String runId,
                                               @Param("afterSequence") long afterSequence,
                                               Pageable pageable);
}
