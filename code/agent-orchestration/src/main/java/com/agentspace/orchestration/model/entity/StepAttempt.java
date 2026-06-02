package com.agentspace.orchestration.model.entity;

import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.AttemptTrigger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;

/**
 * step_attempt：step 的物理执行尝试（对应 Agent Core RuntimeAttempt）。见详细设计 §1.4。
 */
@Entity
@Table(name = "step_attempt")
public class StepAttempt {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "step_id", nullable = false, length = 64)
    private String stepId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttemptStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger", nullable = false, length = 20)
    private AttemptTrigger trigger;

    @Column(name = "runtime_attempt_ref", length = 256)
    private String runtimeAttemptRef;

    @Column(name = "resume_from_session_ref", length = 256)
    private String resumeFromSessionRef;

    @Column(name = "feedback")
    private String feedback;

    @Column(name = "failure_reason", length = 32)
    private String failureReason;

    @Column(name = "last_heartbeat_at")
    private OffsetDateTime lastHeartbeatAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    // FE3 乱序合并暂存：attempt.result 与 runtime.* 可乱序到达，先到者暂存，齐了再定终态。见 §8.4。
    @Column(name = "pending_result_status", length = 20)
    private String pendingResultStatus;     // SUCCEEDED / FAILED

    @Column(name = "pending_result_summary")
    private String pendingResultSummary;

    @Column(name = "pending_result_detail")
    private String pendingResultDetail;

    @Column(name = "pending_session_ref", length = 256)
    private String pendingSessionRef;

    @Column(name = "runtime_terminal", length = 20)
    private String runtimeTerminal;         // COMPLETED / FAILED / CANCELLED

    @Version
    @Column(nullable = false)
    private int version;

    public StepAttempt() {
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

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(int attemptNo) {
        this.attemptNo = attemptNo;
    }

    public AttemptStatus getStatus() {
        return status;
    }

    public void setStatus(AttemptStatus status) {
        this.status = status;
    }

    public AttemptTrigger getTrigger() {
        return trigger;
    }

    public void setTrigger(AttemptTrigger trigger) {
        this.trigger = trigger;
    }

    public String getRuntimeAttemptRef() {
        return runtimeAttemptRef;
    }

    public void setRuntimeAttemptRef(String runtimeAttemptRef) {
        this.runtimeAttemptRef = runtimeAttemptRef;
    }

    public String getResumeFromSessionRef() {
        return resumeFromSessionRef;
    }

    public void setResumeFromSessionRef(String resumeFromSessionRef) {
        this.resumeFromSessionRef = resumeFromSessionRef;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public OffsetDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(OffsetDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getPendingResultStatus() {
        return pendingResultStatus;
    }

    public void setPendingResultStatus(String pendingResultStatus) {
        this.pendingResultStatus = pendingResultStatus;
    }

    public String getPendingResultSummary() {
        return pendingResultSummary;
    }

    public void setPendingResultSummary(String pendingResultSummary) {
        this.pendingResultSummary = pendingResultSummary;
    }

    public String getPendingResultDetail() {
        return pendingResultDetail;
    }

    public void setPendingResultDetail(String pendingResultDetail) {
        this.pendingResultDetail = pendingResultDetail;
    }

    public String getPendingSessionRef() {
        return pendingSessionRef;
    }

    public void setPendingSessionRef(String pendingSessionRef) {
        this.pendingSessionRef = pendingSessionRef;
    }

    public String getRuntimeTerminal() {
        return runtimeTerminal;
    }

    public void setRuntimeTerminal(String runtimeTerminal) {
        this.runtimeTerminal = runtimeTerminal;
    }

    public int getVersion() {
        return version;
    }
}
