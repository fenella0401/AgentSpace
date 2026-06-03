/**
 * 外部客户端：调用 Agent Core 的 StartAttempt / CancelAttempt / QueryAttempt。
 * 子包 {@code claudecode} 把 Claude Code SDK stream-json 翻译为内部事件。
 * （单存储：状态/历史不回流 Agent-Management，由其经 GET /runs/{id} 主动查询。）
 */
package com.agentspace.orchestration.client;
