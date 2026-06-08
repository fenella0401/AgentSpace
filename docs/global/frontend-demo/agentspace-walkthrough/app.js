const features = [
  {
    id: "F-001",
    title: "团队空间初始化与 Harness 配置",
    node: "空间与 Harness 配置",
    summary: "演示零配置空间、七类配置中心、Workflow 画布、Hook 模拟、依赖补全和发布状态。",
    coverage: [
      "零配置空间可直接开始作业",
      "AGENT.md、Skill、Tool、Agent、Workflow、Hook、环境变量七中心",
      "Workflow 画布、Hook 模拟、配置测试与发布",
      "历史配置只导入为草稿，发布后才生效",
    ],
    questions: [
      "AGENT.md 上下文预算与加载范围仍需正式数值",
      "市场资源契约与 Secret Service 细节待联调确认",
    ],
  },
  {
    id: "F-002",
    title: "基于 RBAC 的团队空间权限管理",
    node: "权限与访问控制",
    summary: "演示成员管理、角色矩阵、创建者保护、访客只读和租户管理员无空间角色阻断。",
    coverage: [
      "创建者和管理员可添加、调整、移除非创建者成员",
      "团队成员可创建任务但不能配置 Harness",
      "访客只读，不展示写操作入口",
      "租户管理员不自动拥有团队空间内容权限",
    ],
    questions: [
      "角色变更后的前端权限刷新时机按 PRD 暂定处理",
    ],
  },
  {
    id: "F-003",
    title: "租户管理与租户权限治理",
    node: "租户与管理面",
    summary: "演示全局租户列表、租户详情、租户管理员授权、停用租户只读和创建空间租户选择。",
    coverage: [
      "系统管理员可创建、启停和维护租户",
      "系统管理员可授予或撤销租户管理员",
      "租户管理员只查看被授权租户管理面",
      "团队空间创建前必须完成有效租户上下文校验",
    ],
    questions: [
      "租户编码、名称、归属组织字段规则待正式确认",
      "devuc 身份来源和异常错误码待接口确认",
    ],
  },
  {
    id: "F-004",
    title: "团队模板配置与应用",
    node: "空间与 Harness 配置",
    summary: "演示全局/租户模板、可选模块、跳过模板、分项应用状态和失败项重试。",
    coverage: [
      "模板内容全部可选，跳过模板仍可创建空间",
      "Tool 只允许市场引用",
      "模板应用按资源记录成功、待补全和失败",
      "失败项可补全依赖后单独重试",
    ],
    questions: [
      "模板版本、发布、停用、复制、回滚和历史保留策略待确认",
      "模板应用后是否允许整体重新应用待确认，当前 demo 只展示逐项维护",
      "全局模板与租户模板同名排序规则待确认",
    ],
  },
  {
    id: "F-005",
    title: "团队空间 Agent 任务创建与运行",
    node: "Agent 任务与对话协作",
    summary: "演示执行对象选择、知识域快照、Workflow 运行图、人工确认、Hook 决定和 R-0730 输入增强预览。",
    coverage: [
      "默认 Agent、自定义 Agent、Workflow 可选",
      "本轮知识域进入 Harness 快照",
      "Workflow 等待确认与 Hook 等待确认映射为待输入",
      "R-0730 的 / 功能菜单与 @ 文件菜单作为预览",
    ],
    questions: [
      "Workflow 与 Hook 确认超时的精确 UI 倒计时规则待确认",
    ],
  },
  {
    id: "F-006",
    title: "团队 Agent 任务历史与详情查看",
    node: "Agent 任务与对话协作",
    summary: "演示任务看板、筛选搜索分页、详情时间线、最终输出、plan 弹窗、脱敏和事件流恢复。",
    coverage: [
      "任务清单展示名称、创建人、状态和最新更新时间",
      "支持全部、我的、今天、近一周筛选与搜索分页",
      "详情展示执行过程、最终输出和修改文件",
      "运行中可追加事件，事件流中断不清空已有内容",
    ],
    questions: [
      "相对更新时间在真实实现中需每 60 秒刷新",
    ],
  },
  {
    id: "F-007",
    title: "Agent 文档变更审阅与确认",
    node: "Agent 任务与对话协作",
    summary: "演示文档变更卡片、内容查看、差异查看、接受、拒绝、冲突、只读和权限不足态。",
    coverage: [
      "待接受提案展示文档名称、路径、类型和状态",
      "具备权限用户可查看提议内容和差异",
      "任务创建人可接受或拒绝",
      "冲突、已接受、已拒绝不再展示提交类按钮",
    ],
    questions: [
      "差异查看器的最终组件和长文档性能策略待前端确认",
    ],
  },
];

const state = {
  featureId: "F-001",
  role: "团队管理员",
  tenantId: "tenant-a",
  spaceId: "space-a",
  taskStatus: "运行中",
  taskActor: "任务创建人",
  notice: "已载入中文串讲演示，所有数据均为浏览器内存模拟数据。",
  selectedResource: "Workflow",
  reviewedResources: [],
  selectedTemplateId: "tpl-a",
  executionTarget: "workflow-review",
  selectedDomains: ["domain-product", "domain-dev"],
  confirmation: "等待处理",
  hookDecision: "等待确认",
  slashPreview: false,
  mentionPreview: false,
  taskFilter: "全部",
  taskSearch: "",
  taskPage: 1,
  historyEmpty: false,
  selectedTaskId: "task-a",
  detailsCollapsed: true,
  planModal: false,
  streamInterrupted: false,
  selectedProposalId: "proposal-a",
  proposalTab: "内容",
};

