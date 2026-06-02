package com.agentspace.orchestration.controller;

import com.agentspace.orchestration.model.event.AgentExecutionEvent;
import com.agentspace.orchestration.service.EventIngestService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Agent Core 入站事件回调（内部）。见详细设计 §2.8。
 */
@RestController
@RequestMapping("/internal/agent-core")
public class AgentCoreEventController {

    private final EventIngestService ingestService;

    public AgentCoreEventController(EventIngestService ingestService) {
        this.ingestService = ingestService;
    }

    /**
     * POST /internal/agent-core/events — 接收执行事件。eventId 去重、归属校验、按类别分流。
     */
    @PostMapping("/events")
    public Map<String, Object> receiveEvent(@RequestBody AgentExecutionEvent event) {
        ingestService.ingest(event);
        return Map.of("accepted", true);
    }
}
