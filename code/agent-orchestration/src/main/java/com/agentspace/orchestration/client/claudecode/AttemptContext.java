package com.agentspace.orchestration.client.claudecode;

/**
 * 一个 attempt 的归属上下文。Claude Code SDK 的 {@code stream-json} 输出本身只带 session_id，
 * 不含编排侧的 runId / stepId / attemptId，故由调用方在解析每个 attempt 的事件流时提供。
 *
 * <p>adapter 据此填充 {@link com.agentspace.orchestration.model.event.AgentExecutionEvent} 的归属字段，
 * 入站后由 {@code EventIngestService} 做归属校验。
 *
 * @param runId      所属 run
 * @param stepId     所属 step
 * @param stepKey    step 业务键
 * @param attemptId  所属 attempt（事件归属与去重的核心键）
 * @param attemptNo  attempt 序号
 */
public record AttemptContext(
        String runId,
        String stepId,
        String stepKey,
        String attemptId,
        int attemptNo
) {
}
