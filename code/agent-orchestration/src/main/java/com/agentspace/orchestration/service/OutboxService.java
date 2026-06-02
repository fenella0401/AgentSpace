package com.agentspace.orchestration.service;

import com.agentspace.orchestration.client.dto.OutboundEvent;
import com.agentspace.orchestration.config.OrchestrationProperties;
import com.agentspace.orchestration.model.entity.OutboxMessage;
import com.agentspace.orchestration.repository.OutboxMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * outbox 写入（FE5）：状态变更与展示事件在**当前事务内**写 outbox，由 {@link com.agentspace.orchestration.scheduler.OutboxWorker}
 * 异步投递 Agent-Management，至少一次。见详细设计 §1.7、§2.9、§9.8。
 *
 * <p>背压：PENDING 积压超阈值时，展示类事件降采样（不再入 outbox），控制类状态变更始终入队。
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    public static final String TYPE_DISPLAY = "display";

    private final OutboxMessageRepository outboxRepo;
    private final OrchestrationProperties props;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxMessageRepository outboxRepo,
                         OrchestrationProperties props,
                         ObjectMapper objectMapper) {
        this.outboxRepo = outboxRepo;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /** 状态变更入 outbox（控制类，始终入队）。 */
    public void enqueueStateChange(String runId, String stepId, String attemptId, String type, Object payload) {
        enqueue(runId, stepId, attemptId, type, payload);
    }

    /**
     * 展示事件入 outbox（受背压降采样）。积压超阈值时跳过，返回 false。
     */
    public boolean enqueueDisplay(String runId, String stepId, String attemptId, Object payload) {
        if (outboxRepo.countByStatus("PENDING") >= props.outbox().maxPending()) {
            log.warn("outbox 积压超阈值，展示事件降采样丢弃 run={}", runId);
            return false;
        }
        enqueue(runId, stepId, attemptId, TYPE_DISPLAY, payload);
        return true;
    }

    private void enqueue(String runId, String stepId, String attemptId, String type, Object payload) {
        OutboxMessage msg = new OutboxMessage();
        String outboxId = "ob-" + UUID.randomUUID();
        msg.setId(outboxId);
        msg.setRunId(runId);
        msg.setPayload(writeOutbound(new OutboundEvent(outboxId, type, runId, stepId, attemptId, writeJson(payload))));
        msg.setStatus("PENDING");
        msg.setRetryCount(0);
        msg.setNextRetryAt(OffsetDateTime.now());
        msg.setCreatedAt(OffsetDateTime.now());
        outboxRepo.save(msg);
    }

    private String writeOutbound(OutboundEvent event) {
        return writeJson(event);
    }

    private String writeJson(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
