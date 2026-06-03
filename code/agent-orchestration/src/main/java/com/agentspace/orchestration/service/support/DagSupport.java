package com.agentspace.orchestration.service.support;

import com.agentspace.orchestration.model.flow.AgentFlowEdge;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * DAG 计算工具：基于 edges 计算 ready（无未完成上游）的 step。纯函数，供 RunService 与调度器复用。
 * 见概要设计 §7.3（固定 DAG、串行调度）。
 */
public final class DagSupport {

    private DagSupport() {
    }

    /**
     * 计算初始 ready step：没有任何上游（入度为 0）的 step key。
     *
     * @param stepKeys 全部 step key（保持插入序，用于稳定的 order）
     * @param edges    DAG 边
     * @return 入度为 0 的 step key 集合（保持输入顺序）
     */
    public static Set<String> initialReady(List<String> stepKeys, List<AgentFlowEdge> edges) {
        Set<String> hasUpstream = new LinkedHashSet<>();
        if (edges != null) {
            for (AgentFlowEdge edge : edges) {
                hasUpstream.add(edge.to());
            }
        }
        Set<String> ready = new LinkedHashSet<>();
        for (String key : stepKeys) {
            if (!hasUpstream.contains(key)) {
                ready.add(key);
            }
        }
        return ready;
    }
}
