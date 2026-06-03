package com.agentspace.orchestration.service;

import com.agentspace.orchestration.controller.dto.DisplayEventMessage;
import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.entity.ProcessedEvent;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.WorkflowEvent;
import com.agentspace.orchestration.model.event.AgentExecutionEvent;
import com.agentspace.orchestration.model.event.AttemptResultStatus;
import com.agentspace.orchestration.model.event.EventCategory;
import com.agentspace.orchestration.model.event.EventTypes;
import com.agentspace.orchestration.repository.ProcessedEventRepository;
import com.agentspace.orchestration.repository.StepAttemptRepository;
import com.agentspace.orchestration.repository.WorkflowEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Agent Core 入站事件处理（FE3）：eventId 去重 → 归属校验 → 按类别分流 → 控制/runtime 推进状态机
 * （含 attempt.result 与 runtime.* 乱序合并）→ 留存。见详细设计 §2.8、§8.4。
 */
@Service
public class EventIngestService {

    private static final Logger log = LoggerFactory.getLogger(EventIngestService.class);

    private final ProcessedEventRepository processedRepo;
    private final StepAttemptRepository attemptRepo;
    private final WorkflowEventRepository eventRepo;
    private final AttemptResultHandler resultHandler;
    private final RunEventBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public EventIngestService(ProcessedEventRepository processedRepo,
                              StepAttemptRepository attemptRepo,
                              WorkflowEventRepository eventRepo,
                              AttemptResultHandler resultHandler,
                              RunEventBroadcaster broadcaster,
                              ObjectMapper objectMapper) {
        this.processedRepo = processedRepo;
        this.attemptRepo = attemptRepo;
        this.eventRepo = eventRepo;
        this.resultHandler = resultHandler;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理一个入站事件。幂等：重复 eventId 直接返回。归属不符抛 {@link EventAttributionException}（422）。
     */
    @Transactional
    public void ingest(AgentExecutionEvent event) {
        if (event.eventId() != null && processedRepo.existsById(event.eventId())) {
            log.debug("事件 {} 重复，幂等丢弃", event.eventId());
            return;
        }

        StepAttempt attempt = resolveAndVerify(event);

        EventCategory category = EventTypes.categoryOf(event.eventType());
        persistEvent(event, category);

        switch (category) {
            case CONTROL -> handleControl(event, attempt);
            case RUNTIME -> handleRuntime(event, attempt);
            case DISPLAY -> {
                // 展示类事件已 persistEvent 落库（workflow_event，单存储事实源），并实时推浏览器；
                // 浏览器经 /events/poll 或 SSE 消费，历史回放亦读本地表，不再回流 Agent-Management。
                broadcaster.publish(event.runId(), new DisplayEventMessage(
                        event.eventId(), event.eventType(), "display",
                        event.sequence(), writePayload(event.payload())));
            }
        }

        if (event.eventId() != null) {
            processedRepo.save(new ProcessedEvent(event.eventId(), OffsetDateTime.now()));
        }
    }

    /** 校验事件归属：attemptId 必须存在，且 runId/stepId 与库中一致。 */
    private StepAttempt resolveAndVerify(AgentExecutionEvent event) {
        if (event.attemptId() == null) {
            throw new EventAttributionException("事件缺少 attemptId");
        }
        StepAttempt attempt = attemptRepo.findById(event.attemptId())
                .orElseThrow(() -> new EventAttributionException("attempt 不存在: " + event.attemptId()));
        if (event.runId() != null && !event.runId().equals(attempt.getRunId())) {
            throw new EventAttributionException("事件 runId 与 attempt 不符: " + event.runId());
        }
        if (event.stepId() != null && !event.stepId().equals(attempt.getStepId())) {
            throw new EventAttributionException("事件 stepId 与 attempt 不符: " + event.stepId());
        }
        return attempt;
    }

    private void handleControl(AgentExecutionEvent event, StepAttempt attempt) {
        switch (event.eventType()) {
            case EventTypes.ATTEMPT_STARTED -> markRunning(attempt);
            case EventTypes.ATTEMPT_HEARTBEAT -> {
                attempt.setLastHeartbeatAt(OffsetDateTime.now());
                attempt.setUpdatedAt(OffsetDateTime.now());
                attemptRepo.save(attempt);
            }
            case EventTypes.ATTEMPT_RESULT -> {
                AttemptResultStatus status = readResultStatus(event.payload());
                attempt.setPendingResultStatus(status.name());
                attempt.setPendingResultSummary(str(event.payload(), "summary"));
                attempt.setPendingResultDetail(str(event.payload(), "result"));
                attempt.setPendingSessionRef(str(event.payload(), "sessionRef"));
                attempt.setPendingArtifactRefs(writeJson(
                        event.payload() == null ? null : event.payload().get("artifactRefs")));
                attempt.setUpdatedAt(OffsetDateTime.now());
                attemptRepo.save(attempt);
                tryFinalize(attempt);
            }
            default -> log.debug("未处理的 control 事件: {}", event.eventType());
        }
    }

    private void handleRuntime(AgentExecutionEvent event, StepAttempt attempt) {
        switch (event.eventType()) {
            case EventTypes.RUNTIME_ATTEMPT_CREATED, EventTypes.RUNTIME_RUNNING -> {
                if (attempt.getStatus() == AttemptStatus.STARTING) {
                    markRunning(attempt);
                }
            }
            case EventTypes.RUNTIME_COMPLETED -> {
                attempt.setRuntimeTerminal("COMPLETED");
                save(attempt);
                tryFinalize(attempt);
            }
            case EventTypes.RUNTIME_FAILED -> {
                attempt.setRuntimeTerminal("FAILED");
                save(attempt);
                tryFinalize(attempt);
            }
            case EventTypes.RUNTIME_CANCELLED -> {
                attempt.setRuntimeTerminal("CANCELLED");
                save(attempt);
                tryFinalize(attempt);
            }
            default -> log.debug("未处理的 runtime 事件: {}", event.eventType());
        }
    }

    /**
     * 乱序合并判定终态。见详细设计 §8.4：
     * <ul>
     *   <li>result=success 且 runtime.completed → SUCCEEDED；</li>
     *   <li>result=failed 或 runtime.failed → FAILED；</li>
     *   <li>runtime.cancelled → CANCELLED。</li>
     * </ul>
     */
    private void tryFinalize(StepAttempt attempt) {
        String pending = attempt.getPendingResultStatus();
        String runtime = attempt.getRuntimeTerminal();

        // runtime 失败：无需等 result，直接失败
        if ("FAILED".equals(runtime)) {
            resultHandler.onAttemptFailed(attempt.getId(), "RUNTIME_FAILED", "runtime failed");
            return;
        }
        if ("CANCELLED".equals(runtime)) {
            // Agent Core 自发上报 runtime.cancelled → attempt/step CANCELLED；
            // 经 orchestration 主动 cancel 的路径由 RunCancellationService 负责（attempt 已先置 CANCELLED）。
            resultHandler.onAttemptCancelled(attempt.getId());
            return;
        }
        // result 失败：无需等 runtime
        if ("FAILED".equals(pending)) {
            resultHandler.onAttemptFailed(attempt.getId(), "EXECUTOR_FAILED", "executor reported failure");
            return;
        }
        // 成功需两信号齐：result=SUCCEEDED 且 runtime.completed
        if ("SUCCEEDED".equals(pending) && "COMPLETED".equals(runtime)) {
            resultHandler.onAttemptSucceeded(attempt.getId(),
                    attempt.getPendingResultSummary(),
                    attempt.getPendingResultDetail(),
                    attempt.getPendingSessionRef(),
                    attempt.getPendingArtifactRefs());
        }
    }

    private void markRunning(StepAttempt attempt) {
        if (attempt.getStatus() == AttemptStatus.STARTING || attempt.getStatus() == AttemptStatus.PENDING) {
            attempt.setStatus(AttemptStatus.RUNNING);
            if (attempt.getStartedAt() == null) {
                attempt.setStartedAt(OffsetDateTime.now());
            }
            save(attempt);
        }
    }

    private void persistEvent(AgentExecutionEvent event, EventCategory category) {
        WorkflowEvent e = new WorkflowEvent();
        e.setId("evt-" + UUID.randomUUID());
        e.setEventId(event.eventId());
        e.setRunId(event.runId());
        e.setStepId(event.stepId());
        e.setAttemptId(event.attemptId());
        e.setEventType(event.eventType());
        e.setCategory(category.name().toLowerCase());
        e.setSequenceNo(event.sequence());
        e.setSource(event.source() != null ? event.source().name() : null);
        e.setPayload(writePayload(event.payload()));
        e.setCreatedAt(OffsetDateTime.now());
        eventRepo.save(e);
    }

    private void save(StepAttempt attempt) {
        attempt.setUpdatedAt(OffsetDateTime.now());
        attemptRepo.save(attempt);
    }

    private AttemptResultStatus readResultStatus(Map<String, Object> payload) {
        Object s = payload == null ? null : payload.get("status");
        if (s == null) {
            return AttemptResultStatus.FAILED;
        }
        try {
            return AttemptResultStatus.valueOf(String.valueOf(s));
        } catch (IllegalArgumentException e) {
            return AttemptResultStatus.FAILED;
        }
    }

    private String str(Map<String, Object> payload, String key) {
        Object v = payload == null ? null : payload.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private String writePayload(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
