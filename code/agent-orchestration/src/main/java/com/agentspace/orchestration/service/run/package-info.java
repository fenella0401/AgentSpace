/**
 * run/step/attempt 生命周期编排：启动 run、调度 ready step、启动 attempt、终态推进与下游触发、
 * 自动/手动重试、suspend-resume 续聊、取消级联、重启恢复对账。状态写入走 CAS（乐观锁）+ 事务。
 */
package com.agentspace.orchestration.service.run;
