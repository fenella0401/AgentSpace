package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.ExecutorType;
import com.agentspace.orchestration.model.flow.AgentFlow;
import com.agentspace.orchestration.model.flow.AgentFlowEdge;
import com.agentspace.orchestration.model.flow.AgentFlowStep;
import com.agentspace.orchestration.model.flow.AgentSpec;
import com.agentspace.orchestration.model.flow.PromptSpec;
import com.agentspace.orchestration.model.flow.RunRef;
import com.agentspace.orchestration.model.flow.TenantRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class AgentFlowValidatorTest {

    private final AgentFlowValidator validator = new AgentFlowValidator();

    private AgentFlowStep step(String id) {
        return new AgentFlowStep(id, id,
                new AgentSpec(ExecutorType.CLAUDE_CODE, "agent-ref", List.of(), List.of()),
                new PromptSpec("do {{x}}", Map.of()),
                false);
    }

    private AgentFlow flow(List<AgentFlowStep> steps, List<AgentFlowEdge> edges) {
        return new AgentFlow("1", "flow-1", "f", "snap-1",
                new RunRef("run-1", "idem-1"),
                new TenantRef("team-1", "user-1"),
                null, null, null, null, Map.of(), steps, edges);
    }

    @Test
    void acceptsValidLinearDag() {
        AgentFlow flow = flow(
                List.of(step("a"), step("b"), step("c")),
                List.of(new AgentFlowEdge("a", "b"), new AgentFlowEdge("b", "c")));
        validator.validate(flow); // 不抛异常即通过
    }

    @Test
    void rejectsDuplicateStepId() {
        AgentFlow flow = flow(
                List.of(step("a"), step("a")),
                List.of());
        assertThatThrownBy(() -> validator.validate(flow))
                .isInstanceOf(AgentFlowValidationException.class)
                .hasMessageContaining("step id 重复: a");
    }

    @Test
    void rejectsSelfLoop() {
        AgentFlow flow = flow(
                List.of(step("a")),
                List.of(new AgentFlowEdge("a", "a")));
        var ex = catchThrowableOfType(() -> validator.validate(flow), AgentFlowValidationException.class);
        assertThat(ex.violations()).anyMatch(v -> v.contains("自环"));
    }

    @Test
    void rejectsEdgeReferencingMissingStep() {
        AgentFlow flow = flow(
                List.of(step("a")),
                List.of(new AgentFlowEdge("a", "ghost")));
        var ex = catchThrowableOfType(() -> validator.validate(flow), AgentFlowValidationException.class);
        assertThat(ex.violations()).anyMatch(v -> v.contains("ghost"));
    }

    @Test
    void rejectsCycle() {
        AgentFlow flow = flow(
                List.of(step("a"), step("b"), step("c")),
                List.of(new AgentFlowEdge("a", "b"),
                        new AgentFlowEdge("b", "c"),
                        new AgentFlowEdge("c", "a")));
        var ex = catchThrowableOfType(() -> validator.validate(flow), AgentFlowValidationException.class);
        assertThat(ex.violations()).anyMatch(v -> v.contains("环"));
    }
}
