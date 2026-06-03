-- Agent-Orchestration 完整初始化 schema（openGauss / PostgreSQL 通用）。见详细设计 §1。
--
-- 用法：上线前手动在目标库执行本文件初始化表结构，例如：
--   gsql   -d agent_orchestration -f schema.sql      （openGauss）
--   psql   -d agent_orchestration -f schema.sql      （PostgreSQL）
-- 应用启动时 JPA 仅做 validate（ddl-auto: validate），不自动建表/迁移。
-- 所有 CREATE 均带 IF NOT EXISTS，重复执行安全（已存在则跳过，不报错）。
--
-- 约定：ID 统一 VARCHAR(64)；时间 TIMESTAMPTZ；枚举 VARCHAR + 应用层校验。
-- JSON 列 MVP 用 TEXT（应用层以 String + Jackson 存取），openGauss 与 H2 PG 兼容模式通用。
-- 后续需 jsonb 原生能力（GIN 索引等）时，可单独以 PG 专属脚本升级 agent_flow / payload 列。

CREATE TABLE IF NOT EXISTS workflow_run (
    id                  VARCHAR(64)  PRIMARY KEY,
    idempotency_key     VARCHAR(128) NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    flow_id             VARCHAR(64)  NOT NULL,
    flow_snapshot_id    VARCHAR(64)  NOT NULL,
    flow_name           VARCHAR(256),
    schema_version      VARCHAR(20)  NOT NULL,
    team_id             VARCHAR(64)  NOT NULL,
    user_id             VARCHAR(64)  NOT NULL,
    task_id             VARCHAR(64)  NOT NULL,
    project_id          VARCHAR(64)  NOT NULL,
    agent_flow          TEXT         NOT NULL,
    error_code          VARCHAR(64),
    error_message       TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    started_at          TIMESTAMP WITH TIME ZONE,
    finished_at         TIMESTAMP WITH TIME ZONE,
    version             INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_run_idempotency ON workflow_run (idempotency_key);
CREATE INDEX IF NOT EXISTS idx_run_status ON workflow_run (status);
CREATE INDEX IF NOT EXISTS idx_run_task   ON workflow_run (task_id);
CREATE INDEX IF NOT EXISTS idx_run_team   ON workflow_run (team_id);

CREATE TABLE IF NOT EXISTS workflow_step (
    id                    VARCHAR(64)  PRIMARY KEY,
    run_id                VARCHAR(64)  NOT NULL REFERENCES workflow_run(id),
    step_key              VARCHAR(128) NOT NULL,
    name                  VARCHAR(256),
    status                VARCHAR(20)  NOT NULL,
    order_index           INTEGER      NOT NULL,
    executor_type         VARCHAR(32)  NOT NULL,
    requires_confirmation BOOLEAN      NOT NULL DEFAULT FALSE,
    rendered_prompt       TEXT,
    session_ref           VARCHAR(256),
    output_summary        TEXT,
    output_result         TEXT,
    output_artifact_refs  TEXT,
    retry_count           INTEGER      NOT NULL DEFAULT 0,
    error_code            VARCHAR(64),
    error_message         TEXT,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    started_at            TIMESTAMP WITH TIME ZONE,
    finished_at           TIMESTAMP WITH TIME ZONE,
    version               INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_step_run_key ON workflow_step (run_id, step_key);
CREATE INDEX IF NOT EXISTS idx_step_run_status ON workflow_step (run_id, status);

CREATE TABLE IF NOT EXISTS step_attempt (
    id                      VARCHAR(64)  PRIMARY KEY,
    run_id                  VARCHAR(64)  NOT NULL REFERENCES workflow_run(id),
    step_id                 VARCHAR(64)  NOT NULL REFERENCES workflow_step(id),
    attempt_no              INTEGER      NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    trigger                 VARCHAR(20)  NOT NULL,
    runtime_attempt_ref     VARCHAR(256),
    resume_from_session_ref VARCHAR(256),
    feedback                TEXT,
    failure_reason          VARCHAR(32),
    last_heartbeat_at       TIMESTAMP WITH TIME ZONE,
    error_message           TEXT,
    -- attempt 终态乱序合并暂存：attempt.result（语义结果）与 runtime.*（物理终态）可乱序到达，
    -- 先到者暂存，两者齐再定终态。见详细设计 §8.4。
    pending_result_status   VARCHAR(20),   -- SUCCEEDED/FAILED（来自 attempt.result）
    pending_result_summary  TEXT,
    pending_result_detail   TEXT,
    pending_session_ref     VARCHAR(256),
    runtime_terminal        VARCHAR(20),   -- COMPLETED/FAILED/CANCELLED（来自 runtime.*）
    pending_artifact_refs   TEXT,          -- StepOutput.artifactRefs(JSON)，合并后写入 step
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    started_at              TIMESTAMP WITH TIME ZONE,
    finished_at             TIMESTAMP WITH TIME ZONE,
    version                 INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_attempt_step_no ON step_attempt (step_id, attempt_no);
CREATE INDEX IF NOT EXISTS idx_attempt_run_status ON step_attempt (run_id, status);
CREATE INDEX IF NOT EXISTS idx_attempt_heartbeat  ON step_attempt (status, last_heartbeat_at);

CREATE TABLE IF NOT EXISTS step_dependency (
    id            VARCHAR(64)  PRIMARY KEY,
    run_id        VARCHAR(64)  NOT NULL REFERENCES workflow_run(id),
    from_step_key VARCHAR(128) NOT NULL,
    to_step_key   VARCHAR(128) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dep ON step_dependency (run_id, from_step_key, to_step_key);
CREATE INDEX IF NOT EXISTS idx_dep_to   ON step_dependency (run_id, to_step_key);
CREATE INDEX IF NOT EXISTS idx_dep_from ON step_dependency (run_id, from_step_key);

CREATE TABLE IF NOT EXISTS workflow_event (
    id          VARCHAR(64) PRIMARY KEY,
    event_id    VARCHAR(64) NOT NULL,
    run_id      VARCHAR(64) NOT NULL,
    step_id     VARCHAR(64),
    attempt_id  VARCHAR(64),
    event_type  VARCHAR(64) NOT NULL,
    category    VARCHAR(16) NOT NULL,
    sequence_no BIGINT,
    source      VARCHAR(20),
    payload     TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_event_run_seq ON workflow_event (run_id, sequence_no);
CREATE INDEX IF NOT EXISTS idx_event_attempt ON workflow_event (attempt_id);

CREATE TABLE IF NOT EXISTS outbox_message (
    id            VARCHAR(64) PRIMARY KEY,
    run_id        VARCHAR(64) NOT NULL,
    payload       TEXT        NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count   INTEGER     NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    sent_at       TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_outbox_dispatch ON outbox_message (status, next_retry_at);

CREATE TABLE IF NOT EXISTS processed_event (
    event_id     VARCHAR(64) PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
