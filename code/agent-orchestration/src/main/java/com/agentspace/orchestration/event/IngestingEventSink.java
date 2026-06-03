package com.agentspace.orchestration.event;

import com.agentspace.orchestration.model.event.AgentExecutionEvent;
import com.agentspace.orchestration.service.event.EventIngestService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * {@link EventSink} 的主实现（FE3）：把事件交给 {@link EventIngestService} 去重 / 校验 / 推进状态机。
 * 覆盖 {@link LoggingEventSink}，使 mock Agent Core 推送的事件真正驱动编排。
 */
@Component
@Primary
public class IngestingEventSink implements EventSink {

    private final EventIngestService ingestService;

    public IngestingEventSink(EventIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Override
    public void accept(AgentExecutionEvent event) {
        ingestService.ingest(event);
    }
}
