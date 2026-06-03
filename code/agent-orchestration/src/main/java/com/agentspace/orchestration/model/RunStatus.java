package com.agentspace.orchestration.model;

/**
 * WorkflowRun 状态。见详细设计 §3.3。
 */
public enum RunStatus {
    PENDING,
    RUNNING,
    SUSPENDED,
    COMPLETED,
    FAILED,
    CANCELLING,
    CANCELLED;

    /** 终态：不再产生新事件，前端可停止轮询。 */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
