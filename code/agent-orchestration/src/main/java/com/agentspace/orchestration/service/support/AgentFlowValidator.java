package com.agentspace.orchestration.service.support;

import com.agentspace.orchestration.model.flow.AgentFlow;
import com.agentspace.orchestration.model.flow.AgentFlowEdge;
import com.agentspace.orchestration.model.flow.AgentFlowStep;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.agentspace.orchestration.service.exception.AgentFlowValidationException;

/**
 * AgentFlow 的结构校验：在 Bean Validation（字段非空）之外，补充 DAG 层面的语义校验。
 * 见详细设计 §2.1（422 触发条件）、概要设计 §6。
 *
 * <p>校验项：
 * <ul>
 *   <li>step id 唯一（不重复）；</li>
 *   <li>edge 的 from / to 必须引用已存在的 step；</li>
 *   <li>禁止自环（from == to）；</li>
 *   <li>禁止有环（DAG 无环）。</li>
 * </ul>
 */
@Component
public class AgentFlowValidator {

    /**
     * 校验 AgentFlow 的 DAG 结构。通过则正常返回，否则抛 {@link AgentFlowValidationException}。
     */
    public void validate(AgentFlow flow) {
        List<String> violations = new ArrayList<>();

        List<AgentFlowStep> steps = flow.steps() == null ? List.of() : flow.steps();
        List<AgentFlowEdge> edges = flow.edges() == null ? List.of() : flow.edges();

        Set<String> stepIds = collectStepIds(steps, violations);
        validateEdges(edges, stepIds, violations);

        // 仅在 edge 引用合法时检测环，避免对非法引用产生误导性的环报告
        if (violations.isEmpty()) {
            detectCycle(stepIds, edges, violations);
        }

        if (!violations.isEmpty()) {
            throw new AgentFlowValidationException(violations);
        }
    }

    private Set<String> collectStepIds(List<AgentFlowStep> steps, List<String> violations) {
        Set<String> seen = new HashSet<>();
        for (AgentFlowStep step : steps) {
            String id = step.id();
            if (id == null || id.isBlank()) {
                violations.add("存在 step.id 为空");
                continue;
            }
            if (!seen.add(id)) {
                violations.add("step id 重复: " + id);
            }
        }
        return seen;
    }

    private void validateEdges(List<AgentFlowEdge> edges, Set<String> stepIds, List<String> violations) {
        for (AgentFlowEdge edge : edges) {
            String from = edge.from();
            String to = edge.to();
            if (from != null && from.equals(to)) {
                violations.add("禁止自环: " + from);
            }
            if (from != null && !stepIds.contains(from)) {
                violations.add("edge.from 引用了不存在的 step: " + from);
            }
            if (to != null && !stepIds.contains(to)) {
                violations.add("edge.to 引用了不存在的 step: " + to);
            }
        }
    }

    /**
     * Kahn 拓扑排序检测环：无法排空的节点即处于环中。
     */
    private void detectCycle(Set<String> stepIds, List<AgentFlowEdge> edges, List<String> violations) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        for (String id : stepIds) {
            inDegree.put(id, 0);
            adjacency.put(id, new ArrayList<>());
        }
        for (AgentFlowEdge edge : edges) {
            adjacency.get(edge.from()).add(edge.to());
            inDegree.merge(edge.to(), 1, Integer::sum);
        }

        List<String> queue = new ArrayList<>();
        inDegree.forEach((id, deg) -> {
            if (deg == 0) {
                queue.add(id);
            }
        });

        int visited = 0;
        while (!queue.isEmpty()) {
            String node = queue.remove(queue.size() - 1);
            visited++;
            for (String next : adjacency.get(node)) {
                if (inDegree.merge(next, -1, Integer::sum) == 0) {
                    queue.add(next);
                }
            }
        }

        if (visited < stepIds.size()) {
            violations.add("AgentFlow DAG 存在环");
        }
    }
}
