# AGENTS.md - AI 协作行为指令索引

本文档是 AI Agent 在本项目空间内协作时使用的内容目录与导航索引。
详细规范、模板与技术标准统一存放在 `docs/` 目录下；本文仅提供索引与核心约束摘要。

## 标准操作剧本

执行特定的跨文档工程任务时，不要自行扩展流程，必须加载并严格遵循 `docs/.playbooks/` 目录下的对应剧本。

| 剧本类型 | 文件路径 | 触发场景 |
| --- | --- | --- |
| 产品定义 | `docs/.playbooks/write-product-definition.md` | 指定新需求，修改产品定义 |
| 功能设计 - PRD | `docs/.playbooks/write-function-prd.md` | 新增功能，或修改历史功能 |
| 功能设计 - UI | `docs/.playbooks/write-function-UI.md` | 功能设计 PRD 更新，且需要更新 UI |
| 功能设计 - 前端 | `docs/.playbooks/write-function-frontend.md` | 功能设计 PRD 更新，且需要更新前端 |
| 功能设计 - 后端 | `docs/.playbooks/write-function-backend.md` | 功能设计 PRD 更新，且需要更新后端 |
| 功能设计 - 验收 | `docs/.playbooks/write-function-validate.md` | 功能设计 PRD 更新，且需要更新功能验收标准 |
| 版本发布验证 | `docs/.playbooks/check-release.md` | 验证版本发布完备性 |
| 产品使用手册撰写 | `docs/.playbooks/write-product-use.md` | 版本验证通过，且需要更新产品使用手册 |

## docs/ 目录结构导航

```text
docs/
├── .playbooks/  # 标准操作剧本
│   ├── write-product-definition.md  # 产品定义
│   ├── write-function-prd.md  # 完成功能设计 PRD 部分
│   ├── write-function-UI.md  # 完成功能设计 UI 部分
│   ├── write-function-frontend.md  # 完成功能设计前端部分
│   ├── write-function-backend.md  # 完成功能设计后端部分
│   ├── write-function-validate.md  # 完成功能验收标准
│   ├── check-release.md  # 验证版本发布完备性
│   ├── write-product-use.md  # 版本验证通过后更新产品使用手册
|
├── guides/  # 全局规范与标准
│   ├── workflow.md  # 产品开发流程
│   ├── linter.md  # 版本发布验收标准
│   ├── concepts.md  # 核心概念
│   ├── templates/  # 模板
│   │   ├── product-definition.md  # 产品定义文档模板
│   │   ├── function-index.md  # 功能树与功能索引模板
│   │   ├── function-design.md  # 功能设计文档模板
│   │   ├── test-case.md  # 测试用例模板
│   │   ├── release-plan.md  # 发布计划模板
│   │   ├── test-report.md  # 迭代验收报告模板
│   │   ├── deployment-checklist.md  # 部署检查清单模板
│   │   ├── release-notes.md  # 发布公告模板
│   │   ├── product-use.md  # 产品使用手册模板
│
├── global/  # 全局文档
│   ├── product-definition.md  # 产品定义文档，面向产品经理视角
│   ├── function-index.md  # 功能树与功能索引
│   ├── architecture.md  # 系统架构
│   ├── product-use.md  # 产品使用手册，面向用户视角
│
├── function-design/  # 功能设计文档，开发视角的文档
│   ├── F-001-xxx.md  # F-001-xxx 功能设计
│   ├── F-001-yyy.md
│
├── test-case/  # 测试用例
│   ├── TC-001-xxx.md
│
├── release/  # 版本发布
│   ├── R-0630/  # 版本编号
│   │   ├── release-plan.md  # 发布计划
│   │   ├── test-report.md  # 验收报告
│   │   ├── deployment-checklist.md  # 检查清单
│   │   ├── release-notes.md  # 发布公告
│
└── archives/  # 归档，非指定情况下无需阅读
│   ├── historical-release/  # 历史版本发布记录归档
│   │   ├── R-0530/
│   │   ├── R-0430/
│   ├── deprecated-functions/  # 已下线历史功能设计
│   │   ├── F-000-zzz.md
```
