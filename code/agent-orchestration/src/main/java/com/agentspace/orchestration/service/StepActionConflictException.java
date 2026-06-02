package com.agentspace.orchestration.service;

/**
 * suspended/failed step 操作的状态前置不满足（非 SUSPENDED/FAILED、run 已 CANCELLING 等）。映射 409。
 * 见详细设计 §2.3–2.5、§3.4。
 */
public class StepActionConflictException extends RuntimeException {

    public StepActionConflictException(String message) {
        super(message);
    }
}
