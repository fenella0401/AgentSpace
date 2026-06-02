package com.agentspace.orchestration.client.mock;

import com.agentspace.orchestration.client.AgentCoreClient;
import com.agentspace.orchestration.client.dto.CancelAttemptRequest;
import com.agentspace.orchestration.client.dto.CancelAttemptResponse;
import com.agentspace.orchestration.client.dto.QueryAttemptResponse;
import com.agentspace.orchestration.client.dto.StartAttemptRequest;
import com.agentspace.orchestration.client.dto.StartAttemptResponse;
import com.agentspace.orchestration.event.EventSink;
import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.event.AgentExecutionEvent;
import com.agentspace.orchestration.model.event.AttemptResultPayload;
import com.agentspace.orchestration.model.event.AttemptResultStatus;
import com.agentspace.orchestration.model.event.EventSource;
import com.agentspace.orchestration.model.event.EventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * mock Agent Core（{@code mock} profile）。按 {@link MockAgentCoreProperties} 配置返回
 * 成功 / 失败 / 超时，并在 SUCCEED / FAIL 时异步向 {@link EventSink} 推送一串事件，供 FE2+ 联调。
 *
 * <p>不是真实运行时：不 clone 代码、不起 executor，仅模拟事件时序与终态。
 */
@Component
@Profile("mock")
public class MockAgentCoreClient implements AgentCoreClient {

    private static final Logger log = LoggerFactory.getLogger(MockAgentCoreClient.class);

    private final MockAgentCoreProperties props;
    private final EventSink eventSink;

    /** attemptId -> 已分配的 runtimeAttemptRef，用于幂等与 query/cancel。 */
    private final Map<String, String> runtimeRefs = new ConcurrentHashMap<>();
    private final Map<String, AttemptStatus> statuses = new ConcurrentHashMap<>();

    public MockAgentCoreClient(MockAgentCoreProperties props, EventSink eventSink) {
        this.props = props;
        this.eventSink = eventSink;
    }

    @Override
    public StartAttemptResponse startAttempt(StartAttemptRequest request) {
        String attemptId = request.attemptId();
        // 幂等：重复 start 返回已有 runtimeAttemptRef
        String runtimeRef = runtimeRefs.computeIfAbsent(attemptId,
                k -> "mock-rt-" + UUID.randomUUID());

        sleepQuietly(props.startDelayMs());

        if (props.behavior() == MockAgentCoreProperties.Behavior.TIMEOUT) {
            // 模拟超时：标记 running 后不再推进终态，留给 Agent-Orchestration watchdog 兜底
            statuses.put(attemptId, AttemptStatus.RUNNING);
            log.info("[mock] startAttempt TIMEOUT attempt={} runtimeRef={}", attemptId, runtimeRef);
            return new StartAttemptResponse(runtimeRef, AttemptStatus.STARTING);
        }

        statuses.put(attemptId, AttemptStatus.RUNNING);
        emitEvents(request, runtimeRef);
        return new StartAttemptResponse(runtimeRef, AttemptStatus.STARTING);
    }

    @Override
    public CancelAttemptResponse cancelAttempt(CancelAttemptRequest request) {
        String attemptId = request.attemptId();
        boolean found = runtimeRefs.containsKey(attemptId);
        statuses.put(attemptId, AttemptStatus.CANCELLED);
        log.info("[mock] cancelAttempt attempt={} found={} keepSession={}",
                attemptId, found, request.keepSessionContext());
        return new CancelAttemptResponse(attemptId, AttemptStatus.CANCELLED, found);
    }

    @Override
    public QueryAttemptResponse queryAttempt(String attemptId) {
        AttemptStatus status = statuses.getOrDefault(attemptId, AttemptStatus.PENDING);
        return new QueryAttemptResponse(
                attemptId,
                runtimeRefs.get(attemptId),
                status,
                Instant.now(),
                status == AttemptStatus.SUCCEEDED ? 0 : null,
                status == AttemptStatus.FAILED ? props.failureReason() : null);
    }

    /**
     * 推送事件时序：runtime.attempt_created → (展示类若干) → attempt.result + runtime.completed/failed。
     *
     * <p>mock 阶段同步推送，便于单测直接断言；真实 Agent Core 为异步事件流。
     */
    void emitEvents(StartAttemptRequest request, String runtimeRef) {
        AtomicLong seq = new AtomicLong(0);

        emit(request, EventTypes.RUNTIME_ATTEMPT_CREATED, EventSource.RUNTIME, seq, Map.of("runtimeRef", runtimeRef));
        emit(request, EventTypes.RUNTIME_RUNNING, EventSource.RUNTIME, seq, Map.of());
        emit(request, EventTypes.ATTEMPT_STARTED, EventSource.AGENT_CORE, seq, Map.of());

        if (props.emitDisplayEvents()) {
            for (int i = 0; i < props.eventCount(); i++) {
                emit(request, EventTypes.AGENT_MESSAGE, EventSource.EXECUTOR, seq,
                        Map.of("role", "ASSISTANT", "text", "[mock] message " + i));
            }
        }

        boolean succeed = props.behavior() == MockAgentCoreProperties.Behavior.SUCCEED;
        AttemptResultStatus resultStatus = succeed ? AttemptResultStatus.SUCCEEDED : AttemptResultStatus.FAILED;
        AttemptResultPayload result = new AttemptResultPayload(
                resultStatus,
                succeed ? "[mock] done" : "[mock] failed",
                succeed ? "{}" : null,
                java.util.List.of(),
                "mock-session-" + request.attemptId(),
                succeed ? null : props.failureReason(),
                succeed ? null : "[mock] simulated failure");
        emit(request, EventTypes.ATTEMPT_RESULT, EventSource.AGENT_CORE, seq, toMap(result));

        if (succeed) {
            statuses.put(request.attemptId(), AttemptStatus.SUCCEEDED);
            emit(request, EventTypes.RUNTIME_COMPLETED, EventSource.RUNTIME, seq, Map.of());
        } else {
            statuses.put(request.attemptId(), AttemptStatus.FAILED);
            emit(request, EventTypes.RUNTIME_FAILED, EventSource.RUNTIME, seq,
                    Map.of("failureReason", props.failureReason()));
        }
    }

    private void emit(StartAttemptRequest request, String eventType, EventSource source,
                      AtomicLong seq, Map<String, Object> payload) {
        AgentExecutionEvent event = new AgentExecutionEvent(
                UUID.randomUUID().toString(),
                eventType,
                request.runId(),
                request.stepId(),
                request.stepKey(),
                request.attemptId(),
                request.attemptNo(),
                Instant.now(),
                seq.incrementAndGet(),
                source,
                payload);
        eventSink.accept(event);
    }

    private Map<String, Object> toMap(AttemptResultPayload r) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("status", r.status().name());
        m.put("summary", r.summary());
        m.put("result", r.result());
        m.put("artifactRefs", r.artifactRefs());
        m.put("sessionRef", r.sessionRef());
        m.put("errorCode", r.errorCode());
        m.put("errorMessage", r.errorMessage());
        return m;
    }

    private void sleepQuietly(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
