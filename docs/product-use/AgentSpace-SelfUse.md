# AgentSpace 自用开发任务配置

本文描述 630 版本上线后，AgentSpace 团队用自己的 AgentSpace 配置产品开发、自验、发布和产品发现活动。

## 配置原则

- 代码变更审查放在“本地测试通过后、合入测试分支前”，用于拦截产品口径、代码质量和测试遗漏问题。
- 发布生产环境前必须有人确认；定时任务只负责准备、校验和进入等待确认状态。
- 新增功能由产品负责人发起或确认；630 版本如无细粒度触发权限，先通过团队规范和人工确认约束。
- DTS 事件触发可作为后续扩展；630 版本先用临时任务输入 DTS 单号。

## 前置空间配置

- 团队空间：`AgentSpace 产品研发`。
- 成员：产品、架构、前端、后端、测试、发布负责人；产品和研发负责人设为管理员。
- Skill：文档检索、代码检索、Codehub 读写、差异审查、测试建议生成、发布清单生成。
- MCP：Codehub、eDevOps FE/DTS、CI/CD、测试平台、AgentCenter 查询、文档仓库访问。
- Agent 角色：产品分析 Agent、架构看护 Agent、前端开发 Agent、后端开发 Agent、代码审查 Agent、测试分析 Agent、发布检查 Agent、竞品分析 Agent。
- 知识库：`docs/product/`、架构规范、接口约定、研发规范、测试规范、发布规范。
- 环境变量：Codehub 仓库地址、默认分支、测试分支、集成测试分支、发布分支、eDevOps 项目标识、CI/CD 项目标识；敏感凭证用敏感变量保存。
- AgentFlow：按下文活动创建并发布；事件任务和定时任务只绑定已发布 AgentFlow。
- Publish：完成 Skill、MCP、Agent、AgentFlow、知识库和环境变量保存后，执行 Harness Publish。

## 1. 已有功能特性增量开发

**目标**：对已有功能模块做演进开发，并把 FE 状态同步到 eDevOps。

**配置方式**：临时任务，执行 `AgentFlow`。

**需要配置**：

- AgentFlow：`/feature-increment-dev`。
- 初始输入：`新建FE xxx`，说明该 FE 属于已有功能模块的演进开发。
- Step 1 `design-update`：产品分析 Agent 更新功能设计，输出 `/work/design-update.md`。
- Step 2 `fe-sync-design`：产品分析 Agent 新建或更新 FE，状态同步为 `设计`。
- Step 3 `frontend-dev`：前端开发 Agent 修改前端代码，输出变更摘要。
- Step 4 `backend-dev`：后端开发 Agent 修改后端代码或接口适配，输出变更摘要。
- Step 5 `local-test`：测试分析 Agent 执行本地测试，输出 `/work/local-test-report.md`。
- Step 6 `code-review`：代码审查 Agent 审查差异和测试结果，输出 `/work/code-review.md`。
- Step 7 `merge-test-branch`：前后端开发 Agent 按审查结果修复后提交并合入测试分支。
- Step 8 `fe-sync-test`：产品分析 Agent 将 FE 状态按进展同步为 `开发` / `测试`。
- 产物清单：`/work/design-update.md`、`/work/local-test-report.md`、`/work/code-review.md`。
- 人工确认：Step 1 由产品确认；Step 6 由模块负责人确认。

**完成标准**：设计更新完成，代码合入测试分支，FE 状态进入测试阶段。

## 2. 定时部署到测试环境

**目标**：定时检查测试分支更新，自动部署测试环境并生成测试结论。

**配置方式**：定时任务，执行 `AgentFlow`。

**需要配置**：

- 任务名称：`AgentSpace 测试环境定时部署`。
- AgentFlow：`/deploy-test-env`。
- 调度规则：按团队约定周期执行，例如工作日固定时间。
- Step 1 `branch-check`：发布检查 Agent 检查测试分支是否有新提交。
- Step 2 `ci-deploy`：发布检查 Agent 在有更新时触发 CI 并部署测试环境；无更新时输出本次跳过。
- Step 3 `full-regression`：测试分析 Agent 调用测试平台跑全量功能用例。
- Step 4 `test-report`：测试分析 Agent 生成 `/work/test-report.md`。
- Step 5 `dts-create`：测试分析 Agent 对失败用例创建 DTS，标识功能模块、负责人、失败用例和环境信息。
- 产物清单：`/work/test-report.md`。
- 人工确认：CI 部署失败或大批量用例失败时进入人工确认。

**完成标准**：测试环境部署结果明确；测试报告生成；失败用例已关联 DTS。

## 3. 缺陷修复

**目标**：根据 DTS 单号定位、修复并同步缺陷状态。

**配置方式**：临时任务，未来可扩展为 DTS 事件任务。

**需要配置**：

