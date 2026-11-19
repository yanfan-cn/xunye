# PLAN-268：外链静默 + intent fallback 实施计划

## 1. 基本信息

| 项目 | 内容 |
| --- | --- |
| Plan ID | PLAN-268 |
| 对应 Spec | [`../features/SPEC-268.md`] |
| 状态 | Ready |
| 执行人 | AO |
| 工作分支 | main |
| 基线提交 | 工作区已有 SPEC-267 等未提交改动 |
| 对应 Log | [`../logs/LOG-268.md`] |
| 最后更新 | 2026-11-19 |

## 2. 现状与证据

- 当前入口：`BrowserAwContentsClient.shouldOverrideUrlLoading`（aloha/systemWv）对外链分支；`BrowserSessionScreen.openExternalAppChooser` 空 resolve 时 `showSnack`。
- 可复用：`BrowserUrlPolicy.isExternalAppLaunch`；`onNavigationOverride` / `loadExternalUrl` 可加载 fallback。
- 当前缺口：关阻止仍 snack；不解析 `S.browser_fallback_url`；无手势不弹选择器但也不跟 fallback。

```text
外链 URL -> isExternalAppLaunch
  -> 阻止开: snack，停
  -> 阻止关+手势: chooser 或 snack「没有可打开…」
  -> 阻止关+无手势: 静默停（无 fallback）
```

## 3. 方案与边界

- Agent 最终复述：见 SPEC-268 §2
- 任务下达者确认：2026-11-19「确认」
- 红色问题：无

采用方案：策略集中到 `BrowserUrlPolicy`（fallback 解析）；Client 在阻止关时一律回调宿主（带是否手势）；宿主：手势且可解析→chooser；否则静默跟 http(s) fallback；去掉空/失败 snack。阻止开：snack + 跟 fallback。

实施后流程：

```text
外链 -> Client 消费导航
  -> 阻止开: snack + fallback?
  -> 阻止关+手势+有应用: Chooser
  -> 否则: fallback? 静默跟 : 静默丢
```

| 模块 | 计划改动 | 保持不变 |
| --- | --- | --- |
| `BrowserUrlPolicy` | 增 `intentBrowserFallbackUrl` | http 判定语义 |
| aloha/systemWv `BrowserAwContentsClient` | 无手势也回调宿主处理 fallback；签名带 gesture | 广告/弹窗 |
| `BrowserSessionScreen` | chooser 返回 Boolean；去空 snack；跟 fallback | 其它 chrome |
| 单测 | `BrowserUrlPolicyTest` | — |

明确不做：assemble；改默认 prefs 键名；自定义 scheme 伪 fallback。

## 4. 执行步骤

| 步骤 | 目标结果 | 涉及模块 | 完成证据 |
| --- | --- | --- | --- |
| 1 | Spec/Plan/Log 就位 | specs/ | 文件存在 |
| 2 | Policy + Client + Session | engine + ui | 编译/单测 |
| 3 | 单测 AC-05 | test | PASS |
| 4 | Log 回填 | LOG-268 | 记录 |

## 5. 测试与验收计划

| ID | AC | 验证 | 通过标准 |
| --- | --- | --- | --- |
| TC-01 | AC-05 | `BrowserUrlPolicyTest` | fallback 解析与 scheme 判定 PASS |
| TC-02 | AC-01/02/03/04 | 代码审查 + 可选真机 | 无空 snack；有应用才 chooser；fallback 加载 |

## 6. 风险、回退与停止条件

| 风险 | 处理 | 回退 |
| --- | --- | --- |
| fallback URL 编码异常 | 解析失败则静默 | 还原 Session/Client |
| 手势误判导致少弹选择器 | 保持现有 hasUserGesture | — |

## 7. 完成门禁

- [x] 步骤与 AC 对应
- [x] Spec 已确认
- [ ] Log 实施中更新

## 8. 实际偏差（实施后回填）

- 与计划不同：（实施后填）
