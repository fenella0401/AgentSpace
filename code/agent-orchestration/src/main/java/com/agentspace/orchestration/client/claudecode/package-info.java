/**
 * Claude Code SDK 接入：把 {@code stream-json} 输出（system/assistant/user/result 消息）
 * 翻译为编排内部统一事件 {@link com.agentspace.orchestration.model.event.AgentExecutionEvent}，
 * 投递给 {@link com.agentspace.orchestration.event.EventSink}。见概要设计 §8.4。
 */
package com.agentspace.orchestration.client.claudecode;
