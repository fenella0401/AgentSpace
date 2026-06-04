package com.agentspace.orchestration.config;

import com.agentspace.orchestration.client.claudecode.ClaudeCodeAdapterProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 编排核心配置：启用运行时参数绑定、定时调度与异步执行。
 *
 * <p>{@link EnableAsync} 支撑 {@link com.agentspace.orchestration.service.run.RunScheduleKicker}
 * 的直驱快路径——其 {@code @TransactionalEventListener(AFTER_COMMIT)} 须在独立线程、新事务中执行，
 * 使 {@link com.agentspace.orchestration.service.run.StepLauncher} 对 Agent Core 的同步调用
 * 不占用发布事件的 DB 事务（见 README §2.5、§11#1）。
 */
@Configuration
@EnableConfigurationProperties({OrchestrationProperties.class, ClaudeCodeAdapterProperties.class})
@EnableScheduling
@EnableAsync
public class OrchestrationConfig {

    /**
     * 直驱 kicker 专用线程池：有界队列，避免 {@code SimpleAsyncTaskExecutor} 无限建线程。
     * 队列满时由提交线程（事件发布后的回调线程）兜底执行，最坏退化为同步，仍有轮询 sweeper 保底。
     */
    @Bean("scheduleKickerExecutor")
    public Executor scheduleKickerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("sched-kick-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

