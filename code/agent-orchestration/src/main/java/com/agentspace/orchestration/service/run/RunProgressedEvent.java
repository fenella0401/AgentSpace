package com.agentspace.orchestration.service.run;

/**
 * run 取得进展（某 step 终态、串行单飞槽位释放、下游/兄弟 step 变 READY）的领域事件。
 *
 * <p>由推进 step 状态的事务发布，{@link RunScheduleKicker} 在事务 AFTER_COMMIT 后异步消费——
 * 立即重扫该 run 的 READY step 并启动，构成「直驱快路径」。这样正常路径无需等
 * {@code StepScheduler} 下一轮轮询（轮询降级为兜底 sweeper，见 README §2.5）。
 *
 * <p>为何只携带 runId、且在 AFTER_COMMIT 后才消费：{@link StepLauncher#launch} 会**同步**调用
 * Agent Core（HTTP），绝不能在发布事件的事务内执行，否则会把出站调用压在持有的 DB 事务里。
 * kicker 在新线程、新事务中按 runId 重新查库启动，天然避开此坑（亦见 README §11#1 afterCommit 时机）。
 *
 * @param runId 取得进展的 run
 */
public record RunProgressedEvent(String runId) {
}
