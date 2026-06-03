package com.agentspace.orchestration.repository;

import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.AttemptTrigger;
import com.agentspace.orchestration.model.ExecutorType;
import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证幂等唯一约束与基础 CRUD（H2 + schema.sql 初始化）。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RepositoryIntegrationTest {

    @Autowired
    WorkflowRunRepository runRepo;
    @Autowired
    WorkflowStepRepository stepRepo;
    @Autowired
    StepAttemptRepository attemptRepo;

    private WorkflowRun newRun(String id, String idemKey) {
        WorkflowRun run = new WorkflowRun();
        run.setId(id);
        run.setIdempotencyKey(idemKey);
        run.setStatus(RunStatus.PENDING);
        run.setFlowId("flow-1");
        run.setFlowSnapshotId("snap-1");
        run.setSchemaVersion("1");
        run.setTeamId("team-1");
        run.setUserId("user-1");
        run.setTaskId("task-1");
        run.setProjectId("proj-1");
        run.setAgentFlowJson("{}");
        OffsetDateTime now = OffsetDateTime.now();
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        return run;
    }

    private WorkflowStep newStep(String id, String runId, String key) {
        WorkflowStep step = new WorkflowStep();
        step.setId(id);
        step.setRunId(runId);
        step.setStepKey(key);
        step.setStatus(StepStatus.PENDING);
        step.setOrderIndex(0);
        step.setExecutorType(ExecutorType.CLAUDE_CODE);
        OffsetDateTime now = OffsetDateTime.now();
        step.setCreatedAt(now);
        step.setUpdatedAt(now);
        return step;
    }

    private StepAttempt newAttempt(String id, String runId, String stepId, int no) {
        StepAttempt a = new StepAttempt();
        a.setId(id);
        a.setRunId(runId);
        a.setStepId(stepId);
        a.setAttemptNo(no);
        a.setStatus(AttemptStatus.PENDING);
        a.setTrigger(AttemptTrigger.INITIAL);
        OffsetDateTime now = OffsetDateTime.now();
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        return a;
    }

    @Test
    void persistsAndFindsRunByIdempotencyKey() {
        runRepo.saveAndFlush(newRun("run-1", "idem-1"));
        assertThat(runRepo.findByIdempotencyKey("idem-1")).isPresent();
    }

    @Test
    void rejectsDuplicateIdempotencyKey() {
        runRepo.saveAndFlush(newRun("run-1", "dup"));
        assertThatThrownBy(() -> runRepo.saveAndFlush(newRun("run-2", "dup")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateAttemptNoForSameStep() {
        runRepo.saveAndFlush(newRun("run-1", "idem-1"));
        stepRepo.saveAndFlush(newStep("step-1", "run-1", "analyze"));
        attemptRepo.saveAndFlush(newAttempt("att-1", "run-1", "step-1", 1));
        assertThatThrownBy(() -> attemptRepo.saveAndFlush(newAttempt("att-2", "run-1", "step-1", 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameAttemptNoForDifferentSteps() {
        runRepo.saveAndFlush(newRun("run-1", "idem-1"));
        stepRepo.saveAndFlush(newStep("step-1", "run-1", "a"));
        stepRepo.saveAndFlush(newStep("step-2", "run-1", "b"));
        attemptRepo.saveAndFlush(newAttempt("att-1", "run-1", "step-1", 1));
        attemptRepo.saveAndFlush(newAttempt("att-2", "run-1", "step-2", 1));
        assertThat(attemptRepo.count()).isEqualTo(2);
    }

    @Test
    void countsStepsByStatus() {
        runRepo.saveAndFlush(newRun("run-1", "idem-1"));
        stepRepo.saveAndFlush(newStep("step-1", "run-1", "a"));
        WorkflowStep done = newStep("step-2", "run-1", "b");
        done.setStatus(StepStatus.COMPLETED);
        stepRepo.saveAndFlush(done);
        assertThat(stepRepo.countByRunIdAndStatus("run-1", StepStatus.COMPLETED)).isEqualTo(1);
        assertThat(stepRepo.countByRunIdAndStatus("run-1", StepStatus.PENDING)).isEqualTo(1);
    }
}
