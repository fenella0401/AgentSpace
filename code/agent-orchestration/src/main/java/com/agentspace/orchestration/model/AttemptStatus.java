package com.agentspace.orchestration.model;

/**
 * StepAttempt 状态。见详细设计 §3.1。
 */
public enum AttemptStatus {
    PENDING,
    STARTING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
