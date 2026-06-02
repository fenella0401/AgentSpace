package com.agentspace.orchestration.model.flow;

/**
 * Workspace 引用与挂载信息。云端 workspace 初始为空卷，由 Agent Core 准备。见概要设计 §6。
 */
public record WorkspaceRef(
        String workspaceRef,
        String mountPath,
        String leaseId
) {
}
