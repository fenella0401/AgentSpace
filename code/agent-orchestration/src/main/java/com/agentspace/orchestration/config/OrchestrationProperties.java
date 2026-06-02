package com.agentspace.orchestration.config;

import com.agentspace.orchestration.model.ExecutorType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * 编排运行时配置（前缀 {@code orchestration}）：超时与重试。见详细设计 §3.5、概要设计 §11#2。
 *
 * <p>全局默认 + per-executorType 覆盖；MVP 不支持 per-flow 覆盖。
 *
 * @param maxRetries        单个 step 的自动重试上限（不含首次）
 * @param stepTimeout       step 硬超时（watchdog 用，FE7）
 * @param heartbeatTimeout  心跳丢失阈值（watchdog 用，FE7）
 * @param schedulerPollInterval 调度器轮询间隔
 * @param overrides         per-executorType 覆盖
 */
@ConfigurationProperties(prefix = "orchestration")
public record OrchestrationProperties(
        int maxRetries,
        Duration stepTimeout,
        Duration heartbeatTimeout,
        Duration schedulerPollInterval,
        Map<ExecutorType, ExecutorOverride> overrides,
        Outbox outbox
) {

    public OrchestrationProperties {
        if (maxRetries < 0) {
            maxRetries = 2;
        }
        if (stepTimeout == null) {
            stepTimeout = Duration.ofMinutes(30);
        }
        if (heartbeatTimeout == null) {
            heartbeatTimeout = Duration.ofMinutes(5);
        }
        if (schedulerPollInterval == null) {
            schedulerPollInterval = Duration.ofSeconds(2);
        }
        if (overrides == null) {
            overrides = Map.of();
        }
        if (outbox == null) {
            outbox = new Outbox(0, 0, 0);
        }
    }

    /** per-executorType 覆盖项；字段为空表示沿用全局默认。 */
    public record ExecutorOverride(
            Integer maxRetries,
            Duration stepTimeout,
            Duration heartbeatTimeout
    ) {
    }

    /**
     * outbox 回流与背压配置。
     *
     * @param maxRetries        投递失败的最大重试次数
     * @param backoffBaseSeconds 指数退避基数（next = base * 2^retry）
     * @param maxPending        PENDING 积压阈值；超过则展示类事件降采样（不再入 outbox）
     */
    public record Outbox(int maxRetries, int backoffBaseSeconds, long maxPending) {
        public Outbox {
            if (maxRetries <= 0) {
                maxRetries = 8;
            }
            if (backoffBaseSeconds <= 0) {
                backoffBaseSeconds = 2;
            }
            if (maxPending <= 0) {
                maxPending = 10_000;
            }
        }
    }

    public int maxRetriesFor(ExecutorType type) {
        ExecutorOverride o = overrides.get(type);
        return (o != null && o.maxRetries() != null) ? o.maxRetries() : maxRetries;
    }

    public Duration stepTimeoutFor(ExecutorType type) {
        ExecutorOverride o = overrides.get(type);
        return (o != null && o.stepTimeout() != null) ? o.stepTimeout() : stepTimeout;
    }

    public Duration heartbeatTimeoutFor(ExecutorType type) {
        ExecutorOverride o = overrides.get(type);
        return (o != null && o.heartbeatTimeout() != null) ? o.heartbeatTimeout() : heartbeatTimeout;
    }
}
