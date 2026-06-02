package com.agentspace.orchestration.service.statemachine;

import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 三级状态机的合法转换表。见详细设计 §3.1–3.3、概要设计 §8.5–8.6。
 *
 * <p>仅定义「从某状态可达哪些状态」，CAS + 事务由各状态机服务负责；本类只判合法性。
 */
public final class StateTransitions {

    private StateTransitions() {
    }

    private static final Map<AttemptStatus, Set<AttemptStatus>> ATTEMPT = new EnumMap<>(AttemptStatus.class);
    private static final Map<StepStatus, Set<StepStatus>> STEP = new EnumMap<>(StepStatus.class);
    private static final Map<RunStatus, Set<RunStatus>> RUN = new EnumMap<>(RunStatus.class);

    static {
        // StepAttempt（§3.1）
        ATTEMPT.put(AttemptStatus.PENDING, EnumSet.of(AttemptStatus.STARTING, AttemptStatus.CANCELLED));
        ATTEMPT.put(AttemptStatus.STARTING, EnumSet.of(AttemptStatus.RUNNING, AttemptStatus.FAILED, AttemptStatus.CANCELLED));
        ATTEMPT.put(AttemptStatus.RUNNING, EnumSet.of(AttemptStatus.SUCCEEDED, AttemptStatus.FAILED, AttemptStatus.CANCELLED));
        ATTEMPT.put(AttemptStatus.SUCCEEDED, EnumSet.noneOf(AttemptStatus.class));
        ATTEMPT.put(AttemptStatus.FAILED, EnumSet.noneOf(AttemptStatus.class));
        ATTEMPT.put(AttemptStatus.CANCELLED, EnumSet.noneOf(AttemptStatus.class));

        // WorkflowStep（§3.2）：FAILED 可经手动 retry 回 RUNNING，故非严格终态
        STEP.put(StepStatus.PENDING, EnumSet.of(StepStatus.READY, StepStatus.CANCELLED));
        STEP.put(StepStatus.READY, EnumSet.of(StepStatus.RUNNING, StepStatus.CANCELLED));
        STEP.put(StepStatus.RUNNING, EnumSet.of(StepStatus.COMPLETED, StepStatus.SUSPENDED,
                StepStatus.RUNNING, StepStatus.FAILED, StepStatus.CANCELLED));
        STEP.put(StepStatus.SUSPENDED, EnumSet.of(StepStatus.COMPLETED, StepStatus.RUNNING, StepStatus.CANCELLED));
        STEP.put(StepStatus.FAILED, EnumSet.of(StepStatus.RUNNING, StepStatus.CANCELLED));
        STEP.put(StepStatus.COMPLETED, EnumSet.noneOf(StepStatus.class));
        STEP.put(StepStatus.CANCELLED, EnumSet.noneOf(StepStatus.class));

        // WorkflowRun（§3.3）
        RUN.put(RunStatus.PENDING, EnumSet.of(RunStatus.RUNNING, RunStatus.CANCELLING));
        RUN.put(RunStatus.RUNNING, EnumSet.of(RunStatus.SUSPENDED, RunStatus.COMPLETED,
                RunStatus.FAILED, RunStatus.CANCELLING));
        RUN.put(RunStatus.SUSPENDED, EnumSet.of(RunStatus.RUNNING, RunStatus.COMPLETED,
                RunStatus.FAILED, RunStatus.CANCELLING));
        RUN.put(RunStatus.CANCELLING, EnumSet.of(RunStatus.CANCELLED));
        RUN.put(RunStatus.COMPLETED, EnumSet.noneOf(RunStatus.class));
        RUN.put(RunStatus.FAILED, EnumSet.noneOf(RunStatus.class));
        RUN.put(RunStatus.CANCELLED, EnumSet.noneOf(RunStatus.class));
    }

    public static boolean canTransition(AttemptStatus from, AttemptStatus to) {
        return from == to ? false : ATTEMPT.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean canTransition(StepStatus from, StepStatus to) {
        // RUNNING→RUNNING 合法（自动重试循环）
        if (from == StepStatus.RUNNING && to == StepStatus.RUNNING) {
            return true;
        }
        return from == to ? false : STEP.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean canTransition(RunStatus from, RunStatus to) {
        return from == to ? false : RUN.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean isTerminal(AttemptStatus s) {
        return s == AttemptStatus.SUCCEEDED || s == AttemptStatus.FAILED || s == AttemptStatus.CANCELLED;
    }

    public static boolean isTerminal(RunStatus s) {
        return s == RunStatus.COMPLETED || s == RunStatus.FAILED || s == RunStatus.CANCELLED;
    }
}
