const state = {
  currentView: "sessions",
  currentRole: "团队管理员",
  currentTenantId: "tenant-a",
  currentSpaceId: "space-a",
  selectedSessionId: "session-order-api",
  selectedPipelineId: "pipeline-order-release",
  selectedProposalId: "proposal-api",
  selectedTenantId: "tenant-a",
  sessionFilter: "全部",
  proposalTab: "内容",
  detailMode: "对话流",
  composer: "",
  search: "",
  toast: "AgentSpace 已就绪",
};

const data = {
  roles: ["系统管理员", "租户管理员", "空间创建者", "团队管理员", "团队成员", "访客", "无空间角色"],
  tenants: [
    { id: "tenant-a", name: "启明业务租户", code: "QIMING", org: "企业应用中心", status: "启用", admins: ["李青", "周澜"], spaces: 4, audit: "今日 6 条" },
    { id: "tenant-b", name: "星河运营租户", code: "XINGHE", org: "运营效率部", status: "启用", admins: ["苏念"], spaces: 2, audit: "今日 2 条" },
    { id: "tenant-c", name: "合规研究租户", code: "HEGUI", org: "法务与安全部", status: "停用", admins: ["陈岳"], spaces: 1, audit: "今日 1 条" },
  ],
  spaces: [
    { id: "space-a", tenantId: "tenant-a", name: "产品交付空间", project: "PRJ-1042", owner: "李青", members: 18, status: "正常" },
    { id: "space-b", tenantId: "tenant-a", name: "客服知识空间", project: "PRJ-2048", owner: "周澜", members: 9, status: "正常" },
    { id: "space-c", tenantId: "tenant-b", name: "运营复盘空间", project: "PRJ-3099", owner: "苏念", members: 6, status: "正常" },
  ],
  members: [
    { id: "u1", name: "李青", account: "liqing", role: "创建者", status: "有效", joined: "2026-06-01", updated: "刚刚" },
    { id: "u2", name: "周澜", account: "zhoulan", role: "管理员", status: "有效", joined: "2026-06-02", updated: "8 分钟前" },
    { id: "u3", name: "陈岳", account: "chenyue", role: "团队成员", status: "有效", joined: "2026-06-03", updated: "今天 10:24" },
    { id: "u4", name: "苏念", account: "sunian", role: "访客", status: "有效", joined: "2026-06-04", updated: "昨天" },
  ],
  resources: [
    { id: "agent-md", group: "AGENT.md", name: "空间级指引", status: "已发布", detail: "适用于所有会话，3 个知识域命中" },
    { id: "agent-product", group: "Agent", name: "产品 Agent", status: "已发布", detail: "梳理目标、范围和输出口径" },
    { id: "agent-arch", group: "Agent", name: "架构 Agent", status: "测试通过", detail: "接口、领域模型和服务边界" },
    { id: "skill-openapi", group: "Skill", name: "OpenAPI 文档生成", status: "已发布", detail: "生成接口定义和字段说明" },
    { id: "tool-search", group: "Tool", name: "知识检索 Tool", status: "已发布", detail: "市场版本 v2.1，授权有效" },
    { id: "hook-risk", group: "Hook", name: "高风险 Tool 审批", status: "待补全", detail: "命中生产数据时等待管理员确认" },
    { id: "env-token", group: "环境变量", name: "检索服务令牌", status: "已发布", detail: "敏感值托管，仅保存安全引用" },
  ],
  templates: [
    { name: "标准产品交付模板", scope: "全局模板", status: "已发布", modules: "知识库、AGENT.md、Agent、Skill、Tool、流水线" },
    { name: "客服知识模板", scope: "租户模板", status: "草稿", modules: "知识库、AGENT.md、默认 Agent" },
  ],
  sessions: [
    {
      id: "session-order-api",
      title: "订单模块接口设计",
      status: "运行中",
      source: "手工会话",
      creator: "李青",
      owner: true,
      updated: "12:49",
      agent: "架构 Agent",
      summary: "输出订单服务接口定义、时序图和状态变更字段。",
      messages: [
        { id: "m1", side: "system", speaker: "系统", time: "12:42", body: "会话已基于订单模块规格、接口设计规范和空间指引创建资源快照。" },
        { id: "m2", side: "user", speaker: "李青", time: "12:43", body: "请基于订单模块规格，输出订单服务的 RESTful 接口定义，包括订单创建、查询、状态变更三个核心接口。同步给出主流程时序图。" },
        {
          id: "m3",
          side: "agent",
          speaker: "架构 Agent",
          time: "12:43",
          body: "已读取订单模块规格与接口设计规范，正在生成接口定义和流程图。",
          steps: ["调用 Skill：OpenAPI 文档生成", "调用 Tool：知识检索 Tool", "已生成 3 个接口、2 个字段组、1 张时序图"],
          artifacts: [
            { name: "order-service-api.yaml", type: "OpenAPI 3.1", meta: "2.4 KB · 已同步到知识库" },
            { name: "sequence-create-order.svg", type: "时序图", meta: "14 KB · 已同步到知识库" },
          ],
        },
        { id: "m4", side: "user", speaker: "李青", time: "12:48", body: "状态变更接口需要支持并发场景下的乐观锁，请补充版本号字段。同步让文档 Agent 更新到接口设计规范目录。" },
        { id: "m5", side: "agent", speaker: "文档 Agent", time: "12:49", body: "已收到任务。等待架构 Agent 完成补充后，我会同步更新接口设计规范并提交审阅。" },
      ],
    },
    {
      id: "session-template",
      title: "客服知识空间初始化",
      status: "待输入",
      source: "手工会话",
      creator: "周澜",
      owner: false,
      updated: "10:12",
      agent: "知识 Agent",
      summary: "等待补充目标分类和客服知识目录。",
      messages: [
        { id: "m1", side: "system", speaker: "系统", time: "10:04", body: "已创建客服知识空间会话。" },
        { id: "m2", side: "agent", speaker: "知识 Agent", time: "10:12", body: "请确认首批知识目录：售前咨询、订单问题、退款流程、账号权限。" },
      ],
    },
    {
      id: "session-pipeline-node",
      title: "支付回归验证任务",
      status: "完成静止态",
      source: "流水线 Agent 任务",
      creator: "系统",
      owner: false,
      updated: "昨天",
      agent: "测试 Agent",
      summary: "由支付发布流水线自动创建，输出回归结果。",
      messages: [
        { id: "m1", side: "system", speaker: "系统", time: "昨天", body: "流水线 Agent 任务已创建 Agent 会话。" },
        { id: "m2", side: "agent", speaker: "测试 Agent", time: "昨天", body: "回归用例全部通过，未发现阻断问题。" },
      ],
    },
  ],
  documentProposals: [
    {
      id: "proposal-api",
      title: "订单服务接口定义",
      path: "/知识库/订单服务/order-service-api.yaml",
      status: "待接受",
      owner: "李青",
      content: "新增订单创建、订单查询、订单状态变更接口，并补充 version 字段用于乐观锁控制。",
      diff: ["+ POST /orders", "+ GET /orders/{orderId}", "+ PATCH /orders/{orderId}/status", "+ version: integer"],
    },
    {
      id: "proposal-doc",
      title: "接口设计规范更新",
      path: "/知识库/规范/接口设计规范.md",
      status: "冲突",
      owner: "李青",
      content: "补充状态变更接口的版本号字段和错误码。",
      diff: ["- 状态变更只校验订单状态", "+ 状态变更需校验 version 字段", "+ 409: VERSION_CONFLICT"],
    },
  ],
  pipelineRuns: [
    {
      id: "pipeline-order-release",
      name: "订单服务发布流水线",
      status: "待审阅",
      creator: "李青",
      updated: "12:50",
      nodes: [
        { name: "需求分析", status: "完成", detail: "产品 Agent 输出需求范围与验收重点" },
        { name: "开发任务拆解", status: "运行中", reviewStatus: "待审阅", detail: "架构 Agent 输出开发任务清单" },
        { name: "开发任务执行", status: "等待执行", detail: "开发 Agent 按任务清单完成实现" },
        { name: "集成测试", status: "等待执行", detail: "测试 Agent 执行集成验证" },
      ],
    },
    {
      id: "pipeline-customer",
      name: "客服知识发布流水线",
      status: "运行中",
      creator: "周澜",
      updated: "10:18",
      nodes: [
        { name: "知识需求分析", status: "完成", detail: "知识 Agent 整理发布范围" },
        { name: "知识内容生成", status: "运行中", detail: "内容 Agent 生成知识条目" },
        { name: "敏感信息检查", status: "等待执行", detail: "安全 Agent 检查敏感内容" },
        { name: "发布验证", status: "等待执行", detail: "验证 Agent 检查知识库版本" },
      ],
    },
  ],
};

