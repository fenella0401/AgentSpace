package com.agentspace.orchestration.service;

/**
 * run/step 不存在。映射 404。见详细设计 §2.3–2.5。
 */
public class StepActionNotFoundException extends RuntimeException {

    public StepActionNotFoundException(String message) {
        super(message);
    }
}
