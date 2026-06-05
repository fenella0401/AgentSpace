# 功能设计 PRD 剧本

## 适用场景

当功能树新增功能点，或历史功能点需要修改时，使用本剧本刷新功能设计文档的 PRD 部分。

## 输入

- 目标功能点及功能编号
- `docs/global/function-index.md`
- 现有功能设计文档，或 `docs/guides/templates/function-design.md`

## 依赖模板

- `docs/guides/templates/function-design.md`

## 执行步骤

1. 确认目标功能点的编号、名称、状态和关联需求。
2. 若功能设计文档不存在，按模板创建 `docs/function-design/F-xxx-name.md`。
3. 刷新功能设计文档中的“PRD 设计”部分，按“用户目标、Use Story 拆分、Use Case 编写、业务规则、边界与异常”的顺序组织。
4. 单独使用一个 agent review PRD 设计，检查合理性、一致性、自相矛盾和重复啰嗦问题。
5. 根据 review 结果修正 PRD 设计，或将无法判断的问题记录为待确认。
6. 明确本次 PRD 更新对 UI、前端、后端和验收标准的潜在影响。
7. 记录变更摘要，供后续专项设计剧本使用。

## 需要询问用户的情况

- 无法确定功能编号、功能名称或所属功能树节点。
- 功能目标、用户路径或范围边界存在冲突。
- 需求是否纳入当前版本无法判断。

## 完成标准

- 目标功能的 PRD 设计已更新。
- PRD 中包含用户目标、Use Story 拆分、Use Case 编写、业务规则和边界异常。
- PRD 设计已完成独立 agent review，且 review 问题已关闭或明确记录为待确认。
- 已标记 UI、前端、后端和验收标准是否可能受影响。
