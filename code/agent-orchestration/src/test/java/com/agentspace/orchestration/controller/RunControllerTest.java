package com.agentspace.orchestration.controller;

import com.agentspace.orchestration.model.ExecutorType;
import com.agentspace.orchestration.model.flow.AgentFlow;
import com.agentspace.orchestration.model.flow.AgentFlowStep;
import com.agentspace.orchestration.model.flow.AgentSpec;
import com.agentspace.orchestration.model.flow.PromptSpec;
import com.agentspace.orchestration.model.flow.RunRef;
import com.agentspace.orchestration.model.flow.TaskRef;
import com.agentspace.orchestration.model.flow.TenantRef;
import com.agentspace.orchestration.service.run.RunService;
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

    @Test
    void pollEventsForUnknownRunReturns404() throws Exception {
        mockMvc.perform(get("/runs/{id}/events/poll", "nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void pollEventsReturnsIncrementalBatchWithCursor() throws Exception {
        // 默认 SUCCEED 的 mock 会异步跑完单步并产生展示类事件（emitDisplayEvents）
        String runId = runService.startRun(flow("ctrl-poll")).getId();

        // 轮询直到出现展示事件（mock 异步推送）
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5))
                .until(() -> !runService.pollDisplayEvents(
                        runService.findRun(runId).orElseThrow(), 0L, 100).events().isEmpty());

        mockMvc.perform(get("/runs/{id}/events/poll", runId).param("fromSequence", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.nextSequence").isNumber())
                .andExpect(jsonPath("$.runStatus").exists())
                .andExpect(jsonPath("$.runTerminal").isBoolean());
    }

    @Test
    void pollEventsFromLatestSequenceReturnsEmpty() throws Exception {
        String runId = runService.startRun(flow("ctrl-poll-empty")).getId();
        // 用一个极大的 fromSequence，不应有更新事件
        mockMvc.perform(get("/runs/{id}/events/poll", runId).param("fromSequence", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").isEmpty())
                .andExpect(jsonPath("$.nextSequence").value(999999))
                .andExpect(jsonPath("$.hasMore").value(false));
    }
}
