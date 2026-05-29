# AgentSpace 发布就绪评审提示词

请为当前分支准备一次发布就绪评审。

先阅读 `AGENTS.md`、`docs/harness/` 以及任何发布说明或变更文件。
在有帮助时使用 `product_manager`、`qa_engineer`、`security_reviewer`、`sre_engineer`、`ai_platform_engineer`。

返回以下内容：

1. go/no-go 建议。
2. 上线阻塞项（含负责人和缓解方案）。
3. 产品、UX、QA、安全、AI 平台、SRE 各维度就绪度。
4. 必需的测试、评测与人工检查。
5. 回滚方案与监控检查清单。
6. 上线后反馈与改进闭环。

除非被明确要求，否则不要修改文件。
