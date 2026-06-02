package com.agentspace.orchestration.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptRendererTest {

    private final PromptRenderer renderer = new PromptRenderer();

    @Test
    void replacesKnownPlaceholders() {
        String out = renderer.render("分析 {{task.title}} for {{project.name}}",
                Map.of("task.title", "登录", "project.name", "AgentSpace"));
        assertThat(out).isEqualTo("分析 登录 for AgentSpace");
    }

    @Test
    void toleratesWhitespaceInPlaceholder() {
        String out = renderer.render("{{ task.title }}", Map.of("task.title", "X"));
        assertThat(out).isEqualTo("X");
    }

    @Test
    void throwsOnUnknownVariable() {
        assertThatThrownBy(() -> renderer.render("{{steps.ghost.summary}}", Map.of()))
                .isInstanceOf(PromptRenderException.class)
                .hasMessageContaining("steps.ghost.summary");
    }

    @Test
    void returnsTemplateWhenNoPlaceholder() {
        assertThat(renderer.render("纯文本", Map.of())).isEqualTo("纯文本");
    }
}