- AgentFlow：`/dts-fix`。
- 初始输入：`DTS 单号 xxx`。
- Step 1 `dts-analysis`：测试分析 Agent 读取 DTS 描述、复现步骤、关联模块和责任人。
- Step 2 `root-cause`：前端或后端开发 Agent 定位问题，输出 `/work/root-cause.md`。
- Step 3 `design-or-code-update`：产品分析 Agent 判断是否需要更新设计；开发 Agent 修复代码。
- Step 4 `local-test`：测试分析 Agent 执行本地回归测试，输出 `/work/dts-test-report.md`。
- Step 5 `code-review`：代码审查 Agent 审查修复差异和回归结果，输出 `/work/dts-code-review.md`。
- Step 6 `merge-test-branch`：开发 Agent 按审查结果修复后提交并合入测试分支。
- Step 7 `dts-sync`：测试分析 Agent 将 DTS 状态同步为已修复或待验证。
- 产物清单：`/work/root-cause.md`、`/work/dts-test-report.md`、`/work/dts-code-review.md`。
- 人工确认：Step 5 由缺陷负责人确认。

**完成标准**：DTS 根因明确，修复代码合入测试分支，缺陷状态已同步。

## 4. 发版本

**目标**：将测试通过的 FE 和 DTS 汇总到发布链路，完成文档、分支、CI 和生产部署确认。

**配置方式**：定时任务或发布日前临时任务，执行 `AgentFlow`。

**需要配置**：

- 任务名称：`AgentSpace 发版本准备`。
- AgentFlow：`/release-version`。
- Step 1 `scope-freeze`：发布检查 Agent 查询测试通过的 FE 和 DTS，生成 `/work/release-scope.md`。
- Step 2 `merge-integration-branch`：发布检查 Agent 将范围内变更合入集成测试分支。
- Step 3 `integration-test`：测试分析 Agent 触发 CI 和集成测试，生成 `/work/integration-test-report.md`。
- Step 4 `product-acceptance`：产品分析 Agent 汇总发布范围、集成测试结果、FE/DTS 状态和核心场景验收项，输出 `/work/product-acceptance.md`；产品负责人确认通过、驳回或有条件通过。
- Step 5 `doc-refresh`：产品分析 Agent 基于已验收范围刷新产品文档、发布说明和知识库文档，生成 `/work/release-notes.md`。
- Step 6 `merge-release-branch`：发布检查 Agent 合入发布分支并触发 CI。
- Step 7 `prod-deploy-approval`：发布负责人确认生产部署。
- Step 8 `prod-deploy`：发布检查 Agent 部署生产环境并记录结果。
- 产物清单：`/work/release-scope.md`、`/work/integration-test-report.md`、`/work/product-acceptance.md`、`/work/release-notes.md`。
- 人工确认：Step 4 和 Step 7 必须开启。

**完成标准**：发布范围和产品验收经产品确认；产品文档、发布说明和知识库已刷新；发布分支 CI 通过；生产部署经发布负责人确认后完成。

## 5. 新增功能特性开发

**目标**：从 0 到 1 新增功能模块，并创建 FE、设计文档和开发任务链路。

**配置方式**：临时任务，执行 `AgentFlow`；仅产品负责人发起或确认。

**需要配置**：

- AgentFlow：`/new-feature-dev`。
- 初始输入：`新增功能 xxx`，说明这是之前没有的功能模块。
- Step 1 `product-doc`：产品分析 Agent 更新产品功能文档，输出 `/work/new-feature-product-doc.md`。
- Step 2 `fe-create`：产品分析 Agent 在 eDevOps 新建 FE，状态同步为 `设计`。
- Step 3 `product-approval`：产品负责人审核功能文档和 FE 信息。
- Step 4 `architecture-doc`：架构看护 Agent 新增功能特性架构文档，输出 `/work/new-feature-architecture.md`。
- Step 5 `architecture-approval`：架构负责人审核架构文档。
- Step 6 `owner-assign`：研发负责人指派功能特性开发责任人。
- Step 7 `frontend-dev`：前端开发 Agent 完成前端代码。
- Step 8 `backend-dev`：后端开发 Agent 完成后端代码。
- Step 9 `local-test`：测试分析 Agent 完成本地测试，输出 `/work/new-feature-test-report.md`。
- Step 10 `code-review`：代码审查 Agent 审查代码和测试结果，输出 `/work/new-feature-code-review.md`。
- Step 11 `merge-test-branch`：开发 Agent 按审查结果修复后提交并合入测试分支。
- 产物清单：`/work/new-feature-product-doc.md`、`/work/new-feature-architecture.md`、`/work/new-feature-test-report.md`、`/work/new-feature-code-review.md`。
- 人工确认：Step 3、Step 5、Step 10 必须开启。

**完成标准**：FE 已创建并同步，产品和架构审核通过，代码合入测试分支。

## 6. 竞品分析

**目标**：定期分析竞品和竞品新功能，形成 AgentSpace 功能建议。

**配置方式**：定时任务，执行单 Agent 或轻量 AgentFlow。

**需要配置**：

- 任务名称：`AgentSpace 竞品分析`。
- 执行方式：竞品分析 Agent。
- 调度规则：按周或按月执行。
- 使用项目文件夹：产品定位、已有功能清单、历史竞品分析。
- 预置 Prompt：分析新竞品、竞品新功能、可借鉴能力、对 AgentSpace 的价值和实现成本。
- 产物清单：`/work/competitor-analysis.md`、`/work/feature-suggestions.md`。
- 人工确认：产品负责人确认是否转入新增功能特性开发。

**完成标准**：输出竞品分析和功能建议；经产品确认后才进入新增功能开发流程。