const data = {
  roles: ["系统管理员", "租户管理员", "空间创建者", "团队管理员", "团队成员", "访客", "租户管理员无空间角色"],
  taskActors: ["任务创建人", "非任务创建人"],
  tenants: [
    {
      id: "tenant-a",
      name: "启明业务租户",
      code: "QIMING",
      org: "企业应用中心",
      status: "启用",
      admins: ["李青", "周澜"],
      spaces: 4,
      audit: "今日 6 条",
    },
    {
      id: "tenant-b",
      name: "星河运营租户",
      code: "XINGHE",
      org: "运营效率部",
      status: "停用",
      admins: [],
      spaces: 2,
      audit: "今日 1 条",
    },
    {
      id: "tenant-c",
      name: "合规研究租户",
      code: "HEGUI",
      org: "法务与安全部",
      status: "启用",
      admins: ["陈岳"],
      spaces: 1,
      audit: "今日 3 条",
    },
  ],
  spaces: [
    { id: "space-a", tenantId: "tenant-a", name: "产品交付空间", owner: "李青", project: "PRJ-1042", members: 8, status: "正常" },
    { id: "space-b", tenantId: "tenant-a", name: "客服知识空间", owner: "周澜", project: "PRJ-2048", members: 5, status: "正常" },
    { id: "space-c", tenantId: "tenant-b", name: "运营复盘空间", owner: "苏念", project: "PRJ-3099", members: 6, status: "租户停用只读" },
  ],
  members: [
    { id: "m1", name: "李青", account: "liqing", role: "创建者", status: "有效", joined: "2026-06-01", updated: "刚刚" },
    { id: "m2", name: "周澜", account: "zhoulan", role: "管理员", status: "有效", joined: "2026-06-02", updated: "2 分钟前" },
    { id: "m3", name: "陈岳", account: "chenyue", role: "团队成员", status: "有效", joined: "2026-06-03", updated: "16 分钟前" },
    { id: "m4", name: "苏念", account: "sunian", role: "访客", status: "有效", joined: "2026-06-04", updated: "今天 10:24" },
  ],
  harnessResources: [
    { type: "AGENT.md", name: "空间级指引", status: "已发布", health: "available", detail: "加载 3 个知识域，预算 24k" },
    { type: "Skill", name: "需求拆解 Skill", status: "测试通过", health: "available", detail: "依赖 1 个市场 Tool" },
    { type: "Tool", name: "研发知识检索 Tool", status: "已发布", health: "available", detail: "市场版本 v2.1，授权有效" },
    { type: "Agent", name: "产品 Agent", status: "草稿", health: "degraded", detail: "缺少 1 个环境变量" },
    { type: "Workflow", name: "产品到开发串联流程", status: "测试通过", health: "available", detail: "7 个节点，含条件、并行、子 Workflow 和输出节点" },
    { type: "Hook", name: "高风险 Tool 调用审批", status: "待补全", health: "blocked", detail: "需要管理员确认策略" },
    { type: "环境变量", name: "检索服务访问令牌", status: "已发布", health: "available", detail: "敏感值已托管，不回填" },
  ],
  templates: [
    {
      id: "tpl-a",
      scope: "全局模板",
      name: "标准产品交付模板",
      status: "已发布",
      modules: [
        { type: "知识库", enabled: true, status: "已发布" },
        { type: "AGENT.md", enabled: true, status: "已发布" },
        { type: "Skill", enabled: true, status: "已发布" },
        { type: "Tool", enabled: true, status: "市场引用" },
        { type: "Agent", enabled: true, status: "已发布" },
        { type: "Workflow", enabled: true, status: "模拟通过" },
        { type: "Hook", enabled: false, status: "未启用" },
        { type: "环境变量", enabled: true, status: "待补全" },
      ],
      items: [
        { type: "AGENT.md", status: "成功", reason: "已形成发布版本" },
        { type: "Workflow", status: "成功", reason: "模拟测试通过" },
        { type: "Tool", status: "待补全", reason: "需要目标空间重新授权" },
        { type: "环境变量", status: "失败", reason: "缺少安全引用" },
      ],
    },
    {
      id: "tpl-b",
      scope: "租户模板",
      name: "客服知识模板",
      status: "草稿",
      modules: [
        { type: "知识库", enabled: true, status: "草稿" },
        { type: "AGENT.md", enabled: true, status: "草稿" },
        { type: "Skill", enabled: false, status: "未启用" },
        { type: "Tool", enabled: false, status: "未启用" },
        { type: "Agent", enabled: false, status: "未启用" },
        { type: "Workflow", enabled: false, status: "未启用" },
        { type: "Hook", enabled: false, status: "未启用" },
        { type: "环境变量", enabled: false, status: "未启用" },
      ],
      items: [
        { type: "知识库", status: "成功", reason: "目录已创建" },
      ],
    },
  ],
  domains: [
    { id: "domain-product", name: "产品定义", desc: "PRD、功能树、验收口径" },
    { id: "domain-dev", name: "研发实现", desc: "架构、接口、发布流程" },
    { id: "domain-customer", name: "客服知识", desc: "常见问题、话术、工单" },
  ],
  workflowNodes: [
    { name: "产品 Agent", status: "完成", desc: "拆解需求和验收点" },
    { name: "条件节点", status: "完成", desc: "判断风险等级和分支" },
    { name: "人工确认", status: "等待确认", desc: "确认范围和风险" },
    { name: "并行节点", status: "等待执行", desc: "等待全部或快速失败" },
    { name: "开发 Agent", status: "等待执行", desc: "生成实现方案" },
    { name: "子 Workflow", status: "等待执行", desc: "调用发布检查流程" },
    { name: "输出节点", status: "等待执行", desc: "汇总最终输出" },
  ],
  hookExecutions: [
    { name: "任务开始审计", event: "task.started", result: "允许", reason: "审计记录已写入" },
    { name: "高风险 Tool 调用审批", event: "tool.call.before", result: "等待确认", reason: "命中 production 条件" },
  ],
  tasks: [
    {
      id: "task-a",
      title: "重设计 Harness 配置中心",
      creator: "李青",
      status: "运行中",
      updated: "2 分钟前",
      date: "今天",
      owner: true,
      instruction: "基于 F-001 更新七中心配置体验，重点确认 Workflow 与 Hook 的入口。",
      events: [
        { time: "09:12", title: "读取功能设计", body: "产品 Agent 读取 F-001、F-005 和发布计划，抽取七中心、Workflow、Hook 规则。" },
        { time: "09:17", title: "调用协作 Agent", body: "开发 Agent 梳理前端组件：HarnessOverview、WorkflowCanvas、HookRuleBuilder。" },
        { time: "09:22", title: "工具调用结果", body: "读取 docs/function-design/F-001...，普通文件路径完整展示。" },
        { time: "09:26", title: "敏感信息脱敏", body: "环境变量令牌命中安全策略，展示为已隐藏，不暴露原始值。" },
      ],
      output: "建议以七中心总览承接零配置状态，每个资源保留已发布版本和编辑草稿。Workflow 与 Hook 进入专用配置页，发布前必须测试与查看差异。",
      files: ["docs/function-design/F-001-team-space-initialization-and-harness-configuration.md", ".agentspace/workflows/product-delivery.yaml"],
    },
    {
      id: "task-b",
      title: "梳理租户管理员权限边界",
      creator: "周澜",
      status: "完成静止态",
      updated: "今天 10:12",
      date: "今天",
      owner: false,
      instruction: "确认租户管理员是否可以进入团队空间内容。",
      events: [
        { time: "10:03", title: "权限矩阵复核", body: "租户管理员仅管理租户面，不自动获得团队空间角色。" },
      ],
      output: "租户管理员可在租户管理面查看团队空间清单，但进入空间内容必须具备 SpaceMember 角色。",
      files: ["docs/function-design/F-002-team-space-rbac-permission-management.md"],
    },
    {
      id: "task-c",
      title: "生成客服知识模板",
      creator: "陈岳",
      status: "待输入",
      updated: "昨天 18:40",
      date: "近一周",
      owner: false,
      instruction: "生成租户级客服知识模板。",
      events: [],
      output: "等待创建人补充市场 Tool 授权。",
      files: [],
    },
    {
      id: "task-d",
      title: "文档变更冲突处理",
      creator: "李青",
      status: "异常静止态",
      updated: "3 天前",
      date: "近一周",
      owner: true,
      instruction: "处理 Agent 生成的新文档路径冲突。",
      events: [
        { time: "16:24", title: "路径冲突", body: "目标路径已存在，提案进入冲突状态。" },
      ],
      output: "正式文档保持不变，需要重新生成提案。",
      files: ["docs/global/product-use.md"],
    },
  ],
  proposals: [
    {
      id: "proposal-a",
      doc: "Harness 配置串讲说明",
      path: "docs/global/product-use.md",
      type: "修改已有文档",
      status: "待接受",
      updated: "刚刚",
      readonlyReason: "非任务创建人和访客只能查看，不能接受或拒绝。",
      content: "本次更新补充 Harness 七中心、Workflow 运行图和 Hook 审批说明。空间在零配置时仍可使用平台默认 Agent。",
      diff: [
        "- 原文：空间创建后需要先完成配置。",
        "+ 新文：空间创建后可直接使用平台默认 Agent，配置可逐项完善。",
        "+ 新增：Workflow 与 Hook 发布前必须完成模拟测试和差异确认。",
      ],
    },
    {
      id: "proposal-b",
      doc: "团队模板应用记录",
      path: "docs/global/template-application.md",
      type: "生成新文档",
      status: "冲突",
      updated: "12 分钟前",
      readonlyReason: "提案已冲突，不展示接受或拒绝按钮。",
      content: "模板应用记录用于说明成功、待补全和失败项的恢复路径。",
      diff: [
        "+ 新文档：模板应用记录",
        "+ 状态：成功、待补全、失败",
        "+ 处理：失败项单独重试，不重复创建已成功资源",
      ],
    },
  ],
};

const app = document.querySelector("#app");

function feature() {
  return features.find((item) => item.id === state.featureId) || features[0];
}

function tenant() {
  return data.tenants.find((item) => item.id === state.tenantId) || data.tenants[0];
}

function space() {
  return data.spaces.find((item) => item.id === state.spaceId) || data.spaces[0];
}

function hasSpaceRole() {
  return ["空间创建者", "团队管理员", "团队成员", "访客"].includes(state.role);
}

function canManageSpace() {
  return ["空间创建者", "团队管理员"].includes(state.role);
}

function canCreateAgentTask() {
  return ["空间创建者", "团队管理员", "团队成员"].includes(state.role);
}

function isTaskCreator() {
  return state.taskActor === "任务创建人";
}

function selectedTemplate() {
  return data.templates.find((item) => item.id === state.selectedTemplateId) || data.templates[0];
}

function selectedTask() {
  return data.tasks.find((item) => item.id === state.selectedTaskId) || data.tasks[0];
}