const navItems = [
  { id: "tenants", section: "租户与管理面", label: "租户管理" },
  { id: "space", section: "空间与 Harness 配置", label: "空间配置" },
  { id: "access", section: "权限与访问控制", label: "成员权限" },
  { id: "sessions", section: "Agent 会话与对话协作", label: "Agent 会话" },
  { id: "pipelines", section: "Harness CICD 流水线与任务运行", label: "流水线任务" },
];

const app = document.querySelector("#app");

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function tenant() {
  return data.tenants.find((item) => item.id === state.currentTenantId) || data.tenants[0];
}

function selectedTenant() {
  return data.tenants.find((item) => item.id === state.selectedTenantId) || tenant();
}

function space() {
  return data.spaces.find((item) => item.id === state.currentSpaceId) || data.spaces[0];
}

function session() {
  return data.sessions.find((item) => item.id === state.selectedSessionId) || data.sessions[0];
}

function proposal() {
  return data.documentProposals.find((item) => item.id === state.selectedProposalId) || data.documentProposals[0];
}

function pipeline() {
  return data.pipelineRuns.find((item) => item.id === state.selectedPipelineId) || data.pipelineRuns[0];
}

function canManageSpace() {
  return ["空间创建者", "团队管理员"].includes(state.currentRole);
}

