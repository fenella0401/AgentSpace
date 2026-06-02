package com.agentspace.orchestration.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * workflow_event：事件留存（控制类全量 + 展示类按需）。见详细设计 §1.6。
 * 实时输出走内存/Redis，本表用于补偿和审计，不是实时主路径。
 */
@Entity
@Table(name = "workflow_event")
public class WorkflowEvent {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "step_id", length = 64)
    private String stepId;

    @Column(name = "attempt_id", length = 64)
    private String attemptId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 16)
    private String category;

    @Column(name = "sequence_no")
    private Long sequenceNo;

    @Column(length = 20)
    private String source;

    @Column
    private String payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected WorkflowEvent() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(String attemptId) {
        this.attemptId = attemptId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(Long sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
