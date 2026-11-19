# PLAN-269：NTP 搜索胶囊墨色

## 1. 基本信息

| 项目 | 内容 |
| --- | --- |
| Plan ID | PLAN-269 |
| 对应 Spec | [`../features/SPEC-269.md`] |
| 状态 | Ready |
| 执行人 | AO |
| 对应 Log | [`../logs/LOG-269.md`] |
| 最后更新 | 2026-11-19 |

## 2. 现状与证据

- `BrowserNtpScreen.kt` ~887–894：`darkPresetVisible` 时强制 `Color.White`（SPEC-265），浅色玻璃上不可读。
- logo/shortcut 浅色逻辑保留（壁纸上元素）。

## 3. 方案

抽出 `NtpCapsuleInk.useDarkInk(luminance, darkPreset)`：深色预置或 fieldBg 亮度 ≥ 0.45 → 深色墨。胶囊内改用该决策；去掉 SPEC-265 白字强制。

| 模块 | 改动 |
| --- | --- |
| `NtpCapsuleInk.kt` | 纯函数决策 |
| `BrowserNtpScreen.kt` | 应用决策 |
| `NtpCapsuleInkTest` | AC-04 |

## 4. 测试

| TC | AC | 方法 |
| --- | --- | --- |
| TC-01 | AC-04 | 单测 |
| TC-02 | AC-01/02/03 | 单测分支 + 代码审查 |

## 5. 偏差

（实施后填）
