package com.agentspace.orchestration.model.entity;

import com.agentspace.orchestration.model.ExecutorType;
import com.agentspace.orchestration.model.StepStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;

/**
 * workflow_step：run 内逻辑步骤。见详细设计 §1.3。
 */
@Entity
@Table(name = "workflow_step")
public class WorkflowStep {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "step_key", nullable = false, length = 128)
    private String stepKey;

    @Column(length = 256)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StepStatus status;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "executor_type", nullable = false, length = 32)
    private ExecutorType executorType;

    @Column(name = "requires_confirmation", nullable = false)
    private boolean requiresConfirmation;

    @Column(name = "rendered_prompt")
    private String renderedPrompt;

    @Column(name = "session_ref", length = 256)
    private String sessionRef;

    @Column(name = "output_summary")
    private String outputSummary;

    @Column(name = "output_result")
    private String outputResult;

    @Column(name = "output_artifact_refs")
    private String outputArtifactRefs;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_code", length = 64)
    private String errorCode;

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

    @Version
    @Column(nullable = false)
    private int version;

    public WorkflowStep() {
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

    public String getStepKey() {
        return stepKey;
    }

    public void setStepKey(String stepKey) {
        this.stepKey = stepKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public StepStatus getStatus() {
        return status;
    }

    public void setStatus(StepStatus status) {
        this.status = status;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public ExecutorType getExecutorType() {
        return executorType;
    }

    public void setExecutorType(ExecutorType executorType) {
        this.executorType = executorType;
    }

    public boolean isRequiresConfirmation() {
        return requiresConfirmation;
    }

    public void setRequiresConfirmation(boolean requiresConfirmation) {
        this.requiresConfirmation = requiresConfirmation;
    }

    public String getRenderedPrompt() {
        return renderedPrompt;
    }

    public void setRenderedPrompt(String renderedPrompt) {
        this.renderedPrompt = renderedPrompt;
    }

    public String getSessionRef() {
        return sessionRef;
    }

    public void setSessionRef(String sessionRef) {
        this.sessionRef = sessionRef;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public void setOutputSummary(String outputSummary) {
        this.outputSummary = outputSummary;
    }

    public String getOutputResult() {
        return outputResult;
    }

    public void setOutputResult(String outputResult) {
        this.outputResult = outputResult;
    }

    public String getOutputArtifactRefs() {
        return outputArtifactRefs;
    }

    public void setOutputArtifactRefs(String outputArtifactRefs) {
        this.outputArtifactRefs = outputArtifactRefs;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
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

    public int getVersion() {
        return version;
    }
}
