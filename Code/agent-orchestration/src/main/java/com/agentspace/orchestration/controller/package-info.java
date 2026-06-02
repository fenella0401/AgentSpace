/**
 * HTTP 端点层：对外只读接口（{@code /runs/**}）与内部回调接口（{@code /internal/**}）同层放置，
 * 按路径区分，不在包层级拆分 web / internal。控制器保持薄壳，业务逻辑下沉到 {@code service}。
 */
package com.agentspace.orchestration.controller;