function canCreateSession() {
  return ["空间创建者", "团队管理员", "团队成员"].includes(state.currentRole);
}

function canHandleRuntime() {
  return ["空间创建者", "团队管理员", "团队成员"].includes(state.currentRole);
}

function badgeClass(value) {
  if (["启用", "正常", "已发布", "完成", "完成静止态", "已接受", "通过", "可用"].includes(value)) return "ok";
  if (["运行中", "测试通过", "待输入", "待接受", "待审阅", "待补全", "草稿"].includes(value)) return "wait";
  if (["停用", "冲突", "失败", "异常静止态", "已拒绝", "无权限"].includes(value)) return "bad";
  return "neutral";
}

function badge(value) {
  return `<span class="badge ${badgeClass(value)}">${escapeHtml(value)}</span>`;
}

function button(label, action, options = {}) {
  const variant = options.variant ? ` ${options.variant}` : "";
  const small = options.small ? " small" : "";
  const disabled = options.disabled ? " disabled" : "";
  const value = options.value ? ` data-value="${escapeHtml(options.value)}"` : "";
  return `<button class="button${variant}${small}" data-action="${action}"${value}${disabled}>${escapeHtml(label)}</button>`;
}

function render() {
  app.innerHTML = `
    <div class="product-shell">
      ${renderSidebar()}
      <main class="main-shell">
        ${renderTopbar()}
        ${renderView()}
      </main>
    </div>
    ${state.toast ? `<div class="toast">${escapeHtml(state.toast)}</div>` : ""}
  `;
}

function renderSidebar() {
  const groups = navItems.reduce((acc, item) => {
    if (!acc[item.section]) acc[item.section] = [];
    acc[item.section].push(item);
    return acc;
  }, {});
  return `
    <aside class="sidebar">
      <div class="brand-row">
        <div class="brand-mark">AS</div>
        <div>
          <strong>AgentSpace</strong>
          <span>${escapeHtml(space().name)}</span>
        </div>
      </div>
      <div class="space-card">
        <div class="split">
          <div>
            <strong>${escapeHtml(tenant().name)}</strong>
            <span>${escapeHtml(space().project)} · ${space().members} 人</span>
          </div>
          ${badge(space().status)}
        </div>
      </div>
      <div class="sidebar-actions">
        ${button("开始作业", "new-session", { variant: "primary", disabled: !canCreateSession() })}
      </div>
      <nav class="nav-list" aria-label="AgentSpace 产品导航">
        ${Object.entries(groups).map(([section, items]) => `
          <div class="nav-group">
            <p>${escapeHtml(section)}</p>
            ${items.map((item) => `
              <button class="nav-item ${item.id === state.currentView ? "active" : ""}" data-action="set-view" data-value="${item.id}">
                <span>${escapeHtml(item.label)}</span>
              </button>
            `).join("")}
          </div>
        `).join("")}
      </nav>
      <div class="user-strip">
        <div class="avatar">李</div>
        <div>
          <strong>李青</strong>
          <span>${escapeHtml(state.currentRole)}</span>
        </div>
      </div>
    </aside>
  `;
}

