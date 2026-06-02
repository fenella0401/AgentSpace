/**
 * 调度器：基于 DB polling 的定时触发入口（{@code FOR UPDATE SKIP LOCKED} 抢占 ready step、
 * watchdog 扫描超时/心跳丢失）。此层只做触发与扫描，调度决策逻辑落在 {@code service}。
 */
package com.agentspace.orchestration.scheduler;
