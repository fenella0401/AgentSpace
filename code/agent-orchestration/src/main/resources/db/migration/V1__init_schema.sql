-- Agent-Orchestration 初始 schema。见详细设计 §1。
-- 目标库 openGauss（PostgreSQL 兼容）；测试用 H2 PostgreSQL 兼容模式。
-- ID 统一 varchar(64)；时间 timestamptz；枚举 varchar + 应用层校验。
-- JSON 列：MVP 用 TEXT（应用层以 String + Jackson 存取），保证迁移脚本在 openGauss 与
-- H2 PostgreSQL 兼容模式下通用。后续需 jsonb 原生能力（GIN 索引等）时再以 PG 专属迁移升级。

CREATE TABLE workflow_run (
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

CREATE UNIQUE INDEX uk_run_idempotency ON workflow_run (idempotency_key);
CREATE INDEX idx_run_status ON workflow_run (status);
CREATE INDEX idx_run_task   ON workflow_run (task_id);
CREATE INDEX idx_run_team   ON workflow_run (team_id);

CREATE TABLE workflow_step (
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

CREATE UNIQUE INDEX uk_step_run_key ON workflow_step (run_id, step_key);
CREATE INDEX idx_step_run_status ON workflow_step (run_id, status);

CREATE TABLE step_attempt (
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
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    started_at              TIMESTAMP WITH TIME ZONE,
    finished_at             TIMESTAMP WITH TIME ZONE,
    version                 INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_attempt_step_no ON step_attempt (step_id, attempt_no);
CREATE INDEX idx_attempt_run_status ON step_attempt (run_id, status);
CREATE INDEX idx_attempt_heartbeat  ON step_attempt (status, last_heartbeat_at);

CREATE TABLE step_dependency (
    id            VARCHAR(64)  PRIMARY KEY,
    run_id        VARCHAR(64)  NOT NULL REFERENCES workflow_run(id),
    from_step_key VARCHAR(128) NOT NULL,
    to_step_key   VARCHAR(128) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_dep ON step_dependency (run_id, from_step_key, to_step_key);
CREATE INDEX idx_dep_to   ON step_dependency (run_id, to_step_key);
CREATE INDEX idx_dep_from ON step_dependency (run_id, from_step_key);

CREATE TABLE workflow_event (
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

CREATE INDEX idx_event_run_seq ON workflow_event (run_id, sequence_no);
CREATE INDEX idx_event_attempt ON workflow_event (attempt_id);

CREATE TABLE outbox_message (
    id            VARCHAR(64) PRIMARY KEY,
    run_id        VARCHAR(64) NOT NULL,
    payload       TEXT        NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count   INTEGER     NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    sent_at       TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_dispatch ON outbox_message (status, next_retry_at);

CREATE TABLE processed_event (
    event_id     VARCHAR(64) PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
