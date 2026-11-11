# PLAN-252：统一 App 图标

对应 Spec：[SPEC-252](../features/SPEC-252.md)

## 方案

1. `tools/regen_launcher_icons.py` 从 `Documents\browsesth-logo-512-square.png` 生成 foreground / mono / mipmap 各密度 / about / splash。
2. launcher0–6/w 已指向同一 `@drawable/ic_launcher_foreground`，无需改 XML。
3. 规则：无要求不 release；release 与 debug 互斥。
4. `assembleAlohaDebug`（含新图标）。

## 验证

- 资源文件时间戳已更新；debug 包装机看桌面图标。
