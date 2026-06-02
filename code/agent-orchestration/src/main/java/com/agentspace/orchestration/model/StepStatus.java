package com.agentspace.orchestration.model;

/**
 * WorkflowStep 状态。见详细设计 §3.2。
 */
public enum StepStatus {
    PENDING,
    READY,
    RUNNING,
    SUSPENDED,
    COMPLETED,
    FAILED,
    CANCELLED
}
