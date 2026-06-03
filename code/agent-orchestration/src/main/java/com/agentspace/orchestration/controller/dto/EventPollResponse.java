package com.agentspace.orchestration.controller.dto;

import com.agentspace.orchestration.model.RunStatus;

import java.util.List;

/**
 * 前端轮询展示类事件的响应体（轮询替代 SSE，见详细设计 §2.7）。
 *
 * <p>前端按 {@code nextSequence} 作为下次轮询的 {@code fromSequence}；
 * {@code hasMore=true} 表示本批因 limit 截断、应立即再拉一次（不必等轮询间隔）；
 * {@code runTerminal=true} 表示 run 已到终态（COMPLETED/FAILED/CANCELLED），前端可停止轮询。
 *
 * @param events      本批展示类事件（按 sequenceNo 升序）
 * @param nextSequence 下次轮询的游标（= 本批最大 sequenceNo；本批为空则回显请求的 fromSequence）
 * @param hasMore     是否因 limit 截断仍有更多事件
 * @param runStatus   当前 run 状态
 * @param runTerminal run 是否已终态（前端据此停止轮询）
 */
public record EventPollResponse(
        List<DisplayEventMessage> events,
        long nextSequence,
        boolean hasMore,
        RunStatus runStatus,
        boolean runTerminal
) {
}
