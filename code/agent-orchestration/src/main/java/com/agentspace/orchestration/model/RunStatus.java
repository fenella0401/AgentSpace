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
    CANCELLED
}
