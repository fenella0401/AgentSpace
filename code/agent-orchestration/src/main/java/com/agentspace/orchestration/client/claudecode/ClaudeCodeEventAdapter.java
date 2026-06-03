package com.agentspace.orchestration.client.claudecode;

import com.agentspace.orchestration.model.claudecode.ClaudeCodeContentBlock;
import com.agentspace.orchestration.model.claudecode.ClaudeCodeInnerMessage;
import com.agentspace.orchestration.model.claudecode.ClaudeCodeMessage;
import com.agentspace.orchestration.model.event.AgentExecutionEvent;
import com.agentspace.orchestration.model.event.AttemptResultStatus;
import com.agentspace.orchestration.model.event.EventSource;
import com.agentspace.orchestration.model.event.EventTypes;
import com.agentspace.orchestration.model.event.MessageRole;
import com.agentspace.orchestration.model.event.ToolResultStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 Claude Code SDK {@code stream-json} 消息翻译为编排内部统一事件 {@link AgentExecutionEvent}。
 *
 * <p>SDK 消息流不带编排归属，故每次解析针对一个 attempt，归属由 {@link AttemptContext} 提供。
 * 映射关系（见概要设计 §8.4）：
 * <table>
 *   <tr><th>SDK 消息</th><th>内部事件</th><th>类别</th><th>source</th></tr>
 *   <tr><td>system/init</td><td>attempt.started</td><td>control</td><td>AGENT_CORE</td></tr>
 *   <tr><td>assistant·text</td><td>agent.message</td><td>display</td><td>EXECUTOR</td></tr>
 *   <tr><td>assistant·thinking</td><td>agent.thinking</td><td>display</td><td>EXECUTOR</td></tr>
 *   <tr><td>assistant·tool_use</td><td>agent.tool_use</td><td>display</td><td>EXECUTOR</td></tr>
 *   <tr><td>user·tool_result</td><td>agent.tool_result</td><td>display</td><td>EXECUTOR</td></tr>
 *   <tr><td>result</td><td>attempt.result (+ runtime.completed/failed)</td><td>control+runtime</td><td>AGENT_CORE/RUNTIME</td></tr>
 * </table>
 *
 * <p>关键约定：
 * <ul>
 *   <li><b>幂等 eventId</b>：由 attemptId + 消息 id + 块下标确定性派生，断线重连重读同一段流时天然去重，
 *       与 API 中断恢复机制配合（见 operations.md）；</li>
 *   <li><b>thinking 截断</b>：只取摘要，不透传完整 chain-of-thought；</li>
 *   <li><b>tool 输出限长</b>：超限截断，超大内容应改写 artifact；</li>
 *   <li><b>runtime 终态合成</b>：Claude Code 直连作执行器+运行时时，由 {@code result} 行合成
 *       runtime.completed/failed，使 {@code EventIngestService.tryFinalize} 的「result + runtime 两信号」
 *       得以满足；上层另有运行时层时可关闭。</li>
 * </ul>
 */
