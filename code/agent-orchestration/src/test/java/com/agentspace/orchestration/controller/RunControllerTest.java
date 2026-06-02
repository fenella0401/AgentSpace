package com.agentspace.orchestration.controller;

import com.agentspace.orchestration.model.ExecutorType;
import com.agentspace.orchestration.model.flow.AgentFlow;
import com.agentspace.orchestration.model.flow.AgentFlowStep;
import com.agentspace.orchestration.model.flow.AgentSpec;
import com.agentspace.orchestration.model.flow.PromptSpec;
import com.agentspace.orchestration.model.flow.RunRef;
import com.agentspace.orchestration.model.flow.TaskRef;
import com.agentspace.orchestration.model.flow.TenantRef;
import com.agentspace.orchestration.service.RunService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FE4 只读接口：GET /runs/{id} 快照、GET /runs/{id}/events SSE。鉴权 MVP 暂缓。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "mock"})
class RunControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    RunService runService;

    private AgentFlow flow(String idemKey) {
        AgentFlowStep step = new AgentFlowStep("a", "a",
                new AgentSpec(ExecutorType.CLAUDE_CODE, "agent-ref", List.of(), List.of()),
                new PromptSpec("do a", Map.of()), true);
        return new AgentFlow("1", "flow-1", "f", "snap-" + idemKey,
                new RunRef("run-" + idemKey, idemKey),
                new TenantRef("team-1", "user-1"),
                new TaskRef("task-1", "proj-1"),
                null, null, null, Map.of(),
                List.of(step), List.of());
    }

    @Test
    void getUnknownRunReturns404() throws Exception {
        mockMvc.perform(get("/runs/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void getRunReturnsSnapshot() throws Exception {
        String runId = runService.startRun(flow("ctrl-snap")).getId();
        mockMvc.perform(get("/runs/{id}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.runId").value(runId))
                .andExpect(jsonPath("$.steps[0].stepKey").value("a"));
    }

    @Test
    void eventStreamStartsAsyncSse() throws Exception {
        String runId = runService.startRun(flow("ctrl-sse")).getId();
        // SSE 是异步响应：验证进入了 async 流程（emitter 已注册），content-type 在 dispatch 后才定。
        mockMvc.perform(get("/runs/{id}/events", runId).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    void eventStreamForUnknownRunReturns404() throws Exception {
        mockMvc.perform(get("/runs/{id}/events", "nope").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound());
    }
}
