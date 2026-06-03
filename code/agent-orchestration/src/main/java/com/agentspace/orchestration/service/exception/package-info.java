/**
 * 业务异常。由 {@code controller.ApiExceptionHandler} 统一映射为 HTTP 错误码
 * （如校验失败→422、幂等冲突→409、step 动作冲突→409、归属不符→422）。
 */
package com.agentspace.orchestration.service.exception;
