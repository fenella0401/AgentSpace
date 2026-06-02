package com.agentspace.orchestration.client.mock;

import com.agentspace.orchestration.client.dto.CancelAttemptRequest;
import com.agentspace.orchestration.client.dto.CancelAttemptResponse;
import com.agentspace.orchestration.client.dto.StartAttemptRequest;
import com.agentspace.orchestration.client.dto.StartAttemptResponse;
import com.agentspace.orchestration.event.EventSink;
import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.ExecutorType;
import com.agentspace.orchestration.model.event.AgentExecutionEvent;
import com.agentspace.orchestration.model.event.EventTypes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockAgentCoreClientTest {

    /** 收集事件的测试 sink。 */
    private static final class CollectingSink implements EventSink {
        final List<AgentExecutionEvent> events = new ArrayList<>();

        @Override
        public void accept(AgentExecutionEvent event) {
            events.add(event);
        }
    }

    private StartAttemptRequest request() {
        return new StartAttemptRequest(
                "run-1", "step-1", "analyze", "attempt-1", 1,
                ExecutorType.CLAUDE_CODE, "rendered prompt",
                "agent-ref", List.of(), List.of(), List.of(),
                null, null, null,
                "evt-stream", "log-stream",
                null);
    }

    private MockAgentCoreProperties props(MockAgentCoreProperties.Behavior behavior) {
        return new MockAgentCoreProperties(behavior, 0, true, 2, "EXECUTOR_FAILED");
    }

    @Test
    void succeedEmitsResultAndCompletedInOrder() {
        CollectingSink sink = new CollectingSink();
        MockAgentCoreClient client = new MockAgentCoreClient(props(MockAgentCoreProperties.Behavior.SUCCEED), sink, Runnable::run);

        StartAttemptResponse resp = client.startAttempt(request());

        assertThat(resp.runtimeAttemptRef()).startsWith("mock-rt-");
        assertThat(resp.initialStatus()).isEqualTo(AttemptStatus.STARTING);

        List<String> types = sink.events.stream().map(AgentExecutionEvent::eventType).toList();
        assertThat(types).startsWith(EventTypes.RUNTIME_ATTEMPT_CREATED);
        assertThat(types).contains(EventTypes.ATTEMPT_RESULT);
        assertThat(types).endsWith(EventTypes.RUNTIME_COMPLETED);
        // sequence 单调递增
        long[] seqs = sink.events.stream().mapToLong(AgentExecutionEvent::sequence).toArray();
        for (int i = 1; i < seqs.length; i++) {
            assertThat(seqs[i]).isGreaterThan(seqs[i - 1]);
        }
        assertThat(client.queryAttempt("attempt-1").status()).isEqualTo(AttemptStatus.SUCCEEDED);
    }

    @Test
    void failEmitsRuntimeFailed() {
        CollectingSink sink = new CollectingSink();
        MockAgentCoreClient client = new MockAgentCoreClient(props(MockAgentCoreProperties.Behavior.FAIL), sink, Runnable::run);

        client.startAttempt(request());

        List<String> types = sink.events.stream().map(AgentExecutionEvent::eventType).toList();
        assertThat(types).endsWith(EventTypes.RUNTIME_FAILED);
        assertThat(client.queryAttempt("attempt-1").status()).isEqualTo(AttemptStatus.FAILED);
    }

    @Test
    void timeoutEmitsNoTerminalEvent() {
        CollectingSink sink = new CollectingSink();
        MockAgentCoreClient client = new MockAgentCoreClient(props(MockAgentCoreProperties.Behavior.TIMEOUT), sink, Runnable::run);

        client.startAttempt(request());

        List<String> types = sink.events.stream().map(AgentExecutionEvent::eventType).toList();
        // 超时：不推任何终态事件，留给 watchdog 兜底
        assertThat(types).doesNotContain(EventTypes.ATTEMPT_RESULT,
                EventTypes.RUNTIME_COMPLETED, EventTypes.RUNTIME_FAILED);
        assertThat(client.queryAttempt("attempt-1").status()).isEqualTo(AttemptStatus.RUNNING);
    }

    @Test
    void startAttemptIsIdempotent() {
        CollectingSink sink = new CollectingSink();
        MockAgentCoreClient client = new MockAgentCoreClient(props(MockAgentCoreProperties.Behavior.SUCCEED), sink, Runnable::run);

        StartAttemptResponse first = client.startAttempt(request());
        StartAttemptResponse second = client.startAttempt(request());

        assertThat(second.runtimeAttemptRef()).isEqualTo(first.runtimeAttemptRef());
    }

    @Test
    void cancelReportsRuntimeFound() {
        CollectingSink sink = new CollectingSink();
        MockAgentCoreClient client = new MockAgentCoreClient(props(MockAgentCoreProperties.Behavior.SUCCEED), sink, Runnable::run);
        client.startAttempt(request());

        CancelAttemptResponse resp = client.cancelAttempt(new CancelAttemptRequest("attempt-1", true));
        assertThat(resp.runtimeFound()).isTrue();
        assertThat(resp.status()).isEqualTo(AttemptStatus.CANCELLED);

        CancelAttemptResponse unknown = client.cancelAttempt(new CancelAttemptRequest("nope", true));
        assertThat(unknown.runtimeFound()).isFalse();
    }
}
