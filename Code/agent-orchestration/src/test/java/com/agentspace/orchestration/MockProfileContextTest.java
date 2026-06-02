package com.agentspace.orchestration;

import com.agentspace.orchestration.client.AgentCoreClient;
import com.agentspace.orchestration.client.mock.MockAgentCoreClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 mock profile 下应用上下文正常加载，且 {@link AgentCoreClient} 被装配为 mock 实现。
 */
@SpringBootTest
@ActiveProfiles("mock")
class MockProfileContextTest {

    @Autowired
    AgentCoreClient agentCoreClient;

    @Test
    void mockClientIsWired() {
        assertThat(agentCoreClient).isInstanceOf(MockAgentCoreClient.class);
    }
}
