# agent-orchestration

AgentSpace 的 **Agent-Orchestration** 微服务：执行 Agent-Management 提交的 AgentFlow 快照，
围绕 run / step / attempt 三级状态机做可恢复、幂等、DB 驱动的轻量工作流编排。

设计文档：
- 概要设计 `../../docs/product/agent-orchestration/spec/2026-05-30-orchestrator-agentflow-workflow-template-design.md`
- 详细设计 `../../docs/product/agent-orchestration/spec/2026-06-01-agent-orchestration-implementation-spec.md`
- 里程碑 / task / 特性分解 `../../docs/product/agent-orchestration/plan/`

## 技术栈

Java 21 + Spring Boot 3.2.5 + Maven。状态存储 openGauss（PostgreSQL 兼容，后续接入）。

## 包结构

| 包 | 职责 |
|---|---|
| `controller` | HTTP 端点（对外只读 `/runs/**` 与内部回调 `/internal/**` 同层，按路径区分） |
| `service` | 业务逻辑：状态机、调度、事件处理、续聊、outbox 回流 |
| `model` | 数据结构：实体、DTO、AgentFlow 快照、状态枚举 |
| `scheduler` | DB polling 调度触发与 watchdog 扫描 |
| `client` | Agent Core 调用与 Agent-Management 回流出站 |
| `repository` | 持久化（openGauss 事实状态源） |
| `config` | 数据源、调度、HTTP 客户端、可观测性等配置 |

## 构建与运行

本机 Maven 默认 JDK 可能非 21，构建请显式指定 Java 21：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

mvn compile        # 编译
mvn test           # 跑测试（含 context load）
mvn spring-boot:run # 本地启动，默认端口 8080
```

健康检查：

```bash
curl http://localhost:8080/actuator/health   # -> {"status":"UP"}
```

## 现状

脚手架阶段（T0.1）：可编译、可启动、健康检查可用。尚未接入数据源与数据库迁移，
业务逻辑（FE1 起）逐步填充各分层包。
