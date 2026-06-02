package com.agentspace.orchestration.client;

import com.agentspace.orchestration.client.dto.CancelAttemptRequest;
import com.agentspace.orchestration.client.dto.CancelAttemptResponse;
import com.agentspace.orchestration.client.dto.QueryAttemptResponse;
import com.agentspace.orchestration.client.dto.StartAttemptRequest;
import com.agentspace.orchestration.client.dto.StartAttemptResponse;

/**
 * Agent-Orchestration 对 Agent Core 的对接契约（T0.3 冻结）。见概要设计 §8.1。
 *
 * <p>Agent-Orchestration 决定哪个 attempt 该运行 / 取消 / 重试 / 完成；Agent Core 决定如何运行。
 * 真实 HTTP 实现待后续提供；本接口先由 {@code mock} profile 下的实现支撑联调。
 *
 * <p>所有方法须满足幂等：重复 start 返回已有 RuntimeAttempt，重复 cancel 返回明确状态。
 */
public interface AgentCoreClient {

    /**
     * 启动一个 StepAttempt 的运行时执行。重复调用同一 attemptId 幂等返回已有 RuntimeAttempt。
     */
    StartAttemptResponse startAttempt(StartAttemptRequest request);

    /**
     * 按 attemptId 取消对应 RuntimeAttempt。幂等；不存在时返回 runtimeFound=false。
     */
    CancelAttemptResponse cancelAttempt(CancelAttemptRequest request);

    /**
     * 按 attemptId 查询 RuntimeAttempt 状态，用于重启恢复 / 对账。
     */
    QueryAttemptResponse queryAttempt(String attemptId);
}
