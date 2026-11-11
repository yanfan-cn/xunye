# PLAN-257

1. `BrowserHomeTabScreen`：`AnimatedVisibility`/`AnimatedContent` 包 `TabletEmbeddedReaderHost`（slide+轻 fade，避 AwContents 叠层风险则 slide-only）。
2. `MainScreen`：离开浏览器时 haze 策略收紧；「我的」页可考虑降低玻璃采样或延后启用。
3. `TabletToolboxFloatingCard`：exit/enter 改为与侧栏相同 `slideIn/OutHorizontally { it }`。
4. `BrowserSessionHolder`/`BrowserSessionScreen`：首屏 attach 后尽早 load；可选预创建；减少主线程阻塞。
5. `BrowserTabThumbnailStore`：JPEG/WEBP 落盘 + 内存；Tabs 打开时对活跃 tab 补 `captureActiveThumbnail`。
6. `ReadBookMenuBarShared`/`readerMenuSurfaceBrush`：液态玻璃时顶栏透明/无 tint 渐变。
7. `assembleAlohaDebug` + LOG。
