package com.agentspace.orchestration.client.claudecode;

import com.agentspace.orchestration.event.EventSink;
import com.agentspace.orchestration.model.claudecode.ClaudeCodeMessage;
import com.agentspace.orchestration.model.event.AgentExecutionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

/**
 * 消费 Claude Code SDK {@code stream-json} 输出（newline-delimited JSON），逐行解析为
 * {@link ClaudeCodeMessage}，经 {@link ClaudeCodeEventAdapter} 翻译为内部事件，投递给 {@link EventSink}。
 *
 * <p>这是 Agent Core 入站的「实际接入点」之一：真实运行时把 SDK 进程的 stdout 接到本解析器，
 * 即把执行过程实时转成编排可消费的统一事件流。每个 attempt 用一个 {@link ClaudeCodeEventAdapter.Sequencer}
 * 维护序号；非法 JSON 行跳过并记日志，不中断整条流。
 */
@Component
public class ClaudeCodeStreamParser {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeStreamParser.class);

    private final ObjectMapper objectMapper;
    private final ClaudeCodeEventAdapter adapter;
    private final EventSink eventSink;

    public ClaudeCodeStreamParser(ObjectMapper objectMapper, ClaudeCodeEventAdapter adapter, EventSink eventSink) {
        this.objectMapper = objectMapper;
        this.adapter = adapter;
        this.eventSink = eventSink;
    }

    /**
     * 解析一段 SDK 输出流，把派生的内部事件投递给 sink。阻塞读取直到流结束。
     *
     * @return 投递的事件总数
     */
    public int parseAndDispatch(Reader source, AttemptContext ctx) {
        ClaudeCodeEventAdapter.Sequencer seq = new ClaudeCodeEventAdapter.Sequencer();
        int dispatched = 0;
        try (BufferedReader reader = new BufferedReader(source)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                for (AgentExecutionEvent ev : parseLine(line, ctx, seq)) {
                    eventSink.accept(ev);
                    dispatched++;
                }
            }
        } catch (IOException e) {
            log.warn("[claude-code] 读取 SDK 输出流中断 attempt={}: {}", ctx.attemptId(), e.toString());
        }
        return dispatched;
    }

    /**
     * 解析单行 SDK 输出为内部事件。非法 JSON 跳过（返回空），不抛异常，便于流式逐行调用。
     */
    public List<AgentExecutionEvent> parseLine(String line, AttemptContext ctx, ClaudeCodeEventAdapter.Sequencer seq) {
        ClaudeCodeMessage msg;
        try {
            msg = objectMapper.readValue(line, ClaudeCodeMessage.class);
        } catch (IOException e) {
            log.warn("[claude-code] 跳过非法 JSON 行 attempt={}: {}", ctx.attemptId(), truncate(line));
            return List.of();
        }
        return adapter.adapt(msg, ctx, seq);
    }

    private static String truncate(String line) {
        return line.length() > 200 ? line.substring(0, 200) + "…" : line;
    }
}
