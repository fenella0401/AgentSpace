/**
 * 业务逻辑层。按职责分子包：
 * <ul>
 *   <li>{@code run}：run/step/attempt 生命周期——启动、调度、状态推进、自动重试、续聊、取消、对账；</li>
 *   <li>{@code event}：入站事件接入与实时分发；</li>
 *   <li>{@code support}：AgentFlow 编解码/校验、DAG 工具、prompt 渲染等无状态工具；</li>
 *   <li>{@code exception}：业务异常（由 controller 的 ApiExceptionHandler 映射为 HTTP 错误码）；</li>
 *   <li>{@code statemachine}：合法状态转换表。</li>
 * </ul>
 * 顶层保留 {@link com.agentspace.orchestration.service.OrchestrationMetrics}（可观测性）。
 * 状态写入全程走 CAS（乐观锁）+ 事务。
 */
package com.agentspace.orchestration.service;
