# PLAN-270：搜索胶囊墨色按可见底色自适应

## 1. 基本信息

| 项目 | 内容 |
| --- | --- |
| Plan ID | PLAN-270 |
| 对应 Spec | [`../features/SPEC-270.md`](../features/SPEC-270.md) |
| 状态 | Ready |
| 执行人 | AO |
| 对应 Log | [`../logs/LOG-270.md`](../logs/LOG-270.md) |
| 最后更新 | 2026-11-21 |

## 2. 现状与证据

- `NtpCapsuleInk.useDarkInk(fieldBgLuminance, darkPresetWallpaper)`：`darkPresetWallpaper || 亮度 ≥ 0.45` → 赛博/霓虹下无条件深墨。
- `BrowserNtpScreen.kt:890-901`：`useDarkCapsuleInk` → `textColor` / `secondary`；`NtpDarkWordmark = #1C1B19`。
- 胶囊实际绘制：`clip(capsuleShape).browserControlLiquidGlass(...).background(fieldBg.copy(alpha = capsuleFill))`，`capsuleFill = ntpGlassOverlayAlpha()`（液态玻璃 浅 0.4／深 0.35，填充 1.0）。
- 预置壁纸平均色实测（64×64 均值）：赛博 `#1C527F`、霓虹 `#31124F`、薄雾 `#C1C9D3`、晨纸 `#F5E5D0`、窗纱 `#D8D2C0`。
- 壁纸遮罩 `wallpaperScrim`：赛博/霓虹在浅色主题 0.22、深色 0.28；浅色预置无遮罩。

## 3. 方案（唯一）

### 3.1 决策层：`NtpCapsuleInk` 重写为纯函数

输入：胶囊填色 ARGB、玻璃 alpha、背景色 ARGB → 输出 `NtpCapsuleInk.Ink(text, secondary, scrimOverlay)`。

1. 合成可见底色：`visible = bg * (1 - alpha) + fieldBg * alpha`（按 alpha 混合，逐通道）。
2. 计算 `visible` 的相对亮度 L。
3. 若 L ≥ 0.45 → 深墨 `#1C1B19`；否则浅墨 `#FFFFFF`。
4. 计算该选择下的对比度 `contrast(ink, visible)`；若 < 4.5 → 在胶囊上加一层黑色叠加 `k`（0…0.55），使合成底色朝低亮度移动直至对比达标，返回该 `k`。
5. 次要色 = 主墨同色，alpha 0.62（深墨）/ 0.88（浅墨）。

不引入壁纸 Id 判断；壁纸只用于提供背景平均色（见 3.2）。

### 3.2 背景色来源

- 新增 `NtpWallpaperPalette`（`browser/engine/`）：预置壁纸 → 常量平均色（实测值，含遮罩叠加）；相册/每日壁纸 → `#101418`（等同深色遮罩后取保守深值）。

### 3.3 渲染层：`BrowserNtpScreen`

- 用 `NtpCapsuleInk.decide(fieldBg, capsuleFill, background)` 得到 `textColor`/`secondary`/`scrimOverlay`。
- 胶囊背景改为 `fieldBg.copy(alpha = capsuleFill)` 之上再叠 `Color.Black.copy(alpha = scrimOverlay)`（`scrimOverlay` 为 0 时不画）。
- 删除对 `darkPresetVisible` 的胶囊内依赖（logo/快捷书签仍保留原壁纸判断，不动）。

### 3.4 测试

- 重写 `NtpCapsuleInkTest`：底色合成、深/浅墨选择、垫色分支、深色主题+赛博必浅墨、浅色主题+赛博必深墨、深色主题+薄雾必深墨。
- 新增 `NtpWallpaperPaletteTest`：预置壁纸色均可达标（与决策函数联调）。

## 4. 数据合同

无持久化数据变更；不新增 prefs key。

## 5. 改动范围

| 文件 | 改动 |
| --- | --- |
| `browser/engine/NtpCapsuleInk.kt` | 重写为纯函数决策 + 垫色 |
| `browser/engine/NtpWallpaperPalette.kt` | 新增：预置壁纸平均色 |
| `ui/browser/BrowserNtpScreen.kt` | 应用决策；胶囊垫色；移除深色预置强判 |
| `test/.../NtpCapsuleInkTest.kt` | 重写 |
| `test/.../NtpWallpaperPaletteTest.kt` | 新增 |

## 6. 验证步骤

1. `$env:GRADLE_USER_HOME="D:\cursor\.gradle-home"`
2. `.\gradlew.bat :app:testAlohaDebugUnitTest --tests "io.legado.app.browser.engine.Ntp*" --no-configuration-cache`
3. `.\gradlew.bat assembleAlohaDebug --no-configuration-cache`
4. 装机：深色+赛博、深色+霓虹、浅色+赛博、深色+薄雾各截图一张。

## 7. 回退

单文件纯函数改动；`git checkout` 三个源文件即可回退，无数据迁移。

## 8. 偏差

（实施后填）
