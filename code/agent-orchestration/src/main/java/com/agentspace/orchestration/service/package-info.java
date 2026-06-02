/**
 * 业务逻辑层：run / step / attempt 三级状态机、DAG 调度、事件处理、suspend-resume 续聊、
 * outbox 回流等核心编排逻辑均在此。状态写入走 CAS（乐观锁）+ 事务。
 */
package com.agentspace.orchestration.service;