function renderTopbar() {
  return `
    <header class="topbar">
      <div class="crumbs">AgentSpace / ${escapeHtml(tenant().name)} / ${escapeHtml(space().name)}</div>
      <div class="topbar-controls">
        <label>
          角色
          <select data-bind="currentRole">
            ${data.roles.map((role) => `<option ${role === state.currentRole ? "selected" : ""}>${escapeHtml(role)}</option>`).join("")}
          </select>
        </label>
        <label>
          租户
          <select data-bind="currentTenantId">
            ${data.tenants.map((item) => `<option value="${item.id}" ${item.id === state.currentTenantId ? "selected" : ""}>${escapeHtml(item.name)}</option>`).join("")}
          </select>
        </label>
        <label>
          空间
          <select data-bind="currentSpaceId">
            ${data.spaces.map((item) => `<option value="${item.id}" ${item.id === state.currentSpaceId ? "selected" : ""}>${escapeHtml(item.name)}</option>`).join("")}
          </select>
        </label>
        <div class="view-switch">
          ${["对话流", "总览视图", "场景视图"].map((mode) => `
            <button class="${mode === state.detailMode ? "active" : ""}" data-action="set-detail-mode" data-value="${mode}">${escapeHtml(mode)}</button>
          `).join("")}
        </div>
      </div>
    </header>
  `;
}

function renderView() {
  const views = {
    tenants: renderTenants,
    space: renderSpace,
    access: renderAccess,
    sessions: renderSessions,
    pipelines: renderPipelines,
  };
  return (views[state.currentView] || renderSessions)();
}

function renderTenants() {
  const current = selectedTenant();
  const tenantSpaces = data.spaces.filter((item) => item.tenantId === current.id);
  return `
    <section class="workbench two-col">
      <div class="content-area">
        <div class="page-title">
          <div>
            <h1>租户管理</h1>
            <p>维护租户状态、管理员授权和空间清单。</p>
          </div>
          ${button("新建租户", "create-tenant", { variant: "primary", disabled: state.currentRole !== "系统管理员" })}
        </div>
        <div class="panel">
          <div class="panel-head"><h2>租户列表</h2>${badge(data.tenants.length + " 个租户")}</div>
          <div class="list">
            ${data.tenants.map((item) => `
              <button class="list-row ${item.id === state.selectedTenantId ? "selected" : ""}" data-action="select-tenant" data-value="${item.id}">
                <div>
                  <strong>${escapeHtml(item.name)}</strong>
                  <span>${escapeHtml(item.code)} · ${escapeHtml(item.org)}</span>
                </div>
                ${badge(item.status)}
              </button>
            `).join("")}
          </div>
        </div>
        <div class="panel">
          <div class="panel-head"><h2>租户详情</h2>${badge(current.status)}</div>
          <div class="metrics">
            <div><span>管理员</span><strong>${current.admins.length}</strong></div>
            <div><span>团队空间</span><strong>${tenantSpaces.length}</strong></div>
            <div><span>审计</span><strong>${escapeHtml(current.audit)}</strong></div>
          </div>
          <div class="toolbar">
            ${button(current.status === "启用" ? "停用租户" : "启用租户", "toggle-tenant", { disabled: state.currentRole !== "系统管理员" })}
            ${button("授权管理员", "grant-admin", { disabled: state.currentRole !== "系统管理员" || current.status === "停用" })}
          </div>
        </div>
      </div>
      <aside class="context-panel">
        <h2>空间清单</h2>
        ${tenantSpaces.map((item) => `
          <div class="context-card">
            <strong>${escapeHtml(item.name)}</strong>
            <span>${escapeHtml(item.project)} · 创建者 ${escapeHtml(item.owner)}</span>
            ${badge(item.status)}
          </div>
        `).join("") || `<div class="empty">暂无空间</div>`}
      </aside>
    </section>
  `;
}

