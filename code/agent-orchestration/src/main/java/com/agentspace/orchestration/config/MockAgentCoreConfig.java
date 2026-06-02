package com.agentspace.orchestration.config;

import com.agentspace.orchestration.client.mock.MockAgentCoreProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * mock profile 下的配置：启用 {@link MockAgentCoreProperties} 绑定。
 */
@Configuration
@Profile("mock")
@EnableConfigurationProperties(MockAgentCoreProperties.class)
public class MockAgentCoreConfig {
}
