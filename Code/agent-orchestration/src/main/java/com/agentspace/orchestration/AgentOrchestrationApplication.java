package com.agentspace.orchestration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Agent-Orchestration 服务启动入口。
 *
 * <p>本服务只执行 Agent-Management 提交的 AgentFlow 快照，围绕 run / step / attempt
 * 三级状态机做可恢复、幂等、DB 驱动的轻量调度，不感知底层运行时。
 */
@SpringBootApplication
public class AgentOrchestrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentOrchestrationApplication.class, args);
    }
}
