package com.agentspace.orchestration.service;

/**
 * Prompt 渲染失败（引用不存在的变量 / 未完成上游 step 输出）。对应 attempt failure_reason=PROMPT_RENDER_ERROR。
 * 见概要设计 §7.1。
 */
public class PromptRenderException extends RuntimeException {

    public PromptRenderException(String message) {
        super(message);
    }
}
