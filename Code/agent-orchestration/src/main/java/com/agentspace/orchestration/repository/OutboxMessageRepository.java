package com.agentspace.orchestration.repository;

import com.agentspace.orchestration.model.entity.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * outbox_message 持久化。worker 轮询 PENDING 且到期的消息投递。
 */
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, String> {

    List<OutboxMessage> findByStatusAndNextRetryAtBefore(String status, OffsetDateTime before);
}
