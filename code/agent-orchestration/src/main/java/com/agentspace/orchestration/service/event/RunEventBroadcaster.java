package com.agentspace.orchestration.service.event;

import com.agentspace.orchestration.controller.dto.DisplayEventMessage;
import com.agentspace.orchestration.model.entity.WorkflowEvent;
import com.agentspace.orchestration.repository.WorkflowEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * run 实时事件流分发器（FE4）：管理每个 run 的 SSE 订阅者，把展示类事件实时推给浏览器。
 * 见详细设计 §2.7、§10.2。鉴权 MVP 暂缓（无鉴权直连）。
 *
 * <p>订阅时可带 fromSequence，先回放已留存的展示类事件（断线续传），再接实时流；
 * 实时与回放事件都带 sequenceNo，浏览器按 eventId 去重、sequenceNo 排序。
 */
@Service
public class RunEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RunEventBroadcaster.class);
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final WorkflowEventRepository eventRepo;

    public RunEventBroadcaster(WorkflowEventRepository eventRepo) {
        this.eventRepo = eventRepo;
    }

    /**
     * 订阅一个 run 的展示类事件流。先回放 sequenceNo &gt; fromSequence 的已留存展示事件，再接实时流。
     *
     * @param fromSequence 断线续传起点（null 表示从当前实时流开始，不回放）
     */
    public SseEmitter subscribe(String runId, Long fromSequence) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> list = subscribers.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        emitter.onCompletion(() -> remove(runId, emitter));
        emitter.onTimeout(() -> remove(runId, emitter));
        emitter.onError(e -> remove(runId, emitter));

        if (fromSequence != null) {
            replay(runId, fromSequence, emitter);
        }
        return emitter;
    }

    /** 回放已留存的展示类事件（断线续传）。 */
    private void replay(String runId, long fromSequence, SseEmitter emitter) {
        List<WorkflowEvent> history = eventRepo.findByRunIdOrderBySequenceNoAsc(runId);
        for (WorkflowEvent e : history) {
            if (!"display".equals(e.getCategory())) {
                continue;
            }
            if (e.getSequenceNo() != null && e.getSequenceNo() > fromSequence) {
                sendTo(emitter, toMessage(e), runId);
            }
        }
    }

    /** 推送一条展示类事件给该 run 的所有订阅者。 */
    public void publish(String runId, DisplayEventMessage message) {
        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(runId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            sendTo(emitter, message, runId);
        }
    }

    private void sendTo(SseEmitter emitter, DisplayEventMessage message, String runId) {
        try {
            emitter.send(SseEmitter.event().name("display").data(message));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 推送失败，移除订阅 run={}: {}", runId, e.getMessage());
            remove(runId, emitter);
        }
    }

    private void remove(String runId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(runId);
        if (list != null) {
            list.remove(emitter);
        }
    }

    private DisplayEventMessage toMessage(WorkflowEvent e) {
        return new DisplayEventMessage(e.getEventId(), e.getEventType(), e.getCategory(),
                e.getSequenceNo(), e.getPayload());
    }

    /** 当前订阅者数（测试 / 监控用）。 */
    public int subscriberCount(String runId) {
        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(runId);
        return list == null ? 0 : list.size();
    }
}