@Component
public class ClaudeCodeEventAdapter {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeEventAdapter.class);

    private final ClaudeCodeAdapterProperties props;

    public ClaudeCodeEventAdapter(ClaudeCodeAdapterProperties props) {
        this.props = props;
    }

    /**
     * 把一条 SDK 消息翻译为零个或多个内部事件。{@code seq} 提供全流单调递增序号（adapter 不持有状态，
     * 由调用方按 attempt 维护一个 {@link Sequencer}），保证消费方按序展示。
     */
    public List<AgentExecutionEvent> adapt(ClaudeCodeMessage msg, AttemptContext ctx, Sequencer seq) {
        if (msg == null || msg.type() == null) {
            return List.of();
        }
        return switch (msg.type()) {
            case ClaudeCodeMessage.TYPE_SYSTEM -> adaptSystem(msg, ctx, seq);
            case ClaudeCodeMessage.TYPE_ASSISTANT -> adaptAssistant(msg, ctx, seq);
            case ClaudeCodeMessage.TYPE_USER -> adaptUser(msg, ctx, seq);
            case ClaudeCodeMessage.TYPE_RESULT -> adaptResult(msg, ctx, seq);
            case ClaudeCodeMessage.TYPE_STREAM_EVENT -> List.of(); // 增量 delta，MVP 不逐字转发
            default -> {
                log.debug("[claude-code] 未处理的消息类型: {}", msg.type());
                yield List.of();
            }
        };
    }

    private List<AgentExecutionEvent> adaptSystem(ClaudeCodeMessage msg, AttemptContext ctx, Sequencer seq) {
        if (!ClaudeCodeMessage.SUBTYPE_INIT.equals(msg.subtype())) {
            return List.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (msg.sessionId() != null) {
            payload.put("sessionRef", msg.sessionId());
        }
        if (msg.model() != null) {
            payload.put("model", msg.model());
        }
        return List.of(event(EventTypes.ATTEMPT_STARTED, EventSource.AGENT_CORE, ctx, seq,
                "init", 0, payload));
    }

    private List<AgentExecutionEvent> adaptAssistant(ClaudeCodeMessage msg, AttemptContext ctx, Sequencer seq) {
        ClaudeCodeInnerMessage inner = msg.message();
        if (inner == null || inner.content() == null) {
            return List.of();
        }
        String messageId = inner.id() != null ? inner.id() : "asst";
        List<AgentExecutionEvent> out = new ArrayList<>();
        int idx = 0;
        for (ClaudeCodeContentBlock block : inner.content()) {
            AgentExecutionEvent ev = adaptAssistantBlock(block, ctx, seq, messageId, idx++);
            if (ev != null) {
                out.add(ev);
            }
        }
        return out;
    }

    private AgentExecutionEvent adaptAssistantBlock(ClaudeCodeContentBlock block, AttemptContext ctx,
                                                    Sequencer seq, String messageId, int idx) {
        if (block == null || block.type() == null) {
            return null;
        }
        return switch (block.type()) {
            case ClaudeCodeContentBlock.TYPE_TEXT -> {
                if (isBlank(block.text())) {
                    yield null;
                }
                yield event(EventTypes.AGENT_MESSAGE, EventSource.EXECUTOR, ctx, seq, messageId, idx,
                        Map.of("role", MessageRole.ASSISTANT.name(), "text", block.text()));
            }
            case ClaudeCodeContentBlock.TYPE_THINKING -> {
                if (isBlank(block.thinking())) {
                    yield null;
                }
                yield event(EventTypes.AGENT_THINKING, EventSource.EXECUTOR, ctx, seq, messageId, idx,
                        Map.of("summary", truncate(block.thinking(), props.thinkingSummaryMaxChars())));
            }
            case ClaudeCodeContentBlock.TYPE_TOOL_USE -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("toolCallId", block.id());
                payload.put("toolName", block.name());
                payload.put("input", block.input() != null ? block.input() : Map.of());
                yield event(EventTypes.AGENT_TOOL_USE, EventSource.EXECUTOR, ctx, seq, messageId, idx, payload);
            }
            default -> {
                log.debug("[claude-code] assistant 中未处理的块类型: {}", block.type());
                yield null;
            }
        };
    }

    private List<AgentExecutionEvent> adaptUser(ClaudeCodeMessage msg, AttemptContext ctx, Sequencer seq) {
        ClaudeCodeInnerMessage inner = msg.message();
        if (inner == null || inner.content() == null) {
            return List.of();
        }
        String messageId = inner.id() != null ? inner.id() : "user";
        List<AgentExecutionEvent> out = new ArrayList<>();
        int idx = 0;
        for (ClaudeCodeContentBlock block : inner.content()) {
            int blockIdx = idx++;
            if (block == null || !ClaudeCodeContentBlock.TYPE_TOOL_RESULT.equals(block.type())) {
                continue; // user 消息里只关心 tool_result
            }
            boolean isError = Boolean.TRUE.equals(block.isError());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("toolCallId", block.toolUseId());
            payload.put("status", (isError ? ToolResultStatus.ERROR : ToolResultStatus.SUCCESS).name());
            String output = truncate(stringifyContent(block.content()), props.toolOutputMaxChars());
            if (isError) {
                payload.put("errorMessage", output);
            } else {
                payload.put("output", output);
            }
            out.add(event(EventTypes.AGENT_TOOL_RESULT, EventSource.EXECUTOR, ctx, seq, messageId, blockIdx, payload));
        }
        return out;
    }

    private List<AgentExecutionEvent> adaptResult(ClaudeCodeMessage msg, AttemptContext ctx, Sequencer seq) {
        boolean error = Boolean.TRUE.equals(msg.isError())
                || (msg.subtype() != null && !ClaudeCodeMessage.SUBTYPE_SUCCESS.equals(msg.subtype()));
        AttemptResultStatus status = error ? AttemptResultStatus.FAILED : AttemptResultStatus.SUCCEEDED;

        Map<String, Object> resultPayload = new LinkedHashMap<>();
        resultPayload.put("status", status.name());
        if (!error) {
            resultPayload.put("summary", truncate(msg.result(), props.toolOutputMaxChars()));
            resultPayload.put("result", msg.result());
        } else {
            resultPayload.put("errorCode", msg.subtype() != null ? msg.subtype() : "EXECUTOR_FAILED");
            resultPayload.put("errorMessage", truncate(msg.result(), props.toolOutputMaxChars()));
        }
        resultPayload.put("sessionRef", msg.sessionId());
        resultPayload.put("artifactRefs", List.of());

        List<AgentExecutionEvent> out = new ArrayList<>();
        out.add(event(EventTypes.ATTEMPT_RESULT, EventSource.AGENT_CORE, ctx, seq, "result", 0, resultPayload));

        if (props.synthesizeRuntimeTerminal()) {
            String runtimeType = error ? EventTypes.RUNTIME_FAILED : EventTypes.RUNTIME_COMPLETED;
            Map<String, Object> rt = error
                    ? Map.of("failureReason", "EXECUTOR_FAILED")
                    : Map.of();
            out.add(event(runtimeType, EventSource.RUNTIME, ctx, seq, "result", 1, rt));
        }
        return out;
    }

    /**
     * tool_result 的 content 既可能是纯字符串，也可能是 {@code [{type:text,text:...}]} 块数组，
     * 这里归一为单个文本字符串。
     */
    private String stringifyContent(JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode node : content) {
                JsonNode text = node.get("text");
                if (text != null && text.isTextual()) {
                    sb.append(text.asText());
                } else {
                    sb.append(node.toString());
                }
            }
            return sb.toString();
        }
        return content.toString();
    }

    private AgentExecutionEvent event(String type, EventSource source, AttemptContext ctx, Sequencer seq,
                                      String messageId, int blockIdx, Map<String, Object> payload) {
        return new AgentExecutionEvent(
                deterministicEventId(ctx.attemptId(), messageId, blockIdx, type),
                type,
                ctx.runId(),
                ctx.stepId(),
                ctx.stepKey(),
                ctx.attemptId(),
                ctx.attemptNo(),
                Instant.now(),
                seq.next(),
                source,
                payload);
    }

    /**
     * 确定性 eventId：同一条 SDK 消息的同一块在重读时得到相同 eventId，使 EventIngestService 幂等去重。
     * 含 eventType 以区分由同一 result 行派生的 attempt.result 与 runtime.* 两个事件。
     *
     * <p>对复合键取 SHA-256 截断为定长 hex，保证落在 {@code event_id VARCHAR(64)} 列内
     * （{@code cc-} 前缀 + 40 hex = 43 字符），同时保持确定性与足够的抗碰撞强度。
     */
    private String deterministicEventId(String attemptId, String messageId, int blockIdx, String type) {
        String composite = attemptId + "|" + messageId + "|" + blockIdx + "|" + type;
        return "cc-" + sha256Hex(composite).substring(0, 40);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e); // 标准 JRE 必有
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…[truncated " + (s.length() - max) + " chars]";
    }

    /**
     * attempt 维度的单调序号发生器。adapter 本身无状态，调用方为每个 attempt 持有一个实例，
     * 保证跨多条 SDK 消息派生出的事件 sequence 全局递增（消费方据此有序展示）。
     */
    public static final class Sequencer {
        private long current;

        public Sequencer() {
            this(0);
        }

        public Sequencer(long start) {
            this.current = start;
        }

        public long next() {
            return ++current;
        }
    }
}
