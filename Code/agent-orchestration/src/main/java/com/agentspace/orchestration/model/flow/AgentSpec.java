package com.agentspace.orchestration.model.flow;

import com.agentspace.orchestration.model.ExecutorType;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Step 的 Agent 规格（执行器类型与各类快照引用）。见概要设计 §6。
 */
public record AgentSpec(
        @NotNull ExecutorType executorType,
        String agentSnapshotRef,
        List<String> skillSnapshotRefs,
        List<String> mcpSnapshotRefs
) {
}
