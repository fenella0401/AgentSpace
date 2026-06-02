package com.agentspace.orchestration.model.event;

/**
 * 事件类别，决定 Agent-Orchestration 的处理路径。见概要设计 §8.4。
 *
 * <ul>
 *   <li>{@code CONTROL}：推进状态机（attempt.started/heartbeat/result）；</li>
 *   <li>{@code DISPLAY}：透传给下游展示（thinking/message/tool_use/tool_result/stdout/stderr）；</li>
 *   <li>{@code RUNTIME}：运行环境物理状态（runtime.*）。</li>
 * </ul>
 */
public enum EventCategory {
    CONTROL,
    DISPLAY,
    RUNTIME
}
