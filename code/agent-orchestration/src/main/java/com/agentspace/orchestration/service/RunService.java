package com.agentspace.orchestration.service;

import com.agentspace.orchestration.client.AgentCoreClient;
import com.agentspace.orchestration.client.dto.StartAttemptRequest;
import com.agentspace.orchestration.client.dto.StartAttemptResponse;
import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.AttemptTrigger;
import com.agentspace.orchestration.model.RunStatus;
import com.agentspace.orchestration.model.StepStatus;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * run 生命周期编排（M2 范围）：启动 run → 落库快照 / 建 step+dependency → 算首批 ready
 * → 渲染 prompt → 调 Agent Core StartAttempt。见详细设计 §2.1、概要设计 §7、§9.2。
 *
 * <p>MVP 串行：多个 ready 时按 order_index 选一个启动。完整状态机与调度循环在 FE2。
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
    private final PromptRenderer promptRenderer;
    private final AgentCoreClient agentCoreClient;

    public RunService(WorkflowRunRepository runRepo,
                      WorkflowStepRepository stepRepo,
                      StepDependencyRepository depRepo,
                      StepAttemptRepository attemptRepo,
                      AgentFlowValidator validator,
                      AgentFlowCodec codec,
                      PromptRenderer promptRenderer,
                      AgentCoreClient agentCoreClient) {
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
        this.depRepo = depRepo;
        this.attemptRepo = attemptRepo;
        this.validator = validator;
        this.codec = codec;
        this.promptRenderer = promptRenderer;
        this.agentCoreClient = agentCoreClient;
    }

    /**
     * 启动一次 run。按 idempotencyKey 幂等：命中已存在 run 直接返回其当前状态。
     *
     * @return 已持久化的 WorkflowRun（含最新状态）
     */
    @Transactional
    public WorkflowRun startRun(AgentFlow flow) {
        String idempotencyKey = flow.run().idempotencyKey();

        // 幂等：命中已存在 run 直接返回
        Optional<WorkflowRun> existing = runRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            WorkflowRun run = existing.get();
            log.info("run 幂等命中 idempotencyKey={} runId={} status={}",
                    idempotencyKey, run.getId(), run.getStatus());
            return run;
        }

        // DAG 结构校验（422）
        validator.validate(flow);

        WorkflowRun run = persistRun(flow);
        Map<String, WorkflowStep> stepsByKey = persistSteps(run, flow);
        persistDependencies(run, flow);

        scheduleFirstReadyStep(run, flow, stepsByKey);
        return run;
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

    /**
     * 算首批 ready，串行选 order_index 最小的一个启动。run → RUNNING，step → RUNNING，建 attempt。
     */
    private void scheduleFirstReadyStep(WorkflowRun run, AgentFlow flow, Map<String, WorkflowStep> stepsByKey) {
        List<String> stepKeys = flow.steps().stream().map(AgentFlowStep::id).toList();
        Set<String> ready = DagSupport.initialReady(stepKeys, flow.edges());
        if (ready.isEmpty()) {
            log.warn("run {} 无初始 ready step（DAG 异常）", run.getId());
            return;
        }

        // 串行：选 order_index 最小者
        String chosenKey = ready.stream()
                .min((a, b) -> Integer.compare(
                        stepsByKey.get(a).getOrderIndex(), stepsByKey.get(b).getOrderIndex()))
                .orElseThrow();
        WorkflowStep step = stepsByKey.get(chosenKey);
        AgentFlowStep flowStep = flow.steps().stream()
                .filter(s -> s.id().equals(chosenKey)).findFirst().orElseThrow();

        String renderedPrompt = renderPrompt(flow, flowStep);
        step.setRenderedPrompt(renderedPrompt);
        step.setStatus(StepStatus.RUNNING);
        step.setStartedAt(OffsetDateTime.now());
        step.setUpdatedAt(OffsetDateTime.now());
        stepRepo.save(step);

        StepAttempt attempt = createAttempt(run, step, AttemptTrigger.INITIAL);
        callStartAttempt(run, step, flowStep, attempt, renderedPrompt);

        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(OffsetDateTime.now());
        run.setUpdatedAt(OffsetDateTime.now());
        runRepo.save(run);
    }

    private String renderPrompt(AgentFlow flow, AgentFlowStep flowStep) {
        Map<String, String> context = new HashMap<>();
        // 全局变量 + step 局部变量（上游 stepOutput 在 M2 首 step 阶段为空，FE2 补）
        addFlatVariables(context, "", flow.variables());
        addFlatVariables(context, "", flowStep.prompt().variables());
        return promptRenderer.render(flowStep.prompt().template(), context);
    }

    @SuppressWarnings("unchecked")
    private void addFlatVariables(Map<String, String> out, String prefix, Map<String, Object> vars) {
        if (vars == null) {
            return;
        }
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v instanceof Map<?, ?> nested) {
                addFlatVariables(out, key, (Map<String, Object>) nested);
            } else if (v != null) {
                out.put(key, String.valueOf(v));
            }
        }
    }

    private StepAttempt createAttempt(WorkflowRun run, WorkflowStep step, AttemptTrigger trigger) {
        int nextNo = attemptRepo.findByStepId(step.getId()).size() + 1;
        OffsetDateTime now = OffsetDateTime.now();
        StepAttempt attempt = new StepAttempt();
        attempt.setId("att-" + UUID.randomUUID());
        attempt.setRunId(run.getId());
        attempt.setStepId(step.getId());
        attempt.setAttemptNo(nextNo);
        attempt.setStatus(AttemptStatus.PENDING);
        attempt.setTrigger(trigger);
        attempt.setCreatedAt(now);
        attempt.setUpdatedAt(now);
        return attemptRepo.save(attempt);
    }

    private void callStartAttempt(WorkflowRun run, WorkflowStep step, AgentFlowStep flowStep,
                                  StepAttempt attempt, String renderedPrompt) {
        AgentFlow flow = codec.fromJson(run.getAgentFlowJson());
        StartAttemptRequest req = new StartAttemptRequest(
                run.getId(), step.getId(), step.getStepKey(), attempt.getId(), attempt.getAttemptNo(),
                flowStep.agent().executorType(), renderedPrompt,
                flowStep.agent().agentSnapshotRef(),
                flowStep.agent().skillSnapshotRefs(),
                flowStep.agent().mcpSnapshotRefs(),
                List.of(),
                flow.workspace(), flow.repo(), flow.credentials(),
                "evt-" + run.getId(), "log-" + run.getId(),
                attempt.getResumeFromSessionRef());

        attempt.setStatus(AttemptStatus.STARTING);
        attempt.setUpdatedAt(OffsetDateTime.now());
        attemptRepo.save(attempt);

        StartAttemptResponse resp = agentCoreClient.startAttempt(req);
        attempt.setRuntimeAttemptRef(resp.runtimeAttemptRef());
        attempt.setUpdatedAt(OffsetDateTime.now());
        attemptRepo.save(attempt);
        log.info("StartAttempt 调用完成 run={} step={} attempt={} runtimeRef={}",
                run.getId(), step.getStepKey(), attempt.getId(), resp.runtimeAttemptRef());
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
        return new ArrayList<>(attemptRepo.findByStepId(stepId));
    }
}
