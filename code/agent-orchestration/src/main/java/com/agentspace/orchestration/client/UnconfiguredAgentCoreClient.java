package com.agentspace.orchestration.client;

import com.agentspace.orchestration.client.dto.CancelAttemptRequest;
import com.agentspace.orchestration.client.dto.CancelAttemptResponse;
import com.agentspace.orchestration.client.dto.QueryAttemptResponse;
import com.agentspace.orchestration.client.dto.StartAttemptRequest;
import com.agentspace.orchestration.client.dto.StartAttemptResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 非 mock profile 下的占位 Agent Core 客户端：真实 HTTP 实现待后续提供（FE 之外）。
 *
 * <p>当前所有调用抛 {@link UnsupportedOperationException}，使默认 profile 可装配启动
 * （健康检查、查询等只读路径可用），但实际启动 attempt 需 {@code mock} profile 或真实实现。
 */
@Component
@Profile("!mock")
public class UnconfiguredAgentCoreClient implements AgentCoreClient {

    private static final String MSG =
            "Agent Core 客户端未配置：请用 mock profile 联调，或接入真实 Agent Core 实现";

    @Override
    public StartAttemptResponse startAttempt(StartAttemptRequest request) {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public CancelAttemptResponse cancelAttempt(CancelAttemptRequest request) {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public QueryAttemptResponse queryAttempt(String attemptId) {
        throw new UnsupportedOperationException(MSG);
    }
}
