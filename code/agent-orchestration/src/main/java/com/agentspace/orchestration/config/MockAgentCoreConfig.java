package com.agentspace.orchestration.config;

import com.agentspace.orchestration.client.mock.MockAgentCoreProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * mock profile 下的配置：启用 {@link MockAgentCoreProperties} 绑定，并提供异步推事件的执行器。
 */
@Configuration
@Profile("mock")
@EnableConfigurationProperties(MockAgentCoreProperties.class)
public class MockAgentCoreConfig {

    /** mock 异步推事件的单线程执行器：模拟 Agent Core 异步回调，避免在 startRun 事务内同步触发状态机。 */
    @Bean(name = "mockEmitExecutor", destroyMethod = "shutdown")
    public Executor mockEmitExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mock-agent-core-emit");
            t.setDaemon(true);
            return t;
        });
    }
}
