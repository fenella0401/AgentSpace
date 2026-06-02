package com.agentspace.orchestration.client;

import com.agentspace.orchestration.client.dto.OutboundEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 真实 Agent-Management 出站客户端（非 mock profile）：HTTP POST 到
 * {@code agent-management.events-url}（默认 /internal/agent-orchestration/events）。
 * 见详细设计 §2.9、§9.8。
 */
@Component
@Profile("!mock")
public class HttpAgentManagementClient implements AgentManagementClient {

    private final RestClient restClient;
    private final String eventsUrl;

    public HttpAgentManagementClient(
            @Value("${agent-management.base-url:http://localhost:9090}") String baseUrl,
            @Value("${agent-management.events-path:/internal/agent-orchestration/events}") String eventsPath) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.eventsUrl = eventsPath;
    }

    @Override
    public void sendEvent(OutboundEvent event) {
        restClient.post()
                .uri(eventsUrl)
                .body(event)
                .retrieve()
                .toBodilessEntity();
    }
}
