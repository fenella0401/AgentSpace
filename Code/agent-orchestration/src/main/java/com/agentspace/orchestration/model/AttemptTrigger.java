package com.agentspace.orchestration.model;

/**
 * StepAttempt 触发来源。见详细设计 §1.4。
 */
public enum AttemptTrigger {
    INITIAL,
    AUTO_RETRY,
    MANUAL_RETRY,
    CONTINUE
}
