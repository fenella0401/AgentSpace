package com.agentspace.orchestration.event;

import com.agentspace.orchestration.model.event.AgentExecutionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link EventSink} 的默认实现：记录日志。
 *
 * <p>脚手架 / 联调早期占位，FE3 落地 {@code POST /internal/agent-core/events} 后，
 * 由处理状态机推进的真实实现接管（本实现可作为兜底或被 {@code @Primary} 覆盖）。
 */
@Component
public class LoggingEventSink implements EventSink {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventSink.class);

    @Override
    public void accept(AgentExecutionEvent event) {
        log.info("event received type={} run={} step={} attempt={} seq={}",
                event.eventType(), event.runId(), event.stepId(), event.attemptId(), event.sequence());
    }
}
