package com.agentspace.orchestration.repository;

import com.agentspace.orchestration.model.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * processed_event 持久化。入站事件先查此表，存在则丢弃，实现 eventId 幂等消费。
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
