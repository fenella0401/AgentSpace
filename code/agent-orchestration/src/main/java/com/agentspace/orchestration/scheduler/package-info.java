/**
 * 调度器：基于 DB polling 的定时触发入口（轮询 READY step 经 CAS(@Version) 抢占启动、
 * watchdog 扫描超时/心跳丢失）。此层只做触发与扫描，调度决策逻辑落在 {@code service}。
 *
 * <p>多副本并发不重复靠乐观锁 CAS；{@code FOR UPDATE SKIP LOCKED} 为上线前性能优化，尚未落地。
 */
package com.agentspace.orchestration.scheduler;
