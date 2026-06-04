# 版本发布验证剧本

## 适用场景

当版本内功能设计、验收标准、测试资料和发布资料准备完成后，使用本剧本验证版本发布完备性。

## 输入

- 目标版本编号
- `docs/release/<version>/release-plan.md`
- `docs/release/<version>/test-report.md`
- `docs/release/<version>/deployment-checklist.md`
- `docs/release/<version>/release-notes.md`
- 本版本关联的功能设计文档和测试用例

## 依赖模板

- `docs/guides/templates/release-plan.md`
- `docs/guides/templates/test-report.md`
- `docs/guides/templates/deployment-checklist.md`
- `docs/guides/templates/release-notes.md`

## 执行步骤

1. 确认目标版本编号和版本范围。
2. 检查发布计划是否列出本版本功能、负责人、时间和风险。
3. 检查本版本关联功能是否都有功能设计和验收标准。
4. 检查测试报告是否覆盖本版本范围，并记录通过、失败、阻塞和遗留问题。
5. 检查部署检查清单是否覆盖上线前、上线中和上线后动作。
6. 检查发布公告是否面向用户说明本次变化、限制和注意事项。
7. 输出版本发布完备性结论：通过、需补充后通过、或不通过。

## 需要询问用户的情况

- 无法确定目标版本范围。
- 版本资料缺失且无法从现有文档推断。
- 存在未关闭风险，但无法判断是否允许带风险上线。

## 完成标准

- 发布计划、测试报告、部署检查清单和发布公告均已检查。
- 已输出明确的发布完备性结论。
- 如未通过，已列出必须补充或修正的事项。
