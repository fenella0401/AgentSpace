package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.AttemptTrigger;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.StepDependency;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.model.flow.AgentFlow;
import com.agentspace.orchestration.model.flow.AgentFlowEdge;
import com.agentspace.orchestration.model.flow.AgentFlowStep;
import com.agentspace.orchestration.repository.StepAttemptRepository;
import com.agentspace.orchestration.repository.StepDependencyRepository;
import com.agentspace.orchestration.repository.WorkflowRunRepository;
import com.agentspace.orchestration.repository.WorkflowStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * run 生命周期编排：启动 run → 落库快照 / 建 step+dependency → 算首批 ready → 交调度启动。
 * 见详细设计 §2.1、概要设计 §7、§9.2。实际 step 启动委托 {@link StepLauncher}，
 * 状态推进委托 {@link AttemptResultHandler}。
 */
@Service
public class RunService {

    private static final Logger log = LoggerFactory.getLogger(RunService.class);

    private final WorkflowRunRepository runRepo;
    private final WorkflowStepRepository stepRepo;
    private final StepDependencyRepository depRepo;
    private final StepAttemptRepository attemptRepo;
    private final AgentFlowValidator validator;
    private final AgentFlowCodec codec;
    private final StepLauncher stepLauncher;

    public RunService(WorkflowRunRepository runRepo,
                      WorkflowStepRepository stepRepo,
                      StepDependencyRepository depRepo,
                      StepAttemptRepository attemptRepo,
                      AgentFlowValidator validator,
                      AgentFlowCodec codec,
                      StepLauncher stepLauncher) {
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
        this.depRepo = depRepo;
        this.attemptRepo = attemptRepo;
        this.validator = validator;
        this.codec = codec;
        this.stepLauncher = stepLauncher;
    }

    /**
     * 启动一次 run。按 idempotencyKey 幂等：命中已存在 run 直接返回；同 key 但 AgentFlow 不一致 → 409；
     * 并发同 key 触发唯一索引冲突时重查返回已存在 run（至少一次创建语义）。见详细设计 §2.1、§9.2。
     */
    @Transactional
    public WorkflowRun startRun(AgentFlow flow) {
        String idempotencyKey = flow.run().idempotencyKey();
        String requestHash = codec.toJson(flow);

        Optional<WorkflowRun> existing = runRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return reuseOrConflict(existing.get(), idempotencyKey, requestHash);
        }

        validator.validate(flow);