function renderSpace() {
  return `
    <section class="workbench two-col">
      <div class="content-area">
        <div class="page-title">
          <div>
            <h1>空间配置</h1>
            <p>团队空间可以直接使用默认 Agent，并按需完善资源。</p>
          </div>
          ${button("发布变更", "publish-ready", { variant: "primary", disabled: !canManageSpace() })}
        </div>
        <div class="resource-grid">
          ${data.resources.map((item) => `
            <div class="resource-card">
              <div class="split">
                <div>
                  <strong>${escapeHtml(item.group)}</strong>
                  <span>${escapeHtml(item.name)}</span>
                </div>
                ${badge(item.status)}
              </div>
              <p>${escapeHtml(item.detail)}</p>
              <div class="toolbar">
                ${button("测试", "test-resource", { small: true, value: item.id, disabled: !canManageSpace() })}
                ${button("发布", "publish-resource", { small: true, value: item.id, disabled: !canManageSpace() || item.status !== "测试通过" })}
                ${button("查看变更", "view-resource-diff", { small: true, value: item.id })}
              </div>
            </div>
          `).join("")}
        </div>
        <div class="panel">
          <div class="panel-head"><h2>团队模板</h2>${badge("可选")}</div>
          <div class="table">
            ${data.templates.map((item) => `
              <div class="table-row">
                <strong>${escapeHtml(item.name)}</strong>
                <span>${escapeHtml(item.scope)}</span>
                <span>${escapeHtml(item.modules)}</span>
                ${badge(item.status)}
              </div>
            `).join("")}
          </div>
        </div>
      </div>
      <aside class="context-panel">
        <h2>资源快照</h2>
        ${renderSnapshot()}
      </aside>
    </section>
  `;
}

function renderAccess() {
  const readonly = !canManageSpace();
  return `
    <section class="workbench two-col">
      <div class="content-area">
        <div class="page-title">
          <div>
            <h1>成员权限</h1>
            <p>按空间角色控制成员、会话、资源和流水线操作。</p>
          </div>
          ${button("添加成员", "add-member", { variant: "primary", disabled: readonly })}
        </div>
        <div class="panel">
          <div class="panel-head"><h2>成员列表</h2>${badge(state.currentRole)}</div>
          <div class="member-table">
            <div class="member-head"><span>姓名</span><span>账号</span><span>角色</span><span>状态</span><span>最近更新</span><span></span></div>
            ${data.members.map((member) => `
              <div class="member-row">
                <strong>${escapeHtml(member.name)}</strong>
                <span>${escapeHtml(member.account)}</span>
                <span>${escapeHtml(member.role)}</span>
                ${badge(member.status)}
                <span>${escapeHtml(member.updated)}</span>
                ${member.role === "创建者" ? `<span class="muted">受保护</span>` : button("移除", "remove-member", { small: true, value: member.id, disabled: readonly })}
              </div>
            `).join("")}
          </div>
        </div>
      </div>
      <aside class="context-panel">
        <h2>当前权限</h2>
        <div class="context-card"><strong>成员管理</strong>${badge(canManageSpace() ? "可用" : "无权限")}</div>
        <div class="context-card"><strong>创建会话</strong>${badge(canCreateSession() ? "可用" : "无权限")}</div>
        <div class="context-card"><strong>资源发布</strong>${badge(canManageSpace() ? "可用" : "无权限")}</div>
        <div class="context-card"><strong>流水线处理</strong>${badge(canHandleRuntime() ? "可用" : "无权限")}</div>
      </aside>
    </section>
  `;
}

function filteredSessions() {
  const keyword = state.search.trim();
  return data.sessions.filter((item) => {
    const passFilter = state.sessionFilter === "全部" || item.status === state.sessionFilter || (state.sessionFilter === "我的" && item.owner);
    const passSearch = !keyword || item.title.includes(keyword) || item.creator.includes(keyword);
    return passFilter && passSearch;
  });
}

function renderSessions() {
  const current = session();
  return `
    <section class="workbench session-layout">
      <aside class="session-list">
        <div class="panel-head no-border">
          <h2>会话历史</h2>
          ${button("+", "new-session", { small: true, disabled: !canCreateSession() })}
        </div>
        <input class="search" data-bind="search" value="${escapeHtml(state.search)}" placeholder="搜索会话" />
        <div class="segmented">
          ${["全部", "我的", "运行中", "待输入"].map((item) => `
            <button class="${item === state.sessionFilter ? "active" : ""}" data-action="set-session-filter" data-value="${item}">${escapeHtml(item)}</button>
          `).join("")}
        </div>
        <div class="list">
          ${filteredSessions().map((item) => `
            <button class="session-row ${item.id === state.selectedSessionId ? "selected" : ""}" data-action="select-session" data-value="${item.id}">
              <div>
                <strong>${escapeHtml(item.title)}</strong>
                <span>${escapeHtml(item.creator)} · ${escapeHtml(item.updated)}</span>
              </div>
              ${badge(item.status)}
            </button>
          `).join("") || `<div class="empty">没有符合条件的会话</div>`}
        </div>
      </aside>
      <div class="conversation">
        <div class="conversation-head">
          <div>
            <h1>${escapeHtml(current.title)}</h1>
            <p>${escapeHtml(current.source)} · ${escapeHtml(current.agent)} · ${escapeHtml(current.summary)}</p>
          </div>
          ${badge(current.status)}
        </div>
        <div class="messages">
          ${current.messages.map(renderMessage).join("")}
        </div>
        <div class="composer">
          <div class="segmented wide">
            <button class="active">Agent</button>
            <button>Harness CICD 流水线</button>
          </div>
          <textarea data-bind="composer" placeholder="输入任务说明">${escapeHtml(state.composer)}</textarea>
          <div class="composer-actions">
            <div class="hint">当前快照：AGENT.md、Agent、Skill、Tool、Hook、环境变量</div>
            ${button("发送", "send-message", { variant: "primary", disabled: !canCreateSession() })}
          </div>
        </div>
      </div>
      <aside class="context-panel">
        ${renderSessionContext(current)}
      </aside>
    </section>
  `;
}

