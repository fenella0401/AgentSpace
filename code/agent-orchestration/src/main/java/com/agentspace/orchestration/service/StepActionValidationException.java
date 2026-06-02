package com.agentspace.orchestration.service;

/**
 * step 操作参数校验失败（如 continue 的 feedback 为空）。映射 422。见详细设计 §2.4。
 */
public class StepActionValidationException extends RuntimeException {

    public StepActionValidationException(String message) {
        super(message);
    }
}
