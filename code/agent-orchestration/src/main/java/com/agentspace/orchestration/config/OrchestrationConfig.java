package com.agentspace.orchestration.config;

import com.agentspace.orchestration.client.claudecode.ClaudeCodeAdapterProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 编排核心配置：启用运行时参数绑定与定时调度。
 */
@Configuration
@EnableConfigurationProperties({OrchestrationProperties.class, ClaudeCodeAdapterProperties.class})
@EnableScheduling
public class OrchestrationConfig {
}