function renderMessage(message) {
  if (message.side === "system") {
    return `<div class="message system"><span>${escapeHtml(message.speaker)} · ${escapeHtml(message.time)}</span><p>${escapeHtml(message.body)}</p></div>`;
  }
  return `
    <article class="message ${message.side}">
      <div class="avatar">${escapeHtml(message.speaker.slice(0, 1))}</div>
      <div class="bubble">
        <div class="message-meta"><strong>${escapeHtml(message.speaker)}</strong><span>${escapeHtml(message.time)}</span></div>
        <p>${escapeHtml(message.body)}</p>
        ${message.steps ? `<div class="step-card">${message.steps.map((step) => `<span>${escapeHtml(step)}</span>`).join("")}</div>` : ""}
        ${message.artifacts ? `<div class="artifact-list">${message.artifacts.map((artifact) => `
          <div class="artifact">
            <div><strong>${escapeHtml(artifact.name)}</strong><span>${escapeHtml(artifact.type)} · ${escapeHtml(artifact.meta)}</span></div>
            ${button("查看", "view-artifact", { small: true, value: artifact.name })}
          </div>
        `).join("")}</div>` : ""}
      </div>
    </article>
  `;
}

function renderSessionContext(current) {
  const currentPipeline = pipeline();
  return `
    <h2>任务上下文</h2>
    <div class="context-section">
      <h3>资源快照</h3>
      ${renderSnapshot()}
    </div>
    <div class="context-section">
      <h3>待处理</h3>
      ${data.documentProposals.map((item) => `
        <button class="todo ${item.id === state.selectedProposalId ? "selected" : ""}" data-action="select-proposal" data-value="${item.id}">
          <span>${escapeHtml(item.title)}</span>${badge(item.status)}
        </button>
      `).join("")}
    </div>
    <div class="context-section">
      <h3>关联流水线</h3>
      <div class="context-card">
        <strong>${escapeHtml(currentPipeline.name)}</strong>
        <span>${escapeHtml(currentPipeline.creator)} · ${escapeHtml(currentPipeline.updated)}</span>
        ${badge(currentPipeline.status)}
      </div>
    </div>
  `;
}

function renderSnapshot() {
  return `
    <div class="snapshot">
      <div><span>AGENT.md</span><strong>空间级指引</strong></div>
      <div><span>Agent</span><strong>产品 Agent、架构 Agent</strong></div>
      <div><span>Skill</span><strong>OpenAPI 文档生成</strong></div>
      <div><span>Tool</span><strong>知识检索 Tool</strong></div>
      <div><span>Hook</span><strong>高风险 Tool 审批</strong></div>
    </div>
  `;
}

