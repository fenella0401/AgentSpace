package com.agentspace.orchestration.service;

import com.agentspace.orchestration.model.AttemptStatus;
import com.agentspace.orchestration.repository.StepAttemptRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 编排可观测性指标（FE7）：running attempts、失败原因计数。见详细设计 §13.1。
 *
 * <ul>
 *   <li>{@code orchestration.attempts.running}（gauge）：当前 RUNNING attempt 数；</li>
 *   <li>{@code orchestration.attempt.failures}（counter，tag reason）：attempt 失败计数。</li>
 * </ul>
 */
@Component
public class OrchestrationMetrics {

    private final MeterRegistry registry;
    private final StepAttemptRepository attemptRepo;

    private final AtomicLong runningAttempts = new AtomicLong(0);

    public OrchestrationMetrics(MeterRegistry registry,
                                StepAttemptRepository attemptRepo) {
        this.registry = registry;
        this.attemptRepo = attemptRepo;
        registry.gauge("orchestration.attempts.running", runningAttempts);
    }

    /** 由调度/事件路径周期刷新 gauge（简单起见也可定时拉）。 */
    public void refreshGauges() {
        runningAttempts.set(attemptRepo.findAllInFlight().stream()
                .filter(a -> a.getStatus() == AttemptStatus.RUNNING).count());
    }

    /** attempt 失败计数（按 reason 打标）。 */
    public void recordFailure(String reason) {
        Counter.builder("orchestration.attempt.failures")
                .tags(Tags.of("reason", reason == null ? "UNKNOWN" : reason))
                .register(registry)
                .increment();
    }
}
