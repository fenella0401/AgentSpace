package com.agentspace.orchestration.service.support;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.agentspace.orchestration.service.exception.PromptRenderException;

/**
 * Prompt 渲染器：只做简单 {@code {{path}}} 变量替换，不支持条件 / 循环 / 函数 / 脚本。见概要设计 §7.1。
 *
 * <p>渲染上下文是一个扁平 map，键为点路径（如 {@code task.title}、{@code project.name}、
 * {@code steps.analyze.summary}），由调用方组装：
 * <ol>
 *   <li>{@code AgentFlow.variables}（全局静态变量）；</li>
 *   <li>{@code AgentFlowStep.prompt.variables}（step 局部变量）；</li>
 *   <li>已完成上游 step 的结构化输出（展开为 {@code steps.<key>.summary/result}）。</li>
 * </ol>
 *
 * <p>引用不存在的键 → 抛 {@link PromptRenderException}（PROMPT_RENDER_ERROR）。
 */
@Component
public class PromptRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^}\\s]+)\\s*}}");

    /**
     * 用 context 渲染 template。遇到未知占位符抛 {@link PromptRenderException}。
     */
    public String render(String template, Map<String, String> context) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = context.get(key);
            if (value == null) {
                throw new PromptRenderException("prompt 引用了不存在的变量: " + key);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
