package com.agentspace.orchestration.client.claudecode;

import com.agentspace.orchestration.event.EventSink;
import com.agentspace.orchestration.model.event.AgentExecutionEvent;
import com.agentspace.orchestration.model.event.EventSource;
import com.agentspace.orchestration.model.event.EventTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Claude Code SDK stream-json 解析与映射单测：用一段真实形态的 NDJSON 转写，
 * 断言 system/assistant/user/result 各消息正确翻译为内部事件，并验证脱敏/限长、
 * runtime 终态合成与幂等 eventId。见概要设计 §8.4。
 */
class ClaudeCodeEventAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ClaudeCodeEventAdapter adapter =
            new ClaudeCodeEventAdapter(ClaudeCodeAdapterProperties.defaults());

    private final AttemptContext ctx =
            new AttemptContext("run-1", "step-1", "analyze", "att-1", 1);

    /** 收集投递事件的内存 sink。 */
    private static final class CollectingSink implements EventSink {
        final List<AgentExecutionEvent> events = new ArrayList<>();

        @Override
        public void accept(AgentExecutionEvent event) {
            events.add(event);
        }
    }

    private String transcript() {
        return String.join("\n",
                // 会话初始化
                "{\"type\":\"system\",\"subtype\":\"init\",\"session_id\":\"sess-abc\",\"model\":\"claude-opus-4-8\",\"tools\":[\"Bash\",\"Read\"]}",
                // assistant：文本 + 工具调用
                "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_01\",\"role\":\"assistant\",\"content\":["
                        + "{\"type\":\"text\",\"text\":\"我来读取文件\"},"
                        + "{\"type\":\"tool_use\",\"id\":\"tu_1\",\"name\":\"Read\",\"input\":{\"path\":\"a.txt\"}}"
                        + "]}}",
                // user：工具结果（数组形态 content）
                "{\"type\":\"user\",\"message\":{\"role\":\"user\",\"content\":["
                        + "{\"type\":\"tool_result\",\"tool_use_id\":\"tu_1\",\"content\":[{\"type\":\"text\",\"text\":\"file body\"}]}"
                        + "]}}",
                // assistant：思考 + 收尾文本
                "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_02\",\"role\":\"assistant\",\"content\":["
                        + "{\"type\":\"thinking\",\"thinking\":\"用户想要分析\"},"
                        + "{\"type\":\"text\",\"text\":\"分析完成\"}"
                        + "]}}",
                // 终态
                "{\"type\":\"result\",\"subtype\":\"success\",\"is_error\":false,\"result\":\"done\",\"session_id\":\"sess-abc\",\"total_cost_usd\":0.01}");
    }

    private List<AgentExecutionEvent> parseAll() {
        ClaudeCodeStreamParser parser = new ClaudeCodeStreamParser(mapper, adapter, new CollectingSink());
        // 直接用解析单行的方式收集（复用同一 Sequencer 保证序号连续）
        ClaudeCodeEventAdapter.Sequencer seq = new ClaudeCodeEventAdapter.Sequencer();
        List<AgentExecutionEvent> all = new ArrayList<>();
        for (String line : transcript().split("\n")) {
            all.addAll(parser.parseLine(line, ctx, seq));
        }
        return all;
    }

    @Test
    void mapsFullTranscriptToInternalEvents() {
        List<AgentExecutionEvent> events = parseAll();

        List<String> types = events.stream().map(AgentExecutionEvent::eventType).toList();
        assertThat(types).containsExactly(
                EventTypes.ATTEMPT_STARTED,    // system/init
                EventTypes.AGENT_MESSAGE,      // assistant text
                EventTypes.AGENT_TOOL_USE,     // assistant tool_use
                EventTypes.AGENT_TOOL_RESULT,  // user tool_result
                EventTypes.AGENT_THINKING,     // assistant thinking
                EventTypes.AGENT_MESSAGE,      // assistant text
                EventTypes.ATTEMPT_RESULT,     // result
                EventTypes.RUNTIME_COMPLETED   // 合成的 runtime 终态
        );
    }

    @Test
    void preservesAttributionAndMonotonicSequence() {
        List<AgentExecutionEvent> events = parseAll();

        assertThat(events).allSatisfy(e -> {
            assertThat(e.runId()).isEqualTo("run-1");
            assertThat(e.stepId()).isEqualTo("step-1");
            assertThat(e.attemptId()).isEqualTo("att-1");
            assertThat(e.stepKey()).isEqualTo("analyze");
        });
        List<Long> seqs = events.stream().map(AgentExecutionEvent::sequence).toList();
        for (int i = 1; i < seqs.size(); i++) {
            assertThat(seqs.get(i)).isGreaterThan(seqs.get(i - 1));
        }
    }

    @Test
    void initCarriesSessionRefAndSource() {
        AgentExecutionEvent init = parseAll().get(0);
        assertThat(init.eventType()).isEqualTo(EventTypes.ATTEMPT_STARTED);
        assertThat(init.source()).isEqualTo(EventSource.AGENT_CORE);
        assertThat(init.payload()).containsEntry("sessionRef", "sess-abc");
    }

    @Test
    void toolResultArrayContentIsFlattenedToText() {
        AgentExecutionEvent toolResult = parseAll().stream()
                .filter(e -> e.eventType().equals(EventTypes.AGENT_TOOL_RESULT))
                .findFirst().orElseThrow();
        assertThat(toolResult.payload()).containsEntry("toolCallId", "tu_1");
        assertThat(toolResult.payload()).containsEntry("status", "SUCCESS");
        assertThat(toolResult.payload()).containsEntry("output", "file body");
    }

    @Test
    void resultCarriesSessionRefForResume() {
        AgentExecutionEvent result = parseAll().stream()
                .filter(e -> e.eventType().equals(EventTypes.ATTEMPT_RESULT))
                .findFirst().orElseThrow();
        assertThat(result.payload()).containsEntry("status", "SUCCEEDED");
        assertThat(result.payload()).containsEntry("sessionRef", "sess-abc");
        assertThat(result.source()).isEqualTo(EventSource.AGENT_CORE);
    }

    @Test
    void errorResultMapsToFailedAndRuntimeFailed() {
        String line = "{\"type\":\"result\",\"subtype\":\"error_during_execution\",\"is_error\":true,"
                + "\"result\":\"boom\",\"session_id\":\"sess-x\"}";
        ClaudeCodeStreamParser parser = new ClaudeCodeStreamParser(mapper, adapter, new CollectingSink());
        List<AgentExecutionEvent> events =
                parser.parseLine(line, ctx, new ClaudeCodeEventAdapter.Sequencer());

        assertThat(events).hasSize(2);
        assertThat(events.get(0).eventType()).isEqualTo(EventTypes.ATTEMPT_RESULT);
        assertThat(events.get(0).payload()).containsEntry("status", "FAILED");
        assertThat(events.get(0).payload()).containsEntry("errorCode", "error_during_execution");
        assertThat(events.get(1).eventType()).isEqualTo(EventTypes.RUNTIME_FAILED);
        assertThat(events.get(1).source()).isEqualTo(EventSource.RUNTIME);
    }

    @Test
    void thinkingIsTruncatedToSummary() {
        ClaudeCodeEventAdapter shortAdapter =
                new ClaudeCodeEventAdapter(new ClaudeCodeAdapterProperties(10, 8000, true));
        String line = "{\"type\":\"assistant\",\"message\":{\"id\":\"m\",\"role\":\"assistant\",\"content\":["
                + "{\"type\":\"thinking\",\"thinking\":\"0123456789ABCDEFGHIJ\"}]}}";
        ClaudeCodeStreamParser parser = new ClaudeCodeStreamParser(mapper, shortAdapter, new CollectingSink());
        AgentExecutionEvent thinking =
                parser.parseLine(line, ctx, new ClaudeCodeEventAdapter.Sequencer()).get(0);

        assertThat(thinking.eventType()).isEqualTo(EventTypes.AGENT_THINKING);
        assertThat((String) thinking.payload().get("summary"))
                .startsWith("0123456789")
                .contains("truncated");
    }

    @Test
    void deterministicEventIdEnablesDedupOnReread() {
        // 同一行解析两次（模拟断线重连重读）应得到相同 eventId
        String line = "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_dup\",\"role\":\"assistant\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}}";
        ClaudeCodeStreamParser parser = new ClaudeCodeStreamParser(mapper, adapter, new CollectingSink());
        String id1 = parser.parseLine(line, ctx, new ClaudeCodeEventAdapter.Sequencer()).get(0).eventId();
        String id2 = parser.parseLine(line, ctx, new ClaudeCodeEventAdapter.Sequencer()).get(0).eventId();
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void illegalJsonLineIsSkipped() {
        ClaudeCodeStreamParser parser = new ClaudeCodeStreamParser(mapper, adapter, new CollectingSink());
        assertThat(parser.parseLine("not json", ctx, new ClaudeCodeEventAdapter.Sequencer())).isEmpty();
    }

    @Test
    void parseAndDispatchFeedsSink() {
        CollectingSink sink = new CollectingSink();
        ClaudeCodeStreamParser parser = new ClaudeCodeStreamParser(mapper, adapter, sink);
        int dispatched = parser.parseAndDispatch(new StringReader(transcript()), ctx);
        assertThat(dispatched).isEqualTo(sink.events.size());
        assertThat(dispatched).isEqualTo(8);
    }
}
