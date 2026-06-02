package com.agentspace.orchestration.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * step_dependency：run 内 step 的 DAG 边。见详细设计 §1.5。
 */
@Entity
@Table(name = "step_dependency")
public class StepDependency {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "from_step_key", nullable = false, length = 128)
    private String fromStepKey;

    @Column(name = "to_step_key", nullable = false, length = 128)
    private String toStepKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public StepDependency() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getFromStepKey() {
        return fromStepKey;
    }

    public void setFromStepKey(String fromStepKey) {
        this.fromStepKey = fromStepKey;
    }

    public String getToStepKey() {
        return toStepKey;
    }

    public void setToStepKey(String toStepKey) {
        this.toStepKey = toStepKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
