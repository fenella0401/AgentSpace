package com.agentspace.orchestration.model.event;

/**
 * MVP 事件类型常量及其类别归属。见概要设计 §8.4。
 *
 * <p>事件类型在 envelope 中以字符串 {@code eventType} 承载；此处集中定义常量，
 * 并提供 {@link #categoryOf(String)} 用于按类型判定 {@link EventCategory}。
 */
public final class EventTypes {

    private EventTypes() {
    }

    // control
    public static final String ATTEMPT_STARTED = "attempt.started";
    public static final String ATTEMPT_HEARTBEAT = "attempt.heartbeat";
    public static final String ATTEMPT_RESULT = "attempt.result";

    // display
    public static final String AGENT_THINKING = "agent.thinking";
    public static final String AGENT_MESSAGE = "agent.message";
    public static final String AGENT_TOOL_USE = "agent.tool_use";
    public static final String AGENT_TOOL_RESULT = "agent.tool_result";
    public static final String EXECUTOR_STDOUT = "executor.stdout";
    public static final String EXECUTOR_STDERR = "executor.stderr";

    // runtime
    public static final String RUNTIME_ATTEMPT_CREATED = "runtime.attempt_created";
    public static final String RUNTIME_RUNNING = "runtime.running";
    public static final String RUNTIME_COMPLETED = "runtime.completed";
    public static final String RUNTIME_FAILED = "runtime.failed";
    public static final String RUNTIME_CANCELLED = "runtime.cancelled";

    /**
     * 按事件类型判定类别；未知类型归为 {@link EventCategory#DISPLAY}（透传，不推进状态机）。
     */
    public static EventCategory categoryOf(String eventType) {
        if (eventType == null) {
            return EventCategory.DISPLAY;
        }
        return switch (eventType) {
            case ATTEMPT_STARTED, ATTEMPT_HEARTBEAT, ATTEMPT_RESULT -> EventCategory.CONTROL;
            case RUNTIME_ATTEMPT_CREATED, RUNTIME_RUNNING, RUNTIME_COMPLETED,
                 RUNTIME_FAILED, RUNTIME_CANCELLED -> EventCategory.RUNTIME;
            default -> EventCategory.DISPLAY;
        };
    }
}