function renderPipelines() {
  const current = pipeline();
  const reviewTask = current.nodes.find((task) => task.reviewStatus === "待审阅");
  return `
    <section class="workbench two-col">
      <div class="content-area">
        <div class="page-title">
          <div>
            <h1>流水线任务</h1>
            <p>按结构化流程执行多 Agent 标准作业。</p>
          </div>
          ${button("运行流水线", "start-pipeline", { variant: "primary", disabled: !canCreateSession() })}
        </div>
        <div class="panel">
          <div class="panel-head"><h2>任务清单</h2>${badge(data.pipelineRuns.length + " 个任务")}</div>
          <div class="list">
            ${data.pipelineRuns.map((item) => `
              <button class="list-row ${item.id === state.selectedPipelineId ? "selected" : ""}" data-action="select-pipeline" data-value="${item.id}">
                <div><strong>${escapeHtml(item.name)}</strong><span>${escapeHtml(item.creator)} · ${escapeHtml(item.updated)}</span></div>
                ${badge(item.status)}
              </button>
            `).join("")}
          </div>
        </div>
        <div class="panel">
          <div class="panel-head"><h2>Run 图</h2>${badge(current.status)}</div>
          <div class="run-graph">
            ${current.nodes.map((node, index) => `
              <div class="run-node ${node.status === "运行中" ? "active" : ""}">
                <strong>${escapeHtml(node.name)}</strong>
                ${badge(node.status)}
                <span>${escapeHtml(node.detail)}</span>
              </div>
              ${index < current.nodes.length - 1 ? `<div class="edge"></div>` : ""}
            `).join("")}
          </div>
        </div>
      </div>
      <aside class="context-panel">
        <h2>运行控制</h2>
        <div class="context-card">
          <strong>Agent 任务审阅</strong>
          <span>${reviewTask ? `${escapeHtml(reviewTask.name)}的输出等待审阅。` : "当前没有待审阅的 Agent 任务。"}</span>
          ${badge(reviewTask ? "待审阅" : "无需处理")}
        </div>
        <div class="toolbar vertical">
          ${button("通过审阅", "approve-pipeline", { variant: "primary", disabled: !canHandleRuntime() || !reviewTask })}
          ${button("退回修改", "return-pipeline-review", { disabled: !canHandleRuntime() || !reviewTask })}
          ${button("重试 Agent 任务", "retry-pipeline", { disabled: !canHandleRuntime() })}
        </div>
        <div class="context-section">
          <h3>Agent 任务会话</h3>
          <button class="todo" data-action="set-view" data-value="sessions"><span>订单模块接口设计</span>${badge("运行中")}</button>
        </div>
      </aside>
    </section>
  `;
}

function renderDocumentReview() {
  const item = proposal();
  const canWrite = canCreateSession() && item.status === "待接受";
  return `
    <div class="panel document-panel">
      <div class="panel-head">
        <h2>${escapeHtml(item.title)}</h2>
        ${badge(item.status)}
      </div>
      <p class="muted">${escapeHtml(item.path)}</p>
      <div class="segmented wide">
        ${["内容", "差异"].map((tab) => `<button class="${tab === state.proposalTab ? "active" : ""}" data-action="set-proposal-tab" data-value="${tab}">${tab}</button>`).join("")}
      </div>
      ${state.proposalTab === "内容" ? `<div class="doc-box">${escapeHtml(item.content)}</div>` : `<pre class="diff-box">${item.diff.map(escapeHtml).join("\n")}</pre>`}
      <div class="toolbar">
        ${button("接受变更", "accept-proposal", { variant: "primary", disabled: !canWrite })}
        ${button("拒绝变更", "reject-proposal", { disabled: !canWrite })}
      </div>
    </div>
  `;
}

function setToast(message) {
  state.toast = message;
  window.clearTimeout(setToast.timer);
  setToast.timer = window.setTimeout(() => {
    state.toast = "";
    render();
  }, 2200);
}

