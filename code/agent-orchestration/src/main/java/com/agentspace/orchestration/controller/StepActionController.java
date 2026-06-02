package com.agentspace.orchestration.controller;

import com.agentspace.orchestration.controller.dto.ConfirmResponse;
import com.agentspace.orchestration.controller.dto.ContinueRequest;
import com.agentspace.orchestration.controller.dto.RetryRequest;
import com.agentspace.orchestration.controller.dto.RunResponse;
import com.agentspace.orchestration.controller.dto.StepAttemptResponse;
import com.agentspace.orchestration.model.entity.StepAttempt;
import com.agentspace.orchestration.model.entity.WorkflowRun;
import com.agentspace.orchestration.model.entity.WorkflowStep;
import com.agentspace.orchestration.service.RunCancellationService;
import com.agentspace.orchestration.service.RunService;
import com.agentspace.orchestration.service.SuspendResumeService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * suspended/failed step 操作与 run 取消（FE6）。鉴权 MVP 暂缓。见详细设计 §2.2–2.5。
 */
@RestController
@RequestMapping("/runs/{runId}")
public class StepActionController {

    private final SuspendResumeService suspendResume;
    private final RunCancellationService cancellation;
    private final RunService runService;

    public StepActionController(SuspendResumeService suspendResume,
                                RunCancellationService cancellation,
                                RunService runService) {
        this.suspendResume = suspendResume;
        this.cancellation = cancellation;
        this.runService = runService;
    }

    /** POST /runs/{runId}/cancel — 取消 run。见 §2.2。 */
    @PostMapping("/cancel")
    public RunResponse cancel(@PathVariable String runId) {
        WorkflowRun run = cancellation.cancel(runId);
        return new RunResponse(run.getId(), run.getStatus());
    }

    /** POST /runs/{runId}/steps/{stepId}/confirm — 确认通过。见 §2.3。 */
    @PostMapping("/steps/{stepId}/confirm")
    public ConfirmResponse confirm(@PathVariable String runId, @PathVariable String stepId) {
        WorkflowStep step = suspendResume.confirm(runId, stepId);
        WorkflowRun run = runService.findRun(runId).orElseThrow();
        return new ConfirmResponse(runId, step.getId(), step.getStatus(), run.getStatus());
    }

    /** POST /runs/{runId}/steps/{stepId}/continue — 续聊。见 §2.4。 */
    @PostMapping("/steps/{stepId}/continue")
    public StepAttemptResponse continueStep(@PathVariable String runId, @PathVariable String stepId,
                                            @RequestBody ContinueRequest request) {
        StepAttempt attempt = suspendResume.continueStep(runId, stepId,
                request.feedback(), request.actionKey());
        WorkflowStep step = runService.findSteps(runId).stream()
                .filter(s -> s.getId().equals(stepId)).findFirst().orElseThrow();
        return new StepAttemptResponse(runId, stepId, attempt.getAttemptNo(), step.getStatus());
    }

    /** POST /runs/{runId}/steps/{stepId}/retry — 手动重试。见 §2.5。 */
    @PostMapping("/steps/{stepId}/retry")
    public StepAttemptResponse retry(@PathVariable String runId, @PathVariable String stepId,
                                     @RequestBody(required = false) RetryRequest request) {
        boolean resumeSession = request != null && request.resumeSession();
        String actionKey = request == null ? null : request.actionKey();
        StepAttempt attempt = suspendResume.retry(runId, stepId, resumeSession, actionKey);
        WorkflowStep step = runService.findSteps(runId).stream()
                .filter(s -> s.getId().equals(stepId)).findFirst().orElseThrow();
        return new StepAttemptResponse(runId, stepId, attempt.getAttemptNo(), step.getStatus());
    }
}
