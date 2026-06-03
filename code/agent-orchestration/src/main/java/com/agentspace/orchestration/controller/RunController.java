package com.agentspace.orchestration.controller;

import com.agentspace.orchestration.controller.dto.CreateRunRequest;
import com.agentspace.orchestration.controller.dto.EventPollResponse;
import com.agentspace.orchestration.controller.dto.RunDetailResponse;
import com.agentspace.orchestration.controller.dto.RunResponse;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.service.run.RunService;
import com.agentspace.orchestration.service.event.RunEventBroadcaster;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.HttpStatus;

import java.util.Comparator;
import java.util.List;

/**
 * run 生命周期对外接口。鉴权 MVP 暂缓（无鉴权直连）。见详细设计 §2.1、§2.6、§2.7。
 */
@RestController
@RequestMapping("/runs")
public class RunController {

    private final RunService runService;
    private final RunEventBroadcaster broadcaster;

    public RunController(RunService runService, RunEventBroadcaster broadcaster) {
        this.runService = runService;
        this.broadcaster = broadcaster;
    }

    /**
     * POST /runs — 启动 run。按 Idempotency-Key 幂等。见详细设计 §2.1。
     *
     * <p>{@code Idempotency-Key} header 可选；若提供，须与 body 的 {@code agentFlow.run.idempotencyKey}
     * 一致（否则 422），以 body 内键为执行幂等键。
     */
    @PostMapping
    public RunResponse createRun(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                 @Valid @RequestBody CreateRunRequest request) {
        String bodyKey = request.agentFlow().run() == null ? null : request.agentFlow().run().idempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank() && !idempotencyKey.equals(bodyKey)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Idempotency-Key header 与 body.run.idempotencyKey 不一致");
        }
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

    /**
     * GET /runs/{runId}/events — 展示类事件实时流（SSE）。鉴权 MVP 暂缓。见详细设计 §2.7。
     *
     * @param fromSequence 断线续传起点（可选）
     */
    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable String runId,
                                   @RequestParam(value = "fromSequence", required = false) Long fromSequence) {
        if (runService.findRun(runId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "run 不存在: " + runId);
        }
        return broadcaster.subscribe(runId, fromSequence);
    }

    /**
     * GET /runs/{runId}/events/poll — 展示类事件增量轮询（SSE 之外的轮询方案，建议间隔 5~10s）。
     * 见详细设计 §2.7。无长连接、无常驻线程，天然兼容多副本。
     *
     * <p>前端按返回的 {@code nextSequence} 作为下次的 {@code fromSequence}；{@code hasMore=true}
     * 表示本批被 limit 截断、应立即再拉一次；{@code runTerminal=true} 表示可停止轮询。
     *
     * @param fromSequence 上次轮询到的最大 sequenceNo（可选，缺省从头拉）
     * @param limit        本批最大条数（可选，缺省 200，上限 1000）
     */
    @GetMapping("/{runId}/events/poll")
    public EventPollResponse pollEvents(@PathVariable String runId,
                                        @RequestParam(value = "fromSequence", required = false) Long fromSequence,
                                        @RequestParam(value = "limit", required = false) Integer limit) {
        WorkflowRun run = runService.findRun(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "run 不存在: " + runId));
        return runService.pollDisplayEvents(run, fromSequence, limit);
    }

    private RunDetailResponse.StepView toStepView(WorkflowStep step) {
        List<StepAttempt> attempts = runService.findAttempts(step.getId());
        StepAttempt latest = attempts.stream()
                .max(Comparator.comparingInt(StepAttempt::getAttemptNo))
                .orElse(null);
        Integer currentAttemptNo = latest == null ? null : latest.getAttemptNo();
        return new RunDetailResponse.StepView(
                step.getId(), step.getStepKey(), step.getStatus(),
                step.getOutputSummary(), currentAttemptNo,
                latest == null ? null : latest.getLastHeartbeatAt());
    }
}