function handleAction(action, value) {
  switch (action) {
    case "set-view":
      state.currentView = value;
      break;
    case "set-detail-mode":
      state.detailMode = value;
      setToast(`已切换到${value}`);
      break;
    case "new-session": {
      const id = `session-${Date.now()}`;
      data.sessions.unshift({
        id,
        title: "新建 Agent 会话",
        status: "待输入",
        source: "手工会话",
        creator: "李青",
        owner: true,
        updated: "刚刚",
        agent: "平台默认 Agent",
        summary: "等待输入首条指令。",
        messages: [{ id: "m1", side: "system", speaker: "系统", time: "刚刚", body: "会话已创建。" }],
      });
      state.selectedSessionId = id;
      state.currentView = "sessions";
      setToast("会话已创建");
      break;
    }
    case "select-session":
      state.selectedSessionId = value;
      break;
    case "set-session-filter":
      state.sessionFilter = value;
      break;
    case "send-message": {
      const current = session();
      const text = state.composer.trim() || "请继续推进当前任务。";
      current.messages.push({ id: `u-${Date.now()}`, side: "user", speaker: "李青", time: "刚刚", body: text });
      current.messages.push({ id: `a-${Date.now()}`, side: "agent", speaker: "协作 Agent", time: "刚刚", body: "已接收输入，将基于当前资源快照继续执行，并在产物完成后提交审阅。" });
      current.status = "运行中";
      current.updated = "刚刚";
      state.composer = "";
      setToast("消息已发送");
      break;
    }
    case "select-proposal":
      state.selectedProposalId = value;
      state.currentView = "sessions";
      setToast("已打开文档变更");
      break;
    case "set-proposal-tab":
      state.proposalTab = value;
      break;
    case "accept-proposal":
      proposal().status = "已接受";
      setToast("变更已接受");
      break;
    case "reject-proposal":
      proposal().status = "已拒绝";
      setToast("变更已拒绝");
      break;
    case "select-pipeline":
      state.selectedPipelineId = value;
      break;
    case "approve-pipeline": {
      const currentPipeline = pipeline();
      const reviewIndex = currentPipeline.nodes.findIndex((node) => node.reviewStatus === "待审阅");
      currentPipeline.status = "运行中";
      currentPipeline.nodes = currentPipeline.nodes.map((node, index) => {
        if (index === reviewIndex) return { ...node, status: "完成", reviewStatus: "已通过", detail: `${node.detail}，审阅已通过` };
        if (index === reviewIndex + 1 && node.status === "等待执行") return { ...node, status: "运行中" };
        return node;
      });
      setToast("Agent 任务审阅已通过，流水线继续运行");
      break;
    }
    case "return-pipeline-review":
      pipeline().status = "运行中";
      pipeline().nodes = pipeline().nodes.map((node) => node.reviewStatus === "待审阅"
        ? { ...node, status: "运行中", reviewStatus: "修改中", detail: "根据审阅反馈重新执行" }
        : node);
      setToast("Agent 任务已退回修改");
      break;
    case "retry-pipeline":
      pipeline().status = "运行中";
      setToast("Agent 任务已进入重试");
      break;
    case "select-tenant":
      state.selectedTenantId = value;
      break;
    case "toggle-tenant": {
      const item = selectedTenant();
      item.status = item.status === "启用" ? "停用" : "启用";
      setToast(item.status === "启用" ? "租户已启用" : "租户已停用");
      break;
    }
    case "grant-admin":
      if (!selectedTenant().admins.includes("林岚")) selectedTenant().admins.push("林岚");
      setToast("管理员已授权");
      break;
    case "create-tenant":
      setToast("租户创建入口已打开");
      break;
    case "add-member":
      if (!data.members.some((member) => member.id === "u5")) {
        data.members.push({ id: "u5", name: "林岚", account: "linlan", role: "团队成员", status: "有效", joined: "刚刚", updated: "刚刚" });
      }
      setToast("成员已添加");
      break;
    case "remove-member":
      data.members = data.members.filter((member) => member.id !== value);
      setToast("成员已移除");
      break;
    case "test-resource": {
      const item = data.resources.find((resource) => resource.id === value);
      if (item) item.status = "测试通过";
      setToast("资源测试通过");
      break;
    }
    case "publish-resource": {
      const item = data.resources.find((resource) => resource.id === value);
      if (item) item.status = "已发布";
      setToast("资源已发布");
      break;
    }
    case "publish-ready":
      setToast("可发布资源已更新");
      break;
    case "view-resource-diff":
      setToast("变更已打开");
      break;
    case "start-pipeline":
      state.currentView = "sessions";
      setToast("已打开开始作业入口，并预选当前流水线");
      break;
    case "view-artifact":
      setToast(`${value} 已打开`);
      break;
    default:
      break;
  }
  render();
}

app.addEventListener("click", (event) => {
  const target = event.target.closest("[data-action]");
  if (!target || target.disabled) return;
  handleAction(target.dataset.action, target.dataset.value || "");
});

app.addEventListener("input", (event) => {
  const bind = event.target.dataset.bind;
  if (!bind) return;
  state[bind] = event.target.value;
});

app.addEventListener("change", (event) => {
  const bind = event.target.dataset.bind;
  if (!bind) return;
  state[bind] = event.target.value;
  if (bind === "currentTenantId") {
    const nextSpace = data.spaces.find((item) => item.tenantId === state.currentTenantId);
    if (nextSpace) state.currentSpaceId = nextSpace.id;
    state.selectedTenantId = state.currentTenantId;
  }
  render();
});

function enhanceSessionsView() {
  if (state.currentView !== "sessions") return "";
  return renderDocumentReview();
}

const originalRenderSessions = renderSessions;
renderSessions = function renderSessionsWithReview() {
  const html = originalRenderSessions();
  return html.replace("</aside>\n    </section>", `${enhanceSessionsView()}</aside>\n    </section>`);
};

render();
