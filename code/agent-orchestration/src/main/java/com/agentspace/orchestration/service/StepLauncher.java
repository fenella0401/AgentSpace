package com.agentspace.orchestration.service;

import com.agentspace.orchestration.client.AgentCoreClient;
import com.agentspace.orchestration.client.dto.StartAttemptRequest;
import com.agentspace.orchestration.client.dto.StartAttemptResponse;
import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.model.AttemptTrigger;
import com.agentspace.orchestration.model.StepStatus;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.model.flow.AgentFlow;
import com.agentspace.orchestration.model.flow.AgentFlowStep;
import com.agentspace.orchestration.repository.StepAttemptRepository;
import com.agentspace.orchestration.repository.WorkflowStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 启动一个 step 的执行尝试：渲染 prompt → 建 attempt → 调 Agent Core StartAttempt → step RUNNING。
 * 供调度器（首次/READY）、自动重试、续聊（FE6）复用。见详细设计 §3.1–3.2。
 */
@Component
public class StepLauncher {

    private static final Logger log = LoggerFactory.getLogger(StepLauncher.class);

    private final WorkflowStepRepository stepRepo;
    private final StepAttemptRepository attemptRepo;
    private final AgentFlowCodec codec;
    private final PromptRenderer promptRenderer;
    private final AgentCoreClient agentCoreClient;

    public StepLauncher(WorkflowStepRepository stepRepo,
                        StepAttemptRepository attemptRepo,
                        AgentFlowCodec codec,
                        PromptRenderer promptRenderer,
                        AgentCoreClient agentCoreClient) {
        this.stepRepo = stepRepo;
        this.attemptRepo = attemptRepo;
        this.codec = codec;
        this.promptRenderer = promptRenderer;
        this.agentCoreClient = agentCoreClient;
    }

    /**
     * 为 step 启动一次 attempt。step 须处于 READY（首次/重试）或 SUSPENDED（续聊，FE6）。
     *
     * @param trigger             本次 attempt 的触发来源
     * @param resumeSessionRef    续聊时的对话上下文（非续聊为 null）
     * @param feedback            续聊反馈（非续聊为 null）
     */
    public StepAttempt launch(WorkflowRun run, WorkflowStep step, AgentFlow flow,
                              AttemptTrigger trigger, String resumeSessionRef, String feedback) {
        AgentFlowStep flowStep = flow.steps().stream()
                .filter(s -> s.id().equals(step.getStepKey())).findFirst()
                .orElseThrow(() -> new IllegalStateException("AgentFlow 缺少 step: " + step.getStepKey()));

        String renderedPrompt = renderPrompt(flow, flowStep, run);
        step.setRenderedPrompt(renderedPrompt);
        step.setStatus(StepStatus.RUNNING);
        step.setStartedAt(OffsetDateTime.now());
        step.setUpdatedAt(OffsetDateTime.now());
        stepRepo.save(step);

        StepAttempt attempt = createAttempt(run, step, trigger, resumeSessionRef, feedback);
        callStartAttempt(run, step, flowStep, attempt, renderedPrompt, flow);
        return attempt;
    }

    private String renderPrompt(AgentFlow flow, AgentFlowStep flowStep, WorkflowRun run) {
        Map<String, String> context = new HashMap<>();
        addFlat(context, "", flow.variables());
        addFlat(context, "", flowStep.prompt().variables());
        addUpstreamOutputs(context, run);
        return promptRenderer.render(flowStep.prompt().template(), context);
    }

    /** 把已完成上游 step 的输出展开为 steps.&lt;key&gt;.summary / .result。见概要设计 §7.1。 */
    private void addUpstreamOutputs(Map<String, String> context, WorkflowRun run) {
        for (WorkflowStep s : stepRepo.findByRunIdAndStatus(run.getId(), StepStatus.COMPLETED)) {
            if (s.getOutputSummary() != null) {
                context.put("steps." + s.getStepKey() + ".summary", s.getOutputSummary());
            }
            if (s.getOutputResult() != null) {
                context.put("steps." + s.getStepKey() + ".result", s.getOutputResult());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void addFlat(Map<String, String> out, String prefix, Map<String, Object> vars) {
        if (vars == null) {
            return;
        }
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v instanceof Map<?, ?> nested) {
                addFlat(out, key, (Map<String, Object>) nested);
            } else if (v != null) {
                out.put(key, String.valueOf(v));
            }
        }
    }

    private StepAttempt createAttempt(WorkflowRun run, WorkflowStep step, AttemptTrigger trigger,
                                      String resumeSessionRef, String feedback) {
        int nextNo = attemptRepo.findByStepId(step.getId()).size() + 1;
        OffsetDateTime now = OffsetDateTime.now();
        StepAttempt attempt = new StepAttempt();
        attempt.setId("att-" + UUID.randomUUID());
        attempt.setRunId(run.getId());
        attempt.setStepId(step.getId());
        attempt.setAttemptNo(nextNo);
        attempt.setStatus(AttemptStatus.PENDING);
        attempt.setTrigger(trigger);
        attempt.setResumeFromSessionRef(resumeSessionRef);
        attempt.setFeedback(feedback);
        attempt.setCreatedAt(now);
        attempt.setUpdatedAt(now);
        return attemptRepo.save(attempt);
    }

    private void callStartAttempt(WorkflowRun run, WorkflowStep step, AgentFlowStep flowStep,
                                  StepAttempt attempt, String renderedPrompt, AgentFlow flow) {
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
        log.info("StartAttempt run={} step={} attempt={} no={} trigger={} runtimeRef={}",
                run.getId(), step.getStepKey(), attempt.getId(), attempt.getAttemptNo(),
                attempt.getTrigger(), resp.runtimeAttemptRef());
    }
}
