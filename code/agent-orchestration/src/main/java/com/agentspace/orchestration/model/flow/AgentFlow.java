package com.agentspace.orchestration.model.flow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * AgentFlow：Agent-Management 启动 run 时生成的不可变执行快照。见概要设计 §6。
 *
 * <p>Agent-Orchestration 持久化并执行 AgentFlow，不受后续模板或 Agent 配置变更影响。
 * 不含底层运行时字段、资源规格、镜像、节点、容器、进程、密钥等信息。
 */
public record AgentFlow(
        @NotBlank String schemaVersion,
        @NotBlank String flowId,
        String flowName,
        @NotBlank String flowSnapshotId,
        @NotNull @Valid RunRef run,
        @NotNull @Valid TenantRef tenant,
        @Valid TaskRef task,
        @Valid WorkspaceRef workspace,
        @Valid RepoRef repo,
        @Valid CredentialRefs credentials,
        Map<String, Object> variables,
        @NotEmpty @Valid List<AgentFlowStep> steps,
        @Valid List<AgentFlowEdge> edges
) {
}
