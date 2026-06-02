package com.agentspace.orchestration.client.dto;

import com.agentspace.orchestration.model.ExecutorType;
import com.agentspace.orchestration.model.flow.CredentialRefs;
import com.agentspace.orchestration.model.flow.RepoRef;
import com.agentspace.orchestration.model.flow.WorkspaceRef;

import java.util.List;

/**
 * Agent Core StartAttempt 请求。承载单个 attempt 执行所需的全部输入。见概要设计 §8.1(1)。
 *
 * <p>同一接口同时承载「新建对话」与「续聊」：{@code resumeFromSessionRef} 为空表示新建，
 * 非空表示在已有对话上续聊（Agent Core 用 --resume 重载，prompt 作为本轮追加输入）。
 *
 * <p>Agent 配置 / skill / MCP / 知识库均以引用方式传入，由 Agent Core 按引用拉取并组装执行环境。
 */
public record StartAttemptRequest(
        // 标识与幂等
        String runId,
        String stepId,
        String stepKey,
        String attemptId,
        int attemptNo,

        // 执行器与已渲染 prompt
        ExecutorType executorType,
        String renderedPrompt,

        // Agent 配置与能力包（引用）
        String agentSnapshotRef,
        List<String> skillSnapshotRefs,
        List<String> mcpSnapshotRefs,
        List<String> knowledgeBaseRefs,

        // 工作目录与代码仓
        WorkspaceRef workspace,
        RepoRef repo,

        // 凭证引用
        CredentialRefs credentials,

        // 输出通道（事件 / stdout / stderr / heartbeat 的 stream keys）
        String eventStreamKey,
        String logStreamKey,

        // 续聊（可选）：指向要恢复的对话上下文
        String resumeFromSessionRef
) {
}
