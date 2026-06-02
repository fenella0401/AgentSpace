package com.agentspace.orchestration.scheduler;

import com.agentspace.orchestration.client.AgentManagementClient;
import com.agentspace.orchestration.client.dto.OutboundEvent;
import com.agentspace.orchestration.config.OrchestrationProperties;
import com.agentspace.orchestration.model.entity.OutboxMessage;
import com.agentspace.orchestration.repository.OutboxMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * outbox 投递 worker（FE5）：轮询 PENDING 且到期的消息，投递 Agent-Management。
 * 成功 → SENT；失败 → 指数退避重试（至少一次）。见详细设计 §2.9、§9.8。
 */
@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    private final OutboxMessageRepository outboxRepo;
    private final AgentManagementClient client;
    private final OrchestrationProperties props;
    private final ObjectMapper objectMapper;

    public OutboxWorker(OutboxMessageRepository outboxRepo,
                        AgentManagementClient client,
                        OrchestrationProperties props,
                        ObjectMapper objectMapper) {
        this.outboxRepo = outboxRepo;
        this.client = client;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${orchestration.outbox.poll-interval-ms:1000}")
    public void dispatchPending() {
        List<OutboxMessage> due = outboxRepo.findByStatusAndNextRetryAtBefore("PENDING", OffsetDateTime.now());
        for (OutboxMessage msg : due) {
            dispatchOne(msg.getId());
        }
    }

    /** 单条投递，独立事务，失败不影响其他消息。 */
    @Transactional
    public void dispatchOne(String outboxId) {
        OutboxMessage msg = outboxRepo.findById(outboxId).orElse(null);
        if (msg == null || !"PENDING".equals(msg.getStatus())) {
            return;
        }
        try {
            OutboundEvent event = objectMapper.readValue(msg.getPayload(), OutboundEvent.class);
            client.sendEvent(event);
            msg.setStatus("SENT");
            msg.setSentAt(OffsetDateTime.now());
            outboxRepo.save(msg);
        } catch (Exception e) {
            int next = msg.getRetryCount() + 1;
            msg.setRetryCount(next);
            if (next >= props.outbox().maxRetries()) {
                msg.setStatus("FAILED");
                log.error("outbox {} 投递失败达上限({})，标记 FAILED: {}", outboxId, next, e.getMessage());
            } else {
                long backoff = (long) props.outbox().backoffBaseSeconds() * (1L << Math.min(next, 16));
                msg.setNextRetryAt(OffsetDateTime.now().plusSeconds(backoff));
                log.warn("outbox {} 投递失败，{}s 后重试(第{}次): {}", outboxId, backoff, next, e.getMessage());
            }
            outboxRepo.save(msg);
        }
    }
}