        WorkflowRun run;
        try {
            run = persistRun(flow);
            runRepo.flush(); // 提前触发唯一约束，捕获并发冲突
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 并发同 key：另一请求已创建，重查返回已存在 run
            WorkflowRun other = runRepo.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> e);
            log.info("run 幂等并发冲突，返回已存在 runId={}", other.getId());
            return reuseOrConflict(other, idempotencyKey, requestHash);
        }

        Map<String, WorkflowStep> stepsByKey = persistSteps(run, flow);
        persistDependencies(run, flow);
        markInitialReady(run, flow, stepsByKey);
        launchFirstReady(run, flow);
        return run;
    }

    /** 幂等命中：AgentFlow 一致则复用，不一致则 409。 */
    private WorkflowRun reuseOrConflict(WorkflowRun existing, String idempotencyKey, String requestHash) {
        if (existing.getAgentFlowJson() != null && !existing.getAgentFlowJson().equals(requestHash)) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key 已存在但请求体不一致: " + idempotencyKey);
        }
        log.info("run 幂等命中 idempotencyKey={} runId={} status={}",
                idempotencyKey, existing.getId(), existing.getStatus());
        return existing;
    }

    private WorkflowRun persistRun(AgentFlow flow) {
        OffsetDateTime now = OffsetDateTime.now();
        WorkflowRun run = new WorkflowRun();
        run.setId(flow.run().runId() != null ? flow.run().runId() : "run-" + UUID.randomUUID());
        run.setIdempotencyKey(flow.run().idempotencyKey());
        run.setStatus(RunStatus.PENDING);
        run.setFlowId(flow.flowId());
        run.setFlowSnapshotId(flow.flowSnapshotId());
        run.setFlowName(flow.flowName());
        run.setSchemaVersion(flow.schemaVersion());
        run.setTeamId(flow.tenant().teamId());
        run.setUserId(flow.tenant().userId());
        run.setTaskId(flow.task() != null ? flow.task().taskId() : "");
        run.setProjectId(flow.task() != null ? flow.task().projectId() : "");
        run.setAgentFlowJson(codec.toJson(flow));
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        return runRepo.save(run);
    }

    private Map<String, WorkflowStep> persistSteps(WorkflowRun run, AgentFlow flow) {
        Map<String, WorkflowStep> byKey = new HashMap<>();
        OffsetDateTime now = OffsetDateTime.now();
        int index = 0;
        for (AgentFlowStep s : flow.steps()) {
            WorkflowStep step = new WorkflowStep();
            step.setId("step-" + UUID.randomUUID());
            step.setRunId(run.getId());
            step.setStepKey(s.id());
            step.setName(s.name());
            step.setStatus(StepStatus.PENDING);
            step.setOrderIndex(index++);
            step.setExecutorType(s.agent().executorType());
            step.setRequiresConfirmation(s.requiresConfirmation());
            step.setCreatedAt(now);
            step.setUpdatedAt(now);
            byKey.put(s.id(), stepRepo.save(step));
        }
        return byKey;
    }

    private void persistDependencies(WorkflowRun run, AgentFlow flow) {
        if (flow.edges() == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (AgentFlowEdge edge : flow.edges()) {
            StepDependency dep = new StepDependency();
            dep.setId("dep-" + UUID.randomUUID());
            dep.setRunId(run.getId());
            dep.setFromStepKey(edge.from());
            dep.setToStepKey(edge.to());
            dep.setCreatedAt(now);
            depRepo.save(dep);
        }
    }

    /** 入度为 0 的 step 置 READY。 */
    private void markInitialReady(WorkflowRun run, AgentFlow flow, Map<String, WorkflowStep> stepsByKey) {
        List<String> stepKeys = flow.steps().stream().map(AgentFlowStep::id).toList();
        Set<String> ready = DagSupport.initialReady(stepKeys, flow.edges());
        for (String key : ready) {
            WorkflowStep step = stepsByKey.get(key);
            step.setStatus(StepStatus.READY);
            step.setUpdatedAt(OffsetDateTime.now());
            stepRepo.save(step);
        }
    }

    /** MVP 串行：在初始 ready 中选 order_index 最小者启动，run → RUNNING。 */
    private void launchFirstReady(WorkflowRun run, AgentFlow flow) {
        Optional<WorkflowStep> first = stepRepo.findByRunIdAndStatus(run.getId(), StepStatus.READY).stream()
                .min((a, b) -> Integer.compare(a.getOrderIndex(), b.getOrderIndex()));
        if (first.isEmpty()) {
            log.warn("run {} 无初始 ready step（DAG 异常）", run.getId());
            return;
        }
        StepAttempt attempt = stepLauncher.launch(run, first.get(), flow, AttemptTrigger.INITIAL, null, null);
        // 渲染失败等导致首 step 直接 FAILED：run 已被 recalc 推进，勿覆盖回 RUNNING
        if (attempt.getStatus() == AttemptStatus.FAILED) {
            return;
        }
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(OffsetDateTime.now());
        run.setUpdatedAt(OffsetDateTime.now());
        runRepo.save(run);
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowRun> findRun(String runId) {
        return runRepo.findById(runId);
    }

    @Transactional(readOnly = true)
    public List<WorkflowStep> findSteps(String runId) {
        return stepRepo.findByRunId(runId);
    }

    @Transactional(readOnly = true)
    public List<StepAttempt> findAttempts(String stepId) {
        return attemptRepo.findByStepId(stepId);
    }
}
