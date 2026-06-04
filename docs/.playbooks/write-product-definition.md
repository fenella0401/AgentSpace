# 产品定义剧本

## 适用场景

当产品经理提出新需求，或要求刷新产品定义时，使用本剧本。

## 输入

- 新需求说明
- 现有 `docs/global/product-definition.md`
- 现有 `docs/global/function-index.md`

## 依赖模板

- `docs/guides/templates/product-definition.md`
- `docs/guides/templates/function-index.md`

## 执行步骤

1. 读取新需求，确认需求背景、目标用户、业务目标和范围边界。
2. 对照现有产品定义，判断需要新增、修改或删除的内容。
3. 刷新 `docs/global/product-definition.md`，保持面向产品经理视角。
4. 刷新 `docs/global/function-index.md`，补齐新增功能点或调整历史功能点状态。
5. 给出会签评审所需的变更摘要、影响范围和待确认问题。

## 需要询问用户的情况

- 新需求的目标用户、业务目标或范围边界无法判断。
- 功能树应新增功能还是修改历史功能存在明显歧义。
- 产品定义变更会影响已上线能力，但无法判断是否允许。

## 完成标准

- 产品定义文档已反映本次新需求。
- 功能树与产品定义一致。
- 输出了会签评审所需的变更摘要和待确认问题。
