package com.agentspace.orchestration.controller;

import com.agentspace.orchestration.controller.dto.CreateRunRequest;
import com.agentspace.orchestration.controller.dto.RunDetailResponse;
import com.agentspace.orchestration.controller.dto.RunResponse;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.service.RunService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Comparator;
import java.util.List;

/**
 * run 生命周期对外接口。鉴权 MVP 暂缓（无鉴权直连）。见详细设计 §2.1、§2.6。
 */
@RestController
@RequestMapping("/runs")
public class RunController {

    private final RunService runService;

    public RunController(RunService runService) {
        this.runService = runService;
    }

    /**
     * POST /runs — 启动 run。按 Idempotency-Key 幂等。见详细设计 §2.1。
     */
    @PostMapping
    public RunResponse createRun(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                 @Valid @RequestBody CreateRunRequest request) {
        WorkflowRun run = runService.startRun(request.agentFlow());
        return new RunResponse(run.getId(), run.getStatus());
    }

    /**
     * GET /runs/{runId} — 查询 run / step 状态快照。见详细设计 §2.6。
     */
    @GetMapping("/{runId}")
    public ResponseEntity<RunDetailResponse> getRun(@PathVariable String runId) {
        WorkflowRun run = runService.findRun(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "run 不存在: " + runId));

        List<WorkflowStep> steps = runService.findSteps(runId);
        List<RunDetailResponse.StepView> stepViews = steps.stream()
                .sorted(Comparator.comparingInt(WorkflowStep::getOrderIndex))
                .map(this::toStepView)
                .toList();

        RunDetailResponse.RunView runView = new RunDetailResponse.RunView(
                run.getId(), run.getStatus(), run.getStartedAt(), run.getErrorCode());
        return ResponseEntity.ok(new RunDetailResponse(runView, stepViews));
    }

    private RunDetailResponse.StepView toStepView(WorkflowStep step) {
        List<StepAttempt> attempts = runService.findAttempts(step.getId());
        Integer currentAttemptNo = attempts.stream()
                .map(StepAttempt::getAttemptNo)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new RunDetailResponse.StepView(
                step.getId(), step.getStepKey(), step.getStatus(),
                step.getOutputSummary(), currentAttemptNo, null);
    }
}