function selectedProposal() {
  return data.proposals.find((item) => item.id === state.selectedProposalId) || data.proposals[0];
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function badgeClass(value) {
  if (["启用", "已发布", "成功", "允许", "完成", "测试通过", "模拟通过", "available"].includes(value)) return "green";
  if (["运行中", "待输入", "等待确认", "待补全", "草稿", "degraded"].includes(value)) return "orange";
  if (["停用", "失败", "异常静止态", "冲突", "阻止", "blocked"].includes(value)) return "red";
  if (["市场引用", "预览", "租户模板"].includes(value)) return "purple";
  return "blue";
}

function statusBadge(value, extra = "") {
  return `<span class="badge ${badgeClass(value)} ${extra}">${escapeHtml(value)}</span>`;
}

function button(label, action, options = {}) {
  const variant = options.variant ? ` ${options.variant}` : "";
  const small = options.small ? " small" : "";
  const disabled = options.disabled ? " disabled" : "";
  const attrs = options.value ? ` data-value="${escapeHtml(options.value)}"` : "";
  return `<button class="button${variant}${small}" data-action="${action}"${attrs}${disabled}>${escapeHtml(label)}</button>`;
}

function render() {
  const current = feature();
  app.innerHTML = `
    <div class="workspace">
      ${renderSidebar()}
      <main class="main">
        ${renderTopbar(current)}
        <section class="content">
          <div class="feature-header">
            <div>
              <h2>${current.id} ${escapeHtml(current.title)}</h2>
              <p>${escapeHtml(current.summary)}</p>
              <div class="status-strip">
                ${statusBadge(current.node, "dark")}
                ${statusBadge("中文串讲")}
                ${statusBadge("内存模拟数据")}
                ${state.featureId === "F-005" ? statusBadge("R-0730 预览") : ""}
              </div>
            </div>
            <div class="toolbar">
              ${button("重置当前场景", "reset-feature")}
              ${button("标记疑问", "mark-question", { variant: "primary" })}
            </div>
          </div>
          ${renderFeature()}
        </section>
      </main>
      ${renderInspector(current)}
    </div>
    ${state.notice ? `<div class="toast">${escapeHtml(state.notice)}</div>` : ""}
    ${state.planModal ? renderPlanModal() : ""}
  `;
}

function renderSidebar() {
  return `
    <aside class="sidebar">
      <div class="brand">
        <h1 class="brand-title">AgentSpace</h1>
        <p class="brand-subtitle">全局前端演示 · 开发串讲版</p>
      </div>
      <nav class="nav-list" aria-label="功能导航">
        ${features
          .map((item) => `
            <button class="nav-button ${item.id === state.featureId ? "active" : ""}" data-action="set-feature" data-value="${item.id}">
              <span class="nav-id">${item.id}</span>
              <span class="nav-title">${escapeHtml(item.title)}</span>
            </button>
          `)
          .join("")}
      </nav>
    </aside>
  `;
}

function renderTopbar(current) {
  return `
    <header class="topbar">
      <div class="topbar-title">
        <h1>${escapeHtml(current.node)}</h1>
        <p>当前上下文：${escapeHtml(tenant().name)} / ${escapeHtml(space().name)} / ${escapeHtml(state.taskStatus)}</p>
      </div>
      <div class="context-controls">
        <div class="control">
          <label for="role">当前角色</label>
          <select id="role" data-bind="role">
            ${data.roles.map((role) => `<option ${role === state.role ? "selected" : ""}>${escapeHtml(role)}</option>`).join("")}
          </select>
        </div>
        <div class="control">
          <label for="tenant">当前租户</label>
          <select id="tenant" data-bind="tenantId">
            ${data.tenants.map((item) => `<option value="${item.id}" ${item.id === state.tenantId ? "selected" : ""}>${escapeHtml(item.name)}</option>`).join("")}
          </select>
        </div>
        <div class="control">
          <label for="space">当前空间</label>
          <select id="space" data-bind="spaceId">
            ${data.spaces.map((item) => `<option value="${item.id}" ${item.id === state.spaceId ? "selected" : ""}>${escapeHtml(item.name)}</option>`).join("")}
          </select>
        </div>
        <div class="control">
          <label for="task-status">任务状态</label>
          <select id="task-status" data-bind="taskStatus">
            ${["运行中", "待输入", "完成静止态", "异常静止态"].map((item) => `<option ${item === state.taskStatus ? "selected" : ""}>${item}</option>`).join("")}
          </select>
        </div>
        <div class="control">
          <label for="task-actor">任务身份</label>
          <select id="task-actor" data-bind="taskActor">
            ${data.taskActors.map((item) => `<option ${item === state.taskActor ? "selected" : ""}>${item}</option>`).join("")}
          </select>
        </div>
      </div>
    </header>
  `;
}

function renderInspector(current) {
  return `
    <aside class="inspector">
      <section class="inspector-section">
        <h3>PRD / 验收覆盖</h3>
        <ul>${current.coverage.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>
      </section>
      <section class="inspector-section">
        <h3>待澄清问题</h3>
        <ul>${current.questions.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>
      </section>
      <section class="inspector-section">
        <h3>串讲提示</h3>
        <ul>
          <li>顶部角色切换可触发权限态变化。</li>
          <li>按钮只改变浏览器内存状态，不保存数据。</li>
          <li>遇到未定策略时以“待澄清”展示，不替产品定稿。</li>
        </ul>
      </section>
    </aside>
  `;
}

function renderFeature() {
  const map = {
    "F-001": renderF001,
    "F-002": renderF002,
    "F-003": renderF003,
    "F-004": renderF004,
    "F-005": renderF005,
    "F-006": renderF006,
    "F-007": renderF007,
  };
  return (map[state.featureId] || renderF001)();
}

function renderF001() {
  const defaultReady = data.harnessResources.some((item) => item.type === "Agent" && item.status !== "已发布");
  return `
    <div class="grid">
      <section class="panel">
        <div class="panel-header">
          <div>
            <h3 class="panel-title">空间创建完成</h3>
            <p class="panel-subtitle">零配置不是异常，空间可立即使用平台默认 Agent。</p>
          </div>
          ${statusBadge(defaultReady ? "可直接作业" : "配置完整")}
        </div>
        <div class="panel-body grid two">
          <div class="notice success">
            <strong>当前空间使用平台默认 Agent，可直接开始对话。</strong>
            <p class="muted">不展示强制完成百分比，也不把空中心标记为空间异常。</p>
            <div class="toolbar">
              ${button("开始作业", "jump-runtime", { variant: "primary" })}
              ${button("完善 Harness", "focus-resource", { value: "总览" })}
            </div>
          </div>
          <div class="notice">
            <strong>历史配置导入</strong>
            <p class="muted">历史配置只会导入为草稿，确认发布后才会生效。</p>
            <div class="toolbar">${button("扫描历史目录", "legacy-import")}${button("查看映射预览", "legacy-preview")}</div>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <div>
            <h3 class="panel-title">Harness 七中心</h3>
            <p class="panel-subtitle">资源版本状态、依赖状态和同步状态分开展示。</p>
          </div>
          <div class="toolbar">
            ${button("AI 帮我配置", "ai-draft")}
            ${button("从市场选择", "market-pick")}
            ${button("手动创建", "manual-create")}
          </div>
        </div>
        <div class="panel-body grid three">
          ${data.harnessResources.map(renderHarnessCard).join("")}
        </div>
      </section>

      <section class="grid two">
        <div class="panel">
          <div class="panel-header">
            <div>
              <h3 class="panel-title">Workflow 画布</h3>
              <p class="panel-subtitle">Skill 和 Tool 作为 Agent 能力，不作为用户侧 Workflow 节点。</p>
            </div>
            ${statusBadge("测试通过")}
          </div>
          <div class="panel-body">
            ${renderWorkflowCanvas()}
            <div class="toolbar" style="margin-top: 12px;">
              ${button("图校验", "workflow-check")}
              ${button("模拟运行", "workflow-simulate", { variant: "primary" })}
              ${button("查看版本差异", "show-diff")}
            </div>
          </div>
        </div>
        <div class="panel">
          <div class="panel-header">
            <div>
              <h3 class="panel-title">Hook 模拟测试</h3>
              <p class="panel-subtitle">示例：高风险 Tool 调用审批。</p>
            </div>
            ${statusBadge("待补全")}
          </div>
          <div class="panel-body grid">
            <div class="notice warning"><strong>该 Hook 会阻止任务操作，请先完成模拟测试。</strong></div>
            <div class="item-row">
              <div class="item-main"><strong>事件</strong><span class="muted tiny">tool.call.before</span></div>
              ${statusBadge("同步阻止")}
            </div>
            <div class="item-row">
              <div class="item-main"><strong>条件</strong><span class="muted tiny">high 或 production</span></div>
              ${statusBadge("高风险")}
            </div>
            <div class="item-row">
              <div class="item-main"><strong>动作</strong><span class="muted tiny">团队管理员确认、超时阻止、写审计</span></div>
              ${statusBadge("等待确认")}
            </div>
            <div class="toolbar">
              ${button("确认通过", "hook-test-pass")}
              ${button("模拟拒绝", "hook-test-reject", { variant: "danger" })}
            </div>
          </div>
        </div>
      </section>
    </div>
  `;
}

function renderHarnessCard(item) {
  const reviewed = state.reviewedResources.includes(item.type);
  const canAttemptPublish = item.status === "测试通过";
  return `
    <div class="panel">
      <div class="panel-body">
        <div class="split-row">
          <div class="item-main">
            <strong>${escapeHtml(item.type)}</strong>
            <span class="muted tiny">${escapeHtml(item.name)}</span>
          </div>
          ${statusBadge(item.status)}
        </div>
        <p class="muted">${escapeHtml(item.detail)}</p>
        ${item.status === "测试通过" ? `<div class="status-strip">${statusBadge(reviewed ? "差异已确认" : "待查看差异")}</div>` : ""}
        <div class="toolbar">
          ${button("测试", "resource-test", { small: true, value: item.type })}
          ${button("发布", "resource-publish", { small: true, value: item.type, disabled: !canAttemptPublish })}
          ${button("查看差异", "resource-diff", { small: true, value: item.type })}
        </div>
      </div>
    </div>
  `;
}

function renderWorkflowCanvas() {
  return `
    <div class="canvas">
      <div class="node-map">
        ${data.workflowNodes
          .map((node, index) => `
            <div class="node ${node.status === "等待确认" ? "active" : ""}">
              <h4>${escapeHtml(node.name)}</h4>
              ${statusBadge(node.status)}
              <p>${escapeHtml(node.desc)}</p>
            </div>
            ${index < data.workflowNodes.length - 1 ? `<span class="connector">→</span>` : ""}
          `)
          .join("")}
      </div>
    </div>
  `;
}

function renderF002() {
  const readonly = state.role === "访客" || state.role === "租户管理员无空间角色";
  const canManage = canManageSpace();
  const managementOnly = state.role === "系统管理员" || state.role === "租户管理员";
  return `
    <div class="grid">
      ${managementOnly ? `
        <div class="notice warning">
          <strong>${escapeHtml(state.role)}不能绕过团队空间角色直接管理空间内容。</strong>
          <p class="muted">全局或租户管理身份只提供管理面视图；成员、知识库和 Harness 写权限仍按 SpaceMember 角色判断。</p>
        </div>
      ` : ""}
      ${state.role === "租户管理员无空间角色" ? `
        <div class="notice danger">
          <strong>你可以在租户管理面查看该空间，但需要团队空间授权后才能进入内容。</strong>
          <p class="muted">租户管理员身份不自动继承团队空间写权限。</p>
        </div>
      ` : ""}
      ${state.role === "访客" ? `<div class="notice warning"><strong>当前角色仅支持查看。</strong><p class="muted">不展示任务创建、继续输入、邀请、保存、删除、发布和运行类主按钮。</p></div>` : ""}
      <section class="panel">
        <div class="panel-header">
          <div>
            <h3 class="panel-title">成员管理</h3>
            <p class="panel-subtitle">${escapeHtml(space().name)} · ${escapeHtml(tenant().name)} · 当前角色 ${escapeHtml(state.role)}</p>
          </div>
          <div class="toolbar">
            ${button("添加成员", "add-member", { variant: "primary", disabled: !canManage })}
            ${button("模拟权限不足", "permission-denied")}
          </div>
        </div>
        <div class="panel-body">
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>姓名</th><th>账号</th><th>角色</th><th>状态</th><th>加入时间</th><th>最近更新</th><th>操作</th>
                </tr>
              </thead>
              <tbody>
                ${data.members.map((member) => `
                  <tr>
                    <td><strong>${escapeHtml(member.name)}</strong></td>
                    <td>${escapeHtml(member.account)}</td>
                    <td>
                      ${member.role === "创建者" ? `${statusBadge("创建者")} <span class="muted tiny">系统生成</span>` : `
                        <select class="field" data-member-role="${member.id}" ${canManage && !readonly ? "" : "disabled"}>
                          ${["管理员", "团队成员", "访客"].map((role) => `<option ${role === member.role ? "selected" : ""}>${role}</option>`).join("")}
                        </select>
                      `}
                    </td>
                    <td>${statusBadge(member.status)}</td>
                    <td>${escapeHtml(member.joined)}</td>
                    <td>${escapeHtml(member.updated)}</td>
                    <td>
                      ${member.role === "创建者"
                        ? `<span class="muted tiny">当前版本不支持移除、降级或移交</span>`
                        : button("移除", "remove-member", { small: true, variant: "danger", value: member.id, disabled: !canManage || readonly })}
                    </td>
                  </tr>
                `).join("")}
              </tbody>
            </table>
          </div>
        </div>
      </section>
      <section class="grid two">
        <div class="panel">
          <div class="panel-header"><h3 class="panel-title">权限态</h3></div>
          <div class="panel-body grid">
            <div class="item-row"><div class="item-main"><strong>成员管理入口</strong><span class="muted tiny">空间创建者、团队管理员可见</span></div>${statusBadge(canManage ? "可用" : "隐藏")}</div>
            <div class="item-row"><div class="item-main"><strong>任务创建入口</strong><span class="muted tiny">空间创建者、团队管理员、团队成员可用</span></div>${statusBadge(canCreateAgentTask() ? "可用" : "禁用")}</div>
            <div class="item-row"><div class="item-main"><strong>Harness 写操作</strong><span class="muted tiny">团队成员和访客不可用</span></div>${statusBadge(canManage ? "可用" : "禁用")}</div>
            <div class="item-row"><div class="item-main"><strong>后端鉴权</strong><span class="muted tiny">前端权限态只作体验优化</span></div>${statusBadge("以后端为准")}</div>
          </div>
        </div>
        <div class="panel">
          <div class="panel-header"><h3 class="panel-title">异常承接</h3></div>
          <div class="panel-body grid">
            <div class="notice"><strong>成员添加失败</strong><p class="muted">保留目标用户和角色草稿，允许创建者或管理员重试。</p></div>
            <div class="notice"><strong>角色更新失败</strong><p class="muted">保留原角色，不产生部分成功的权限数据。</p></div>
            <div class="notice warning"><strong>审计写入失败</strong><p class="muted">成员和角色写操作不会静默成功。</p></div>
          </div>
        </div>
      </section>
    </div>
  `;
}

function renderF003() {
  const currentTenant = tenant();
  const tenantSpaces = data.spaces.filter((item) => item.tenantId === currentTenant.id);
  const disabled = currentTenant.status === "停用";
  return `
    <div class="grid">
      <section class="grid two">
        <div class="panel">
          <div class="panel-header">
            <div>
              <h3 class="panel-title">全局租户列表</h3>
              <p class="panel-subtitle">仅系统管理员可访问全局管理面。</p>
            </div>
            ${button("新建租户", "create-tenant", { variant: "primary", disabled: state.role !== "系统管理员" })}
          </div>
          <div class="panel-body">
            ${data.tenants.map((item) => `
              <div class="item-row">
                <div class="item-main">
                  <strong>${escapeHtml(item.name)}</strong>
                  <span class="muted tiny">${escapeHtml(item.code)} · ${escapeHtml(item.org)} · 管理员 ${item.admins.length} · 空间 ${item.spaces}</span>
                </div>
                <div class="toolbar">
                  ${statusBadge(item.status)}
                  ${button("查看", "select-tenant", { small: true, value: item.id })}
                </div>
              </div>
            `).join("")}
          </div>
        </div>
        <div class="panel">
          <div class="panel-header">
            <div>
              <h3 class="panel-title">租户详情</h3>
              <p class="panel-subtitle">${escapeHtml(currentTenant.code)} · ${escapeHtml(currentTenant.org)} · 审计 ${escapeHtml(currentTenant.audit)}</p>
            </div>
            ${statusBadge(currentTenant.status)}
          </div>
          <div class="panel-body grid">
            ${disabled ? `<div class="notice warning"><strong>租户已停用，当前仅支持查看；不能新增团队空间、新增租户管理员或修改租户级配置。</strong></div>` : ""}
            <div class="metric-row"><span>租户管理员</span><strong>${currentTenant.admins.length ? currentTenant.admins.join("、") : "暂无租户管理员"}</strong></div>
            <div class="metric-row"><span>团队空间数量</span><strong>${tenantSpaces.length}</strong></div>
            <div class="toolbar">
              ${button(currentTenant.status === "启用" ? "停用租户" : "启用租户", "toggle-tenant", { disabled: state.role !== "系统管理员" })}
              ${button("授权租户管理员", "grant-admin", { disabled: state.role !== "系统管理员" || disabled })}
              ${button("撤销最后一个管理员", "revoke-admin", { variant: "danger", disabled: state.role !== "系统管理员" || currentTenant.admins.length === 0 })}
            </div>
          </div>
        </div>
      </section>

      <section class="grid two">
        <div class="panel">
          <div class="panel-header">
            <div>
              <h3 class="panel-title">租户团队空间清单</h3>
              <p class="panel-subtitle">租户管理员可查看清单，但不自动进入空间内容。</p>
            </div>
            ${statusBadge(state.role)}
          </div>
          <div class="panel-body">
            ${tenantSpaces.length ? tenantSpaces.map((item) => `
              <div class="item-row">
                <div class="item-main">
                  <strong>${escapeHtml(item.name)}</strong>
                  <span class="muted tiny">创建者 ${escapeHtml(item.owner)} · 项目 ${escapeHtml(item.project)} · 成员 ${item.members}</span>
                </div>
                <div class="toolbar">
                  ${statusBadge(item.status)}
                  ${button("进入内容", "enter-space", { small: true, disabled: !hasSpaceRole() })}
                </div>
              </div>
            `).join("") : `<div class="empty">当前租户暂无团队空间</div>`}
          </div>
        </div>
        <div class="panel">
          <div class="panel-header">
            <div>
              <h3 class="panel-title">团队空间创建租户上下文</h3>
              <p class="panel-subtitle">创建前必须校验 tenantId、租户状态和空间创建权限。</p>
            </div>
            ${statusBadge(disabled ? "创建禁用" : "待校验")}
          </div>
          <div class="panel-body grid">
            <label class="control">
              <span class="muted tiny">所属租户</span>
              <select data-bind="tenantId" ${disabled ? "disabled" : ""}>
                ${data.tenants.map((item) => `<option value="${item.id}" ${item.id === state.tenantId ? "selected" : ""}>${escapeHtml(item.name)}</option>`).join("")}
              </select>
            </label>
            ${disabled ? `<div class="notice danger">租户停用或用户无空间创建权限时，禁用创建入口并展示原因。</div>` : `<div class="notice success">租户上下文可用，空间创建事务可消费该 tenantId。</div>`}
            <div class="toolbar">${button("校验租户上下文", "validate-tenant", { variant: "primary", disabled })}${button("模拟 devuc 失败", "devuc-fail", { variant: "danger" })}</div>
          </div>
        </div>
      </section>
    </div>
  `;
}

function renderF004() {
  const tpl = selectedTemplate();
  return `
    <div class="grid">
      <section class="panel">
        <div class="panel-header">
          <div>
            <h3 class="panel-title">团队模板编辑器</h3>
            <p class="panel-subtitle">每个模块默认未启用，管理员按需添加。Tool 模块只提供市场选择器。</p>
          </div>
          <div class="toolbar">
            <select class="field" data-bind="selectedTemplateId">
              ${data.templates.map((item) => `<option value="${item.id}" ${item.id === tpl.id ? "selected" : ""}>${escapeHtml(item.name)}</option>`).join("")}
            </select>
            ${statusBadge(tpl.scope)}
            ${statusBadge(tpl.status)}
          </div>
        </div>
        <div class="panel-body grid four">
          ${tpl.modules.map((module) => `
            <div class="panel">
              <div class="panel-body">
                <div class="split-row">
                  <div class="item-main">
                    <strong>${escapeHtml(module.type)}</strong>
                    <span class="muted tiny">${module.enabled ? "已纳入模板" : "默认未启用"}</span>
                  </div>
                  ${statusBadge(module.status)}
                </div>
                <p class="muted">${module.type === "Tool" ? "仅从 AI 市场选择，不配置底层连接。" : "发布前执行同类资源校验和模拟应用。"}</p>
                ${button(module.enabled ? "移出模块" : "启用模块", "toggle-template-module", { small: true, value: module.type })}
              </div>
            </div>
          `).join("")}
        </div>
      </section>

      <section class="grid two">
        <div class="panel">
          <div class="panel-header">
            <div>
              <h3 class="panel-title">空间创建向导</h3>
              <p class="panel-subtitle">团队创建者可以选择模板，也可以跳过模板直接创建空间。</p>
            </div>
            ${statusBadge("零配置可用")}
          </div>
          <div class="panel-body grid">
            <div class="notice success"><strong>跳过模板，直接开始</strong><p class="muted">稍后仍可在 Harness 中逐项配置。</p></div>
            <div class="notice"><strong>${escapeHtml(tpl.name)}</strong><p class="muted">模板后续更新不自动改变已应用空间。</p></div>
            <div class="toolbar">
              ${button("跳过模板", "skip-template")}
              ${button("应用模板", "apply-template", { variant: "primary" })}
            </div>
          </div>
        </div>
        <div class="panel">
          <div class="panel-header">
            <div>
              <h3 class="panel-title">模板应用状态</h3>
              <p class="panel-subtitle">成功、待补全和失败项独立记录，失败项可单独重试。</p>
            </div>
            ${statusBadge("部分失败")}
          </div>
          <div class="panel-body">
            ${tpl.items.map((item) => `
              <div class="item-row">
                <div class="item-main">
                  <strong>${escapeHtml(item.type)}</strong>
                  <span class="muted tiny">${escapeHtml(item.reason)}</span>
                </div>
                <div class="toolbar">
                  ${statusBadge(item.status)}
                  ${button("重试", "retry-template-item", { small: true, value: item.type, disabled: item.status === "成功" })}
                </div>
              </div>
            `).join("")}
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <div>
            <h3 class="panel-title">需求待澄清</h3>
            <p class="panel-subtitle">演示不补全未确认策略，只把风险放在串讲视野里。</p>
          </div>
          ${statusBadge("待澄清")}
        </div>
        <div class="panel-body grid three">
          <div class="notice warning">模板版本、复制、回滚和历史版本保留策略待确认。</div>
          <div class="notice warning">模板应用后是否允许整体重新应用待确认；当前只展示逐项维护。</div>
          <div class="notice warning">全局模板与租户模板同名时的排序和展示规则待确认。</div>
        </div>
      </section>
    </div>
  `;
}

function renderF005() {
  const canCreate = canCreateAgentTask();
  const canHandleRuntime = isTaskCreator() && canCreate;
  const managementOnly = state.role === "系统管理员" || state.role === "租户管理员";
  return `
    <div class="grid">
      ${managementOnly ? `
        <div class="notice warning">
          <strong>${escapeHtml(state.role)}不能仅凭管理面身份创建或处理团队空间任务。</strong>
          <p class="muted">任务创建、继续输入、回答询问和确认处理都必须基于团队空间角色。</p>
        </div>
      ` : ""}
      <section class="panel">
        <div class="panel-header">
          <div>
            <h3 class="panel-title">新建 Agent 任务</h3>
            <p class="panel-subtitle">执行对象选择器独立于 R-0730 的 / 与 @ 输入增强。</p>
          </div>
          ${statusBadge(canCreate ? "可创建" : "无创建权限")}
        </div>
        <div class="panel-body grid">
          <div class="segmented">
            ${[
              ["default-agent", "空间默认 Agent"],
              ["agent-product", "产品 Agent"],
              ["workflow-review", "产品到开发 Workflow"],
            ].map(([value, label]) => `<button class="segment ${state.executionTarget === value ? "active" : ""}" data-action="set-target" data-value="${value}">${label}</button>`).join("")}
          </div>
          <div class="grid three">
            ${data.domains.map((domain) => `
              <button class="panel" data-action="toggle-domain" data-value="${domain.id}" aria-pressed="${state.selectedDomains.includes(domain.id)}">
                <div class="panel-body">
                  <div class="split-row"><strong>${escapeHtml(domain.name)}</strong>${statusBadge(state.selectedDomains.includes(domain.id) ? "已选择" : "未选择")}</div>
                  <p class="muted">${escapeHtml(domain.desc)}</p>
                </div>
              </button>
            `).join("")}
          </div>
          <div class="input-row">
            <textarea class="field" data-bind="runtimeDraft">请基于 F-001 和 F-005 生成开发串讲提纲，重点说明 Workflow 和 Hook 的运行边界。</textarea>
            ${button("创建任务", "create-runtime-task", { variant: "primary", disabled: !canCreate })}
          </div>
          <div class="toolbar">
            ${button("输入 / 打开功能菜单", "toggle-slash")}
            ${button("输入 @ 打开文件菜单", "toggle-mention")}
          </div>
          ${state.slashPreview ? `<div class="notice"><strong>R-0730 功能菜单预览</strong><p class="muted">可选择：总结当前任务、生成测试用例、创建发布公告。取消选择时按普通文本提交。</p></div>` : ""}
          ${state.mentionPreview ? `<div class="notice"><strong>R-0730 文件菜单预览</strong><p class="muted">可引用当前空间内有读取权限的知识文件；跨空间、无权限或超限时阻止提交。</p></div>` : ""}
        </div>
      </section>

      <section class="grid two">
        <div class="panel">
          <div class="panel-header">
            <div>
              <h3 class="panel-title">Workflow 运行图</h3>
              <p class="panel-subtitle">选择 Workflow 后创建 WorkflowRun，并按图执行节点。</p>
            </div>
            ${statusBadge(state.taskStatus)}
          </div>
          <div class="panel-body">
            ${renderWorkflowCanvas()}
            <div class="notice warning" style="margin-top: 12px;">
              <strong>人工确认节点暂停 Workflow。</strong>
              <p class="muted">创建人处理后恢复对应分支；确认使用统一确认记录和幂等提交。</p>
              <div class="toolbar">
                ${statusBadge(state.confirmation)}
                ${button("批准", "runtime-confirm", { value: "已批准", disabled: !canHandleRuntime })}
                ${button("拒绝", "runtime-confirm", { variant: "danger", value: "已拒绝", disabled: !canHandleRuntime })}
                ${button("重试 Agent 节点", "retry-workflow-node", { disabled: !canHandleRuntime })}
                ${button("取消 Workflow", "cancel-workflow", { variant: "danger", disabled: !canHandleRuntime })}
              </div>
            </div>
          </div>
        </div>
        <div class="panel">
          <div class="panel-header">
            <div>
              <h3 class="panel-title">Hook 决定</h3>
              <p class="panel-subtitle">同步 Hook 可以继续、阻止或要求确认。</p>
            </div>
            ${statusBadge(state.hookDecision)}
          </div>
          <div class="panel-body">
            ${data.hookExecutions.map((hook) => `
              <div class="item-row">
                <div class="item-main">
                  <strong>${escapeHtml(hook.name)}</strong>
                  <span class="muted tiny">${escapeHtml(hook.event)} · ${escapeHtml(hook.reason)}</span>
                </div>
                ${statusBadge(hook.result)}
              </div>
            `).join("")}
            <div class="toolbar" style="margin-top: 12px;">
              ${button("允许 Tool 调用", "hook-decision", { value: "允许", disabled: !canHandleRuntime })}
              ${button("阻止 Tool 调用", "hook-decision", { variant: "danger", value: "阻止", disabled: !canHandleRuntime })}
              ${button("要求人工确认", "hook-decision", { value: "等待确认", disabled: !canHandleRuntime })}
            </div>
          </div>
        </div>
      </section>
    </div>
  `;
}

function filteredTasks() {
  if (state.historyEmpty) return [];
  let items = data.tasks.slice();
  if (state.taskFilter === "我的") items = items.filter((item) => item.owner);
  if (state.taskFilter === "今天") items = items.filter((item) => item.date === "今天");
  if (state.taskFilter === "近一周") items = items.filter((item) => item.date === "今天" || item.date === "近一周");
  if (state.taskSearch.trim()) {
    const keyword = state.taskSearch.trim();
    items = items.filter((item) => item.title.includes(keyword));
  }
  return items;
}

function renderF006() {
  const tasks = filteredTasks();
  const perPage = 20;
  const pageCount = Math.max(1, Math.ceil(tasks.length / perPage));
  const page = Math.min(state.taskPage, pageCount);
  const currentPageTasks = tasks.slice((page - 1) * perPage, page * perPage);
  const task = selectedTask();
  return `
    <div class="grid">
      <section class="panel">
        <div class="panel-header">
          <div>
            <h3 class="panel-title">任务历史看板</h3>
            <p class="panel-subtitle">默认按最新更新时间倒序；筛选或搜索变化时重置第一页。</p>
          </div>
          ${statusBadge("总数 " + tasks.length)}
        </div>
        <div class="panel-body grid">
          <div class="toolbar">
            <div class="segmented">
              ${["全部", "我的", "今天", "近一周"].map((item) => `<button class="segment ${item === state.taskFilter ? "active" : ""}" data-action="set-task-filter" data-value="${item}">${item}</button>`).join("")}
            </div>
            <input class="search-field" data-bind="taskSearch" value="${escapeHtml(state.taskSearch)}" placeholder="按对话名称搜索" />
            ${button(state.historyEmpty ? "恢复任务数据" : "模拟空空间", "toggle-empty-history")}
          </div>
          ${currentPageTasks.length ? `
            <div class="table-wrap">
              <table>
                <thead><tr><th>对话名称</th><th>创建人</th><th>状态</th><th>最新更新时间</th><th>操作</th></tr></thead>
                <tbody>
                  ${currentPageTasks.map((item) => `
                    <tr>
                      <td><strong>${escapeHtml(item.title)}</strong></td>
                      <td>${escapeHtml(item.creator)}</td>
                      <td>${statusBadge(item.status)}</td>
                      <td>${escapeHtml(item.updated)}</td>
                      <td>${button("查看详情", "select-task", { small: true, value: item.id })}</td>
                    </tr>
                  `).join("")}
                </tbody>
              </table>
            </div>
            <div class="split-row">
              <span class="muted tiny">第 ${page} / ${pageCount} 页，每页 ${perPage} 条</span>
              <div class="toolbar">
                ${button("上一页", "prev-page", { disabled: page <= 1 })}
                ${button("下一页", "next-page", { disabled: page >= pageCount })}
              </div>
            </div>
          ` : `<div class="empty">${state.historyEmpty ? "当前空间暂无 Agent 任务" : "没有符合条件的 Agent 任务"}</div>`}
        </div>
      </section>

      <section class="grid two">
        <div class="panel">
          <div class="panel-header">
            <div>
              <h3 class="panel-title">任务详情</h3>
              <p class="panel-subtitle">${escapeHtml(task.title)} · ${escapeHtml(task.creator)} · ${escapeHtml(task.updated)}</p>
            </div>
            ${statusBadge(task.status)}
          </div>
          <div class="panel-body grid">
            <div class="notice"><strong>用户指令</strong><p class="muted">${escapeHtml(task.instruction)}</p></div>
            <div class="split-row">
              <strong>Agent 详细执行过程</strong>
              <div class="toolbar">
                ${button(state.detailsCollapsed ? "展开过程" : "收起过程", "toggle-details")}
                ${button("追加运行事件", "append-event")}
                ${button("模拟事件流中断", "interrupt-stream", { variant: "danger" })}
              </div>
            </div>
            ${state.streamInterrupted ? `<div class="notice warning"><strong>事件流中断</strong><p class="muted">保留已展示事件，并允许重新查询已保存事件。</p>${button("重新查询事件", "recover-stream")}</div>` : ""}
            ${state.detailsCollapsed ? `<div class="notice">收到最终输出开始阶段事件后默认收起详细执行过程，用户可点击展开。</div>` : renderTimeline(task.events)}
          </div>
        </div>
        <div class="panel">
          <div class="panel-header">
            <div>
              <h3 class="panel-title">最终输出与修改文件</h3>
              <p class="panel-subtitle">修改文件入口跳转或调起 F-007 文档变更审阅。</p>
            </div>
            ${button("查看 plan / 进度", "open-plan", { variant: "primary" })}
          </div>
          <div class="panel-body grid">
            <div class="notice success"><strong>最终 Agent 输出</strong><p class="muted">${escapeHtml(task.output)}</p></div>
            ${task.files.length ? task.files.map((file) => `
              <div class="item-row">
                <div class="item-main"><strong>${escapeHtml(file)}</strong><span class="muted tiny">普通文件路径完整展示，不被摘要替代。</span></div>
                ${button("审阅变更", "jump-proposal", { small: true })}
              </div>
            `).join("") : `<div class="empty">暂无修改文件</div>`}
          </div>
        </div>
      </section>
    </div>
  `;
}

function renderTimeline(events) {
  if (!events.length) {
    return `<div class="empty">暂无详细执行过程，但详情仍展示用户指令、最终输出和修改文件。</div>`;
  }
  return `
    <div class="timeline">
      ${events.map((event) => `
        <div class="timeline-item">
          <div class="timeline-time">${escapeHtml(event.time)}</div>
          <div class="timeline-card">
            <strong>${escapeHtml(event.title)}</strong>
            <span class="muted">${escapeHtml(event.body)}</span>
          </div>
        </div>
      `).join("")}
    </div>
  `;
}

function renderPlanModal() {
  return `
    <div class="modal-backdrop" role="dialog" aria-modal="true" aria-label="plan 进度弹窗">
      <div class="modal">
        <div class="modal-header">
          <h3>Agent plan 与进度</h3>
          ${button("关闭", "close-plan", { small: true })}
        </div>
        <div class="modal-body grid">
          <div class="notice"><strong>当前任务状态：${escapeHtml(state.taskStatus)}</strong><p class="muted">关闭弹窗不影响主对话界面继续展示或输入。</p></div>
          ${["读取 PRD 与验收标准", "生成串讲结构", "等待人工确认", "准备最终输出"].map((step, index) => `
            <div class="item-row">
              <div class="item-main"><strong>${index + 1}. ${escapeHtml(step)}</strong><span class="muted tiny">最新进度事件已保存</span></div>
              ${statusBadge(index < 2 ? "完成" : index === 2 ? "等待确认" : "等待执行")}
            </div>
          `).join("")}
        </div>
        <div class="modal-footer">${button("知道了", "close-plan", { variant: "primary" })}</div>
      </div>
    </div>
  `;
}

function renderF007() {
  const proposal = selectedProposal();
  const canViewProposal = hasSpaceRole();
  const canHandleProposal = isTaskCreator() && canCreateAgentTask();
  const readonly = !canHandleProposal;
  const processed = proposal.status !== "待接受";
  return `
    <div class="grid">
      <section class="grid two">
        <div class="panel">
          <div class="panel-header">
            <div>
              <h3 class="panel-title">文档变更提案</h3>
              <p class="panel-subtitle">agent core 返回文档变更事件后创建待接受提案。</p>
            </div>
            ${statusBadge(state.role)}
          </div>
          <div class="panel-body">
            ${canViewProposal ? data.proposals.map((item) => `
              <div class="item-row">
                <div class="item-main">
                  <strong>${escapeHtml(item.doc)}</strong>
                  <span class="muted tiny">${escapeHtml(item.path)} · ${escapeHtml(item.type)} · ${escapeHtml(item.updated)}</span>
                </div>
                <div class="toolbar">
                  ${statusBadge(item.status)}
                  ${button("打开", "select-proposal", { small: true, value: item.id })}
                </div>
              </div>
            `).join("") : `<div class="empty">无任务查看权限，不能查看文档变更提案</div>`}
          </div>
        </div>
        <div class="panel">
          <div class="panel-header">
            <div>
              <h3 class="panel-title">提案处理</h3>
              <p class="panel-subtitle">${canViewProposal ? `${escapeHtml(proposal.doc)} · ${escapeHtml(proposal.path)}` : "权限不足，无法查看提案摘要"}</p>
            </div>
            ${statusBadge(canViewProposal ? proposal.status : "权限不足")}
          </div>
          <div class="panel-body grid">
            ${!canViewProposal ? `<div class="notice danger"><strong>无任务查看权限，不能查看文档变更提案。</strong><p class="muted">需要先获得团队空间访问权限。</p></div>` : ""}
            ${readonly && canViewProposal ? `<div class="notice warning"><strong>当前用户仅可查看，不能接受或拒绝。</strong><p class="muted">${escapeHtml(proposal.readonlyReason)}</p></div>` : ""}
            ${proposal.status === "冲突" ? `<div class="notice danger"><strong>正式文档已变化，需要用户重新查看或等待后续处理。</strong></div>` : ""}
            <div class="segmented">
              ${["内容", "差异"].map((tab) => `<button class="segment ${state.proposalTab === tab ? "active" : ""}" data-action="proposal-tab" data-value="${tab}">${tab}</button>`).join("")}
            </div>
            ${canViewProposal ? (state.proposalTab === "内容" ? renderProposalContent(proposal) : renderProposalDiff(proposal)) : `<div class="empty">权限不足，无法查看内容和差异</div>`}
            ${canHandleProposal && !processed ? `
              <div class="toolbar">
                ${button("接受变更", "accept-proposal", { variant: "primary" })}
                ${button("拒绝变更", "reject-proposal", { variant: "danger" })}
                ${button("模拟版本冲突", "conflict-proposal")}
              </div>
            ` : `
              <div class="notice">已处理、冲突、非任务创建人或无空间写权限时，不展示提交类按钮。</div>
            `}
            <div class="toolbar">
              ${button("模拟权限不足", "proposal-denied")}
            </div>
          </div>
        </div>
      </section>
      <section class="panel">
        <div class="panel-header">
          <div>
            <h3 class="panel-title">验收状态覆盖</h3>
            <p class="panel-subtitle">已处理提案不再展示提交类按钮，差异加载失败不影响查看内容。</p>
          </div>
          ${statusBadge("审计写入")}
        </div>
        <div class="panel-body grid three">
          <div class="notice">接受生成新文档后创建正式知识库文档。</div>
          <div class="notice">接受修改已有文档后正式文档更新为提议版本。</div>
          <div class="notice warning">接受时基准版本不一致会阻止写入并进入冲突。</div>
        </div>
      </section>
    </div>
  `;
}

function renderProposalContent(proposal) {
  return `<div class="notice"><strong>提议版本内容</strong><p class="muted">${escapeHtml(proposal.content)}</p></div>`;
}

function renderProposalDiff(proposal) {
  return `
    <pre class="code-block">${proposal.diff.map((line) => {
      const cls = line.startsWith("+") ? "diff-add" : line.startsWith("-") ? "diff-del" : "";
      return `<span class="${cls}">${escapeHtml(line)}</span>`;
    }).join("\n")}</pre>
  `;
}

function setNotice(message) {
  state.notice = message;
  window.clearTimeout(setNotice.timer);
  setNotice.timer = window.setTimeout(() => {
    state.notice = "";
    render();
  }, 2800);
}

function resetFeature() {
  const id = state.featureId;
  Object.assign(state, {
    selectedResource: "Workflow",
    reviewedResources: [],
    confirmation: "等待处理",
    hookDecision: "等待确认",
    slashPreview: false,
    mentionPreview: false,
    taskFilter: "全部",
    taskSearch: "",
    taskPage: 1,
    historyEmpty: false,
    selectedTaskId: "task-a",
    detailsCollapsed: true,
    planModal: false,
    streamInterrupted: false,
    selectedProposalId: "proposal-a",
    proposalTab: "内容",
  });
  state.featureId = id;
  setNotice("当前场景已重置为串讲初始状态。");
}

function handleAction(action, value) {
  const currentTenant = tenant();
  const tpl = selectedTemplate();
  const task = selectedTask();
  const proposal = selectedProposal();

  switch (action) {
    case "set-feature":
      state.featureId = value;
      state.notice = "";
      break;
    case "reset-feature":
      resetFeature();
      return;
    case "mark-question":
      setNotice("已标记：串讲时应先确认右侧待澄清问题，再进入开发拆解。");
      break;
    case "jump-runtime":
      state.featureId = "F-005";
      setNotice("已跳转到任务创建与运行串讲面。");
      break;
    case "focus-resource":
      setNotice(`已聚焦 ${value}，可继续演示配置中心入口。`);
      break;
    case "legacy-import":
      setNotice("已扫描历史配置目录：只生成草稿，不直接生效。");
      break;
    case "legacy-preview":
      setNotice("映射预览显示不支持项与敏感值处理结果。");
      break;
    case "ai-draft":
      setNotice("AI 生成结构化草稿，发布前仍需人工测试和查看差异。");
      break;
    case "market-pick":
      setNotice("市场入口只选择已发布 Skill、Tool 或 Agent，不配置底层连接。");
      break;
    case "manual-create":
      setNotice("已进入手动创建草稿流程。");
      break;
    case "resource-test":
      updateResource(value, "测试通过");
      setNotice(`${value} 已完成确定性测试。`);
      break;
    case "resource-publish":
      {
        const resource = data.harnessResources.find((item) => item.type === value);
        if (!resource || resource.status !== "测试通过") {
          setNotice(`${value} 发布被阻止：必须先测试通过且依赖完整。`);
          break;
        }
        if (!state.reviewedResources.includes(value)) {
          setNotice(`${value} 发布被阻止：请先查看并确认版本差异。`);
          break;
        }
        updateResource(value, "已发布");
        setNotice(`${value} 已发布，当前生效版本已更新。`);
      }
      break;
    case "resource-diff":
      if (!state.reviewedResources.includes(value)) state.reviewedResources.push(value);
      setNotice(`${value} 差异已打开并确认：发布前需要人工确认。`);
      break;
    case "workflow-check":
      setNotice("Workflow 图校验通过：节点、分支、人工确认与输出节点完整。");
      break;
    case "workflow-simulate":
      setNotice("Workflow 模拟运行通过：人工确认节点会暂停并等待恢复。");
      break;
    case "show-diff":
      setNotice("已展示 Workflow 版本差异。");
      break;
    case "hook-test-pass":
      updateResource("Hook", "测试通过");
      setNotice("Hook 模拟测试通过，高风险 Tool 需管理员确认后继续。");
      break;
    case "hook-test-reject":
      setNotice("Hook 模拟拒绝：主动作被阻止，审计记录完整。");
      break;
    case "add-member":
      if (!data.members.some((item) => item.id === "m5")) {
        data.members.push({ id: "m5", name: "林岚", account: "linlan", role: "团队成员", status: "有效", joined: "刚刚", updated: "刚刚" });
      }
      setNotice("已添加团队成员；真实实现需由后端写审计后生效。");
      break;
    case "remove-member":
      data.members = data.members.filter((item) => item.id !== value);
      setNotice("已移除非创建者成员；创建者保护仍不可移除。");
      break;
    case "permission-denied":
      setNotice("后端返回权限不足：前端刷新权限集合并切换只读或阻断状态。");
      break;
    case "select-tenant":
      state.tenantId = value;
      setNotice("已切换租户详情。");
      break;
    case "create-tenant":
      if (!data.tenants.some((item) => item.id === "tenant-demo")) {
        data.tenants.push({ id: "tenant-demo", name: "新建演示租户", code: "DEMO", org: "演示组织", status: "启用", admins: [], spaces: 0, audit: "刚写入 1 条" });
      }
      state.tenantId = "tenant-demo";
      setNotice("租户已创建，并写入创建审计。");
      break;
    case "toggle-tenant":
      currentTenant.status = currentTenant.status === "启用" ? "停用" : "启用";
      setNotice(currentTenant.status === "启用" ? "租户已启用。" : "租户已停用，管理面进入只读。");
      break;
    case "grant-admin":
      if (!currentTenant.admins.includes("林岚")) currentTenant.admins.push("林岚");
      setNotice("租户管理员已授权，目标用户可看到该租户管理面入口。");
      break;
    case "revoke-admin":
      currentTenant.admins.pop();
      setNotice(currentTenant.admins.length ? "租户管理员已撤销。" : "最后一个租户管理员已撤销，由系统管理员兜底治理。");
      break;
    case "enter-space":
      setNotice(hasSpaceRole() ? "已进入团队空间内容。" : "需要团队空间授权后才能进入内容。");
      break;
    case "validate-tenant":
      setNotice("租户上下文校验通过，空间创建事务可继续。");
      break;
    case "devuc-fail":
      setNotice("devuc 鉴权失败：管理面读写请求默认拒绝。");
      break;
    case "toggle-template-module":
      {
        const module = tpl.modules.find((item) => item.type === value);
        if (module) {
          module.enabled = !module.enabled;
          module.status = module.enabled ? (module.type === "Tool" ? "市场引用" : "草稿") : "未启用";
        }
        setNotice(`${value} 模块状态已切换。`);
      }
      break;
    case "skip-template":
      setNotice("已跳过模板，空间仍可使用平台默认 Agent。");
      break;
    case "apply-template":
      setNotice("模板应用已创建分项记录，空间不等待全部项成功即可使用。");
      break;
    case "retry-template-item":
      {
        const item = tpl.items.find((entry) => entry.type === value);
        if (item) {
          item.status = "成功";
          item.reason = "重试成功，未重复创建已成功资源";
        }
        setNotice(`${value} 已单项重试成功。`);
      }
      break;
    case "set-target":
      state.executionTarget = value;
      setNotice("执行对象已切换，本轮快照会记录选择。");
      break;
    case "toggle-domain":
      if (state.selectedDomains.includes(value)) {
        state.selectedDomains = state.selectedDomains.filter((id) => id !== value);
      } else {
        state.selectedDomains.push(value);
      }
      setNotice("知识域选择已更新，将进入本轮 Harness 快照。");
      break;
    case "create-runtime-task":
      state.taskStatus = "运行中";
      setNotice("任务已创建，并生成包含七类 Harness 版本的快照。");
      break;
    case "toggle-slash":
      state.slashPreview = !state.slashPreview;
      break;
    case "toggle-mention":
      state.mentionPreview = !state.mentionPreview;
      break;
    case "runtime-confirm":
      state.confirmation = value;
      setNotice(value === "已批准" ? "Workflow 已按批准分支恢复。" : "Workflow 已按拒绝分支结束或失败。");
      break;
    case "retry-workflow-node":
      setNotice("已使用幂等键重试 Agent 节点，不产生重复外部副作用。");
      break;
    case "cancel-workflow":
      state.taskStatus = "异常静止态";
      setNotice("Workflow 已取消，后续不再创建新节点运行。");
      break;
    case "hook-decision":
      state.hookDecision = value;
      data.hookExecutions[1].result = value;
      setNotice(value === "阻止" ? "Hook 阻止主动作，不执行高风险 Tool。" : "Hook 决定已记录。");
      break;
    case "set-task-filter":
      state.taskFilter = value;
      state.taskPage = 1;
      break;
    case "toggle-empty-history":
      state.historyEmpty = !state.historyEmpty;
      state.taskPage = 1;
      setNotice(state.historyEmpty ? "已切换为空空间状态。" : "已恢复任务历史数据。");
      break;
    case "prev-page":
      state.taskPage = Math.max(1, state.taskPage - 1);
      break;
    case "next-page":
      state.taskPage += 1;
      break;
    case "select-task":
      state.selectedTaskId = value;
      state.detailsCollapsed = false;
      break;
    case "toggle-details":
      state.detailsCollapsed = !state.detailsCollapsed;
      break;
    case "append-event":
      task.events.push({ time: "刚刚", title: "新增运行事件", body: "事件流追加展示新增执行过程和进度事件。" });
      state.detailsCollapsed = false;
      setNotice("已追加运行事件。");
      break;
    case "interrupt-stream":
      state.streamInterrupted = true;
      break;
    case "recover-stream":
      state.streamInterrupted = false;
      setNotice("已重新查询保存事件，页面保留原有过程。");
      break;
    case "open-plan":
      state.planModal = true;
      break;
    case "close-plan":
      state.planModal = false;
      break;
    case "jump-proposal":
      state.featureId = "F-007";
      setNotice("已跳转到文档变更审阅能力。");
      break;
    case "select-proposal":
      state.selectedProposalId = value;
      break;
    case "proposal-tab":
      state.proposalTab = value;
      break;
    case "accept-proposal":
      proposal.status = "已接受";
      setNotice("文档变更已接受，正式文档更新并写入审计日志。");
      break;
    case "reject-proposal":
      proposal.status = "已拒绝";
      setNotice("文档变更已拒绝，正式文档保持基准版本。");
      break;
    case "conflict-proposal":
      proposal.status = "冲突";
      setNotice("接受时发现正式文档版本不一致，提案进入冲突。");
      break;
    case "proposal-denied":
      setNotice("接口返回权限不足：切换为只读态或展示权限不足提示。");
      break;
    default:
      break;
  }
  render();
}

function updateResource(type, status) {
  const resource = data.harnessResources.find((item) => item.type === type);
  if (resource) resource.status = status;
}

app.addEventListener("click", (event) => {
  const target = event.target.closest("[data-action]");
  if (!target || target.disabled) return;
  handleAction(target.dataset.action, target.dataset.value || "");
});

app.addEventListener("change", (event) => {
  const bind = event.target.dataset.bind;
  if (bind) {
    state[bind] = event.target.value;
    if (bind === "taskSearch") state.taskPage = 1;
    render();
    return;
  }
  const memberId = event.target.dataset.memberRole;
  if (memberId) {
    const member = data.members.find((item) => item.id === memberId);
    if (member) {
      member.role = event.target.value;
      member.updated = "刚刚";
      setNotice("成员角色已更新；后端权限立即按最新角色生效。");
    }
    render();
  }
});

app.addEventListener("input", (event) => {
  const bind = event.target.dataset.bind;
  if (bind === "taskSearch") {
    state.taskSearch = event.target.value;
    state.taskPage = 1;
    render();
  }
});

render();
