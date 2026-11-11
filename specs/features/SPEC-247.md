# SPEC-247：本地仓库根目录更名为 D:\cursor\Xunye

| 项目 | 内容 |
| --- | --- |
| Spec ID | SPEC-247 |
| 状态 | In Progress |
| 负责人 | AO |
| 需求依据 | 2026-11-07 聊天：「本地 D:\cursor\Xunye 更名为 D:\cursor\Xunye」 |
| 对应 Plan | [`../plans/PLAN-247.md`](../plans/PLAN-247.md) |
| 关联 | SPEC-245/246 |

## 确认

- 复述：磁盘目录 `D:\cursor\Xunye` → `D:\cursor\Xunye`；同步文档/规则中的本机路径。
- 任务下达者指令即确认（本条为直接路径更名）。

## 范围

- 做：物理更名；更新活跃 docs/rules/README 中的本机根路径。
- 不做：改 GitHub 仓名（已是 xunye）；改 applicationId；强制用户重开 IDE 以外的操作。

## 验收

- [ ] AC-01：`D:\cursor\Xunye` 存在且含 `upstream\Xunye`
- [ ] AC-02：`D:\cursor\Xunye` 不存在（或仅为空残留）
- [ ] AC-03：活跃规则/README 路径已指向 `D:\cursor\Xunye`
