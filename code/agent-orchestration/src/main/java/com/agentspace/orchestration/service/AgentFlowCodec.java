package com.agentspace.orchestration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentspace.orchestration.model.flow.AgentFlow;
import org.springframework.stereotype.Component;

/**
 * AgentFlow 快照的 JSON 存取（jsonb 列以 String 持久化）。
 */
@Component
public class AgentFlowCodec {

    private final ObjectMapper objectMapper;

    public AgentFlowCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(AgentFlow flow) {
        try {
            return objectMapper.writeValueAsString(flow);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AgentFlow 序列化失败", e);
        }
    }

    public AgentFlow fromJson(String json) {
        try {
            return objectMapper.readValue(json, AgentFlow.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AgentFlow 反序列化失败", e);
        }
    }
}
