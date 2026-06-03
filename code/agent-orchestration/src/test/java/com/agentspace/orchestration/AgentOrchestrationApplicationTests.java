package com.agentspace.orchestration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 验证 Spring 应用上下文能正常加载（test profile：H2 + schema.sql）。
 */
@SpringBootTest
@ActiveProfiles("test")
class AgentOrchestrationApplicationTests {

    @Test
    void contextLoads() {
    }
}
