# PLAN-253：桌面图标内缩 + 启动页回退

1. 改 `tools/regen_launcher_icons.py`：foreground/mipmap/about/mono 使用 `pad_ratio=0.18`；splash 不再由方图覆盖。
2. 日间 `ic_launcher_background` → `#14305A`；夜间同色，避免内缩露底。
3. 运行脚本重写 launcher 相关资源。
4. `git checkout HEAD --` 恢复两档 `ic_splash_logo.png`。
5. 写 LOG；更新交接一句；**不 assemble**。

## 验证

- 量测 foreground 内容相对画布边距 ≈18%。
- `git diff`/`git status` 确认 splash 相对 HEAD 无改动；未跑 gradlew assemble。
