package com.agentspace.orchestration.service.statemachine;

import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StateTransitionsTest {

    @Test
    void attemptLegalTransitions() {
        assertThat(StateTransitions.canTransition(AttemptStatus.PENDING, AttemptStatus.STARTING)).isTrue();
        assertThat(StateTransitions.canTransition(AttemptStatus.STARTING, AttemptStatus.RUNNING)).isTrue();
        assertThat(StateTransitions.canTransition(AttemptStatus.RUNNING, AttemptStatus.SUCCEEDED)).isTrue();
        assertThat(StateTransitions.canTransition(AttemptStatus.RUNNING, AttemptStatus.FAILED)).isTrue();
    }

    @Test
    void attemptIllegalTransitions() {
        assertThat(StateTransitions.canTransition(AttemptStatus.PENDING, AttemptStatus.SUCCEEDED)).isFalse();
        assertThat(StateTransitions.canTransition(AttemptStatus.SUCCEEDED, AttemptStatus.RUNNING)).isFalse();
        assertThat(StateTransitions.canTransition(AttemptStatus.FAILED, AttemptStatus.SUCCEEDED)).isFalse();
    }

    @Test
    void stepRunningToRunningAllowedForRetry() {
        assertThat(StateTransitions.canTransition(StepStatus.RUNNING, StepStatus.RUNNING)).isTrue();
        assertThat(StateTransitions.canTransition(StepStatus.RUNNING, StepStatus.COMPLETED)).isTrue();
        assertThat(StateTransitions.canTransition(StepStatus.RUNNING, StepStatus.SUSPENDED)).isTrue();
        assertThat(StateTransitions.canTransition(StepStatus.FAILED, StepStatus.RUNNING)).isTrue();
    }

    @Test
    void stepIllegalTransitions() {
        assertThat(StateTransitions.canTransition(StepStatus.PENDING, StepStatus.RUNNING)).isFalse();
        assertThat(StateTransitions.canTransition(StepStatus.COMPLETED, StepStatus.RUNNING)).isFalse();
    }

    @Test
    void runTransitionsAndTerminal() {
        assertThat(StateTransitions.canTransition(RunStatus.PENDING, RunStatus.RUNNING)).isTrue();
        assertThat(StateTransitions.canTransition(RunStatus.RUNNING, RunStatus.SUSPENDED)).isTrue();
        assertThat(StateTransitions.canTransition(RunStatus.CANCELLING, RunStatus.CANCELLED)).isTrue();
        assertThat(StateTransitions.isTerminal(RunStatus.COMPLETED)).isTrue();
        assertThat(StateTransitions.isTerminal(RunStatus.RUNNING)).isFalse();
    }
}
