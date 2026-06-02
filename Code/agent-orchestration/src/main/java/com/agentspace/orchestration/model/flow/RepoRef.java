package com.agentspace.orchestration.model.flow;

/**
 * 代码仓引用。Agent Core 在 attempt 启动时 clone / checkout 进 workspace。见概要设计 §6。
 */
public record RepoRef(
        String repoUrl,
        String branch,
        String commit
) {
}
