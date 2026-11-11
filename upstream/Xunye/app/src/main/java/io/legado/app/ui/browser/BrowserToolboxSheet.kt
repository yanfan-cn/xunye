package io.legado.app.ui.browser

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.browser.engine.BrowserChromePrefs
import io.legado.app.browser.engine.BrowserToolboxPrefs
import io.legado.app.browser.engine.BrowserUa
import io.legado.app.ui.config.themeConfig.ThemeModeSelector
import io.legado.app.ui.theme.ChromeSurfaceStyle
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.MaterialSymbol
import io.legado.app.ui.widget.components.icon.SymbolIcon
import io.legado.app.ui.widget.components.icon.Symbols
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import kotlinx.coroutines.delay

/**
 * SPEC-070: Edge-style browser toolbox — pinned accent row + outline grid + reorder.
 * SPEC-097: no "工具箱" title; each cell rounded fill / liquid glass.
 * SPEC-099/102: panel glass; cells; no divider.
 * SPEC-107: readable overlay; 20dp corners; no drag handle; web bg / eye / text zoom.
 */
data class BrowserToolboxActions(
    val onOpenBookmarks: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onOpenDownloads: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onNewIncognitoTab: () -> Unit,
    val onAddBookmark: () -> Unit,
    /** SPEC-259: current page already in bookmarks → show 移出收藏. */
    val pageBookmarked: Boolean = false,
    val onSavePage: () -> Unit,
    val onShare: () -> Unit,
    val onCopyLink: () -> Unit,
    val onPickBlock: () -> Unit,
    val adBlockEnabled: Boolean,
    val onToggleAdBlock: () -> Unit,
    val uaMode: BrowserUa.Mode,
    val onToggleUa: () -> Unit,
    val webDarkMode: String,
    val onWebDarkModeChange: (String) -> Unit,
    val eyeProtectionEnabled: Boolean,
    val onToggleEyeProtection: () -> Unit,
    val onOpenEyeProtectionSettings: () -> Unit,
    val textZoomPercent: Int,
    val onOpenTextZoom: () -> Unit,
    /** SPEC-119: open find-in-page bar on the current webpage. */
    val onFindInPage: () -> Unit = {},
    /** SPEC-136: video capsule available for current page. */
    val videoCapsuleAvailable: Boolean = false,
    val onOpenVideoCapsule: () -> Unit = {},
    /** SPEC-242: hide browser chrome; restore FAB uses CloseFullscreen. */
    val onEnterFullscreen: () -> Unit = {},
    /** SPEC-251: in-page translate toggle / open translation settings. */
    val onTranslatePage: () -> Unit = {},
    val onOpenTranslationSettings: () -> Unit = {},
    /** SPEC-262: page currently translated → toolbox label「恢复原文」. */
    val pageTranslated: Boolean = false,
)

private data class ToolboxGridDef(
    val id: String,
    val label: String,
    val icon: MaterialSymbol,
)

/**
 * SPEC-111: tablet side panel historically forced solid fills (SIGSEGV with glass+fade).
 * SPEC-214: liquid glass may use glass cells; callers pass solidSurfaces=false when liquid.
 */
private val LocalToolboxSolidSurfaces = staticCompositionLocalOf { false }

/** SPEC-214: nested web-bg card only when not liquid glass (filled path unchanged). */
internal fun toolboxWebBgUsesNestedSurface(style: ChromeSurfaceStyle): Boolean =
    style != ChromeSurfaceStyle.LiquidGlass

/** SPEC-107: 20dp corners. */
private val ToolboxCellShape = RoundedCornerShape(20.dp)
private val ToolboxPanelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val ToolboxCellGap = 12.dp
private val ToolboxCellHeight = 62.dp
private val ToolboxCellIcon = 24.dp
private val ToolboxCellLabelSp = 11.sp
// SPEC-201: 操作者要求缩小工具箱内部上边框（12dp → 4dp；底 sheet 侧同步一致化）。
private val ToolboxContentTopInset = 4.dp
private val ToolboxWebBgShape = RoundedCornerShape(20.dp)

/** SPEC-233: Material motion 强调曲线（M3 EmphasizedDecelerate / EmphasizedAccelerate）。 */
internal val ToolboxEnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
internal val ToolboxExitEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

@Composable
fun Modifier.toolboxPanelSurface(
    shape: Shape = ToolboxPanelShape,
): Modifier =
    browserControlLiquidGlass(
        shape = shape,
        interactive = false,
        showShadow = false,
        enableLens = false,
        // SPEC-107 1A: match NTP home-menu readable overlay alphas.
        readableOverlay = true,
    )

@Composable
private fun Modifier.toolboxCellSurface(): Modifier {
    // SPEC-111: tablet side panel must not use liquid glass (RenderThread SIGSEGV).
    if (LocalToolboxSolidSurfaces.current) {
        return clip(ToolboxCellShape).background(
            LegadoTheme.colorScheme.surfaceContainerHighest,
            ToolboxCellShape,
        )
    }
    val style = currentChromeSurfaceStyle()
    // SPEC-109: denser keys for readability (was 0.68 / 0.72).
    val overlay = when (style) {
        ChromeSurfaceStyle.Filled -> null
        else -> if (LegadoTheme.isDark) 0.92f else 0.90f
    }
    return browserControlLiquidGlass(
        shape = ToolboxCellShape,
        interactive = false,
        showShadow = false,
        enableLens = false,
        overlayAlpha = overlay,
    )
}

@Composable
private fun Modifier.toolboxWebBgSurface(): Modifier {
    if (LocalToolboxSolidSurfaces.current) {
        return clip(ToolboxWebBgShape).background(
            LegadoTheme.colorScheme.surfaceContainerHighest,
            ToolboxWebBgShape,
        )
    }
    return browserControlLiquidGlass(
        shape = ToolboxWebBgShape,
        interactive = false,
        showShadow = false,
        enableLens = false,
        readableOverlay = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserToolboxSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    actions: BrowserToolboxActions,
) {
    val context = LocalContext.current
    val prefs = remember { BrowserToolboxPrefs(context) }
    var order by remember { mutableStateOf(prefs.orderedIds()) }
    var editing by remember { mutableStateOf(false) }
    val scheme = LegadoTheme.colorScheme
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // SPEC-170: phone landscape matches tablet side floating card.
    if (TabletBrowserLayout.phoneUsesTabletLikePanels(landscape)) {
        BrowserToolboxSideCard(
            show = show,
            onDismiss = {
                if (editing) {
                    prefs.saveOrder(order)
                    editing = false
                }
                onDismiss()
            },
            order = order,
            onOrderChange = { order = it },
            editing = editing,
            onEditingChange = { editing = it },
            onFinishEditing = {
                prefs.saveOrder(order)
                editing = false
            },
            actions = actions,
        )
        return
    }

    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val sheetHeight = screenH * 0.55f

    AppModalBottomSheet(
        show = show,
        onDismissRequest = {
            if (editing) {
                prefs.saveOrder(order)
                editing = false
            }
            onDismiss()
        },
        title = if (editing) "调整顺序" else null,
        endAction = if (editing) {
            {
                TextButton(
                    onClick = {
                        prefs.saveOrder(order)
                        editing = false
                    },
                ) {
                    Text("完成", color = scheme.primary)
                }
            }
        } else {
            null
        },
        contentPaddingEnabled = false,
        animateContentSize = false,
        containerColor = Color.Transparent,
        showDragHandle = false,
        contentWindowInsets = {
            WindowInsets.statusBars.union(WindowInsets.navigationBars)
        },
        modifier = Modifier
            .height(sheetHeight)
            .toolboxPanelSurface()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            BrowserToolboxBody(
                order = order,
                onOrderChange = { order = it },
                editing = editing,
                onEditingChange = { editing = it },
                actions = actions,
                onDismiss = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

/** SPEC-170: landscape toolbox — tablet-like floating card (right). */
@Composable
private fun BrowserToolboxSideCard(
    show: Boolean,
    onDismiss: () -> Unit,
    order: List<String>,
    onOrderChange: (List<String>) -> Unit,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onFinishEditing: () -> Unit,
    actions: BrowserToolboxActions,
) {
    // SPEC-233: 侧悬浮卡关闭时先播退出动画再移除 Dialog。原实现 `if (!show) return`
    // 会在 show=false 的同帧整窗移除（无退出动画，表现为硬切 + 掉帧）。
    val enterMs = 300
    val exitMs = 250
    var composed by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (show) {
            composed = true
            exiting = false
            contentVisible = true
        } else if (composed) {
            exiting = true
            contentVisible = false
        }
    }
    LaunchedEffect(exiting) {
        if (exiting) {
            delay(exitMs.toLong() + 50)
            composed = false
        }
    }
    if (!composed) return

    val scheme = LegadoTheme.colorScheme
    val panelShape = RoundedCornerShape(24.dp)
    // SPEC-182 R2: operator set width to half the screen.
    val widthFraction = TabletBrowserLayout.PhoneLandscapeToolboxWidthFraction
    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.TopEnd,
        ) {
            AnimatedVisibility(
                visible = contentVisible,
                // SPEC-233: Material motion 强调曲线（EmphasizedDecelerate/EmphasizedAccelerate）。
                enter = fadeIn(animationSpec = tween(enterMs, easing = ToolboxEnterEasing)) +
                    slideInHorizontally(animationSpec = tween(enterMs, easing = ToolboxEnterEasing)) { it / 4 },
                exit = fadeOut(animationSpec = tween(exitMs, easing = ToolboxExitEasing)) +
                    slideOutHorizontally(animationSpec = tween(exitMs, easing = ToolboxExitEasing)) { it / 4 },
            ) {
                Column(
                    Modifier
                        // SPEC-182 R2: card must sit below the system status bar (the Dialog
                        // is decorFitsSystemWindows=false) and above the nav bar; fill the
                        // remaining height so the menu always shows completely.
                        .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
                        .padding(top = 8.dp, end = 16.dp, bottom = 16.dp)
                        .fillMaxWidth(widthFraction)
                        .fillMaxHeight()
                        .clip(panelShape)
                        .background(scheme.surface, panelShape)
                        .clickable(enabled = false) {}
                        // SPEC-182 R2: inner padding 12dp (was 16dp).
                        .padding(12.dp),
                ) {
                    if (editing) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "调整顺序",
                                Modifier.weight(1f),
                                fontSize = 16.sp,
                                color = scheme.onSurface,
                            )
                            TextButton(onClick = onFinishEditing) { Text("完成") }
                        }
                    }
                    BrowserToolboxBody(
                        order = order,
                        onOrderChange = onOrderChange,
                        editing = editing,
                        onEditingChange = onEditingChange,
                        solidSurfaces = true,
                        actions = actions,
                        onDismiss = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun BrowserToolboxBody(
    order: List<String>,
    onOrderChange: (List<String>) -> Unit,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    actions: BrowserToolboxActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * SPEC-111/214: true → solid cell fills (filled chrome / legacy crash avoidance).
     * Liquid glass callers set false so panel/cells use glass.
     */
    solidSurfaces: Boolean = false,
) {
    val scheme = LegadoTheme.colorScheme
    val chromeStyle = currentChromeSurfaceStyle()
    CompositionLocalProvider(LocalToolboxSolidSurfaces provides solidSurfaces) {
    Column(modifier = modifier) {
        if (!editing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ToolboxContentTopInset)
                    // SPEC-214: liquid glass — no nested glass card under 网页背景.
                    .then(
                        if (toolboxWebBgUsesNestedSurface(chromeStyle)) {
                            Modifier.toolboxWebBgSurface().clip(ToolboxWebBgShape)
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    "网页背景",
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                // SPEC-119: icons only (no 跟随系统/浅色/深色 text).
                ThemeModeSelector(
                    selectedMode = webDarkToSelector(actions.webDarkMode),
                    onModeSelected = { value ->
                        actions.onWebDarkModeChange(selectorToWebDark(value))
                    },
                    iconsOnly = true,
                )
            }
            Spacer(Modifier.height(ToolboxCellGap))
        } else {
            Spacer(Modifier.height(ToolboxContentTopInset))
        }

        // SPEC-201: 书签/历史/下载/设置改为随下方网格滚动（固定 Row 已删）。

        val defs = remember(
            order,
            actions.adBlockEnabled,
            actions.uaMode,
            actions.eyeProtectionEnabled,
            actions.textZoomPercent,
            actions.videoCapsuleAvailable,
        ) {
            order.mapNotNull { id -> gridDef(id, actions) }
        }

        if (editing) {
            val gridState = rememberLazyGridState()
            val reorderable = rememberReorderableLazyGridState(gridState) { from, to ->
                onOrderChange(
                    order.toMutableList().apply {
                        add(to.index, removeAt(from.index))
                    },
                )
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(ToolboxCellGap),
                verticalArrangement = Arrangement.spacedBy(ToolboxCellGap),
            ) {
                // SPEC-201: 书签/历史/下载/设置随网格滚动（编辑态顺序固定、不可拖拽）。
                ToolboxShortcutCells(
                    primaryColor = scheme.primary,
                    actions = actions,
                    onDismiss = onDismiss,
                    editing = true,
                )
                itemsIndexed(defs, key = { _, d -> d.id }) { _, def ->
                    ReorderableItem(state = reorderable, key = def.id) {
                        ToolboxCell(
                            icon = def.icon,
                            label = def.label,
                            tint = scheme.onSurface,
                            iconFilled = false,
                            editing = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .longPressDraggableHandle(),
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(ToolboxCellGap),
                verticalArrangement = Arrangement.spacedBy(ToolboxCellGap),
            ) {
                // SPEC-201: 书签/历史/下载/设置随网格滚动。
                ToolboxShortcutCells(
                    primaryColor = scheme.primary,
                    actions = actions,
                    onDismiss = onDismiss,
                    editing = false,
                )
                items(defs, key = { it.id }) { def ->
                    ToolboxCell(
                        icon = def.icon,
                        label = def.label,
                        tint = when (def.id) {
                            "eye_protection" ->
                                if (actions.eyeProtectionEnabled) scheme.primary else scheme.onSurface
                            // SPEC-136: accent when page has sniffed video.
                            "video_capsule" ->
                                if (actions.videoCapsuleAvailable) scheme.primary
                                else scheme.onSurface.copy(alpha = 0.38f)
                            else -> scheme.onSurface
                        },
                        iconFilled = when (def.id) {
                            "eye_protection" -> actions.eyeProtectionEnabled
                            "video_capsule" -> actions.videoCapsuleAvailable
                            "add_bookmark" -> actions.pageBookmarked
                            else -> false
                        },
                        onClick = {
                            when (def.id) {
                                "new_incognito" -> {
                                    onDismiss()
                                    actions.onNewIncognitoTab()
                                }
                                "add_bookmark" -> {
                                    onDismiss()
                                    actions.onAddBookmark()
                                }
                                "save_page" -> {
                                    onDismiss()
                                    actions.onSavePage()
                                }
                                "share" -> {
                                    onDismiss()
                                    actions.onShare()
                                }
                                "copy_link" -> {
                                    onDismiss()
                                    actions.onCopyLink()
                                }
                                "pick_block" -> {
                                    onDismiss()
                                    actions.onPickBlock()
                                }
                                "ad_block" -> actions.onToggleAdBlock()
                                "ua_toggle" -> actions.onToggleUa()
                                "eye_protection" -> actions.onToggleEyeProtection()
                                "text_zoom" -> {
                                    onDismiss()
                                    actions.onOpenTextZoom()
                                }
                                "find_in_page" -> {
                                    onDismiss()
                                    actions.onFindInPage()
                                }
                                "translate" -> {
                                    onDismiss()
                                    actions.onTranslatePage()
                                }
                                "video_capsule" -> {
                                    onDismiss()
                                    actions.onOpenVideoCapsule()
                                }
                                "fullscreen" -> {
                                    onDismiss()
                                    actions.onEnterFullscreen()
                                }
                            }
                        },
                        onLongClick = when (def.id) {
                            "eye_protection" -> {
                                {
                                    onDismiss()
                                    actions.onOpenEyeProtectionSettings()
                                }
                            }
                            "translate" -> {
                                {
                                    onDismiss()
                                    actions.onOpenTranslationSettings()
                                }
                            }
                            else -> null
                        },
                    )
                }
                item(key = "customize") {
                    ToolboxCell(
                        icon = Symbols.Tune,
                        label = "自定义顺序",
                        tint = scheme.onSurface,
                        iconFilled = false,
                        onClick = { onEditingChange(true) },
                    )
                }
            }
        }
    }
    } // CompositionLocalProvider SPEC-111
}

private fun gridDef(id: String, actions: BrowserToolboxActions): ToolboxGridDef? = when (id) {
    "new_incognito" -> ToolboxGridDef(id, "新建无痕", Symbols.VisibilityOff)
    "add_bookmark" -> ToolboxGridDef(
        id,
        if (actions.pageBookmarked) "移出收藏" else "加入收藏",
        if (actions.pageBookmarked) Symbols.BookmarkRemove else Symbols.BookmarkAdd,
    )
    "save_page" -> ToolboxGridDef(id, "保存网页", Symbols.DownloadForOffline)
    "share" -> ToolboxGridDef(id, "转发网页", Symbols.Share)
    "copy_link" -> ToolboxGridDef(id, "复制链接", Symbols.ContentCopy)
    "pick_block" -> ToolboxGridDef(id, "点选屏蔽", Symbols.Block)
    "video_capsule" -> ToolboxGridDef(
        id,
        "净视胶囊",
        MaterialSymbol("videocam", autoMirror = false),
    )
    "ad_block" -> ToolboxGridDef(
        id,
        if (actions.adBlockEnabled) "广告拦截·开" else "广告拦截·关",
        Symbols.Ad,
    )
    "ua_toggle" -> ToolboxGridDef(
        id,
        "UA·${actions.uaMode.label}",
        if (actions.uaMode == BrowserUa.Mode.Desktop) Symbols.Computer else Symbols.PhoneAndroid,
    )
    "eye_protection" -> ToolboxGridDef(
        id,
        if (actions.eyeProtectionEnabled) "护眼·开" else "护眼·关",
        Symbols.Visibility,
    )
    "text_zoom" -> ToolboxGridDef(
        id,
        "字体·${actions.textZoomPercent}%",
        Symbols.FormatSize,
    )
    "find_in_page" -> ToolboxGridDef(id, "页内查找", Symbols.Search)
    "translate" -> ToolboxGridDef(
        id,
        if (actions.pageTranslated) "恢复原文" else "翻译",
        Symbols.Translate,
    )
    "fullscreen" -> ToolboxGridDef(id, "全屏", Symbols.OpenInFull)
    else -> null
}

/**
 * SPEC-201: 书签/历史/下载/设置四个入口——原为网格上方的固定 Row，
 * 改为随 4 列网格滚动的固定顺序项（编辑态不参与拖拽排序）。
 */
private fun LazyGridScope.ToolboxShortcutCells(
    primaryColor: Color,
    actions: BrowserToolboxActions,
    onDismiss: () -> Unit,
    editing: Boolean,
) {
    item(key = "shortcut_bookmarks") {
        ToolboxCell(
            icon = Symbols.Bookmarks,
            label = "书签",
            tint = primaryColor,
            iconFilled = true,
            editing = editing,
            onClick = {
                onDismiss()
                actions.onOpenBookmarks()
            },
        )
    }
    item(key = "shortcut_history") {
        ToolboxCell(
            icon = Symbols.History,
            label = "历史",
            tint = primaryColor,
            iconFilled = true,
            editing = editing,
            onClick = {
                onDismiss()
                actions.onOpenHistory()
            },
        )
    }
    item(key = "shortcut_downloads") {
        ToolboxCell(
            icon = Symbols.Download,
            label = "下载",
            tint = primaryColor,
            iconFilled = true,
            editing = editing,
            onClick = {
                onDismiss()
                actions.onOpenDownloads()
            },
        )
    }
    item(key = "shortcut_settings") {
        ToolboxCell(
            icon = Symbols.Settings,
            label = "设置",
            tint = primaryColor,
            iconFilled = true,
            editing = editing,
            onClick = {
                onDismiss()
                actions.onOpenSettings()
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolboxCell(
    icon: MaterialSymbol,
    label: String,
    tint: Color,
    iconFilled: Boolean,
    editing: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(ToolboxCellHeight)
            .toolboxCellSurface()
            .clip(ToolboxCellShape)
            .then(
                when {
                    editing || onClick == null -> Modifier
                    onLongClick != null -> Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                    else -> Modifier.clickable(onClick = onClick)
                },
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        SymbolIcon(
            icon,
            contentDescription = label,
            tint = tint,
            filled = iconFilled,
            size = ToolboxCellIcon,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = LegadoTheme.colorScheme.onSurface,
            fontSize = ToolboxCellLabelSp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
        )
    }
}

/** SPEC-107/120: Chrome/Edge-style webpage text zoom sheet + force zoom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserTextZoomSheet(
    show: Boolean,
    percent: Int,
    onDismiss: () -> Unit,
    onPercentChange: (Int) -> Unit,
    forcePageZoom: Boolean = true,
    onForcePageZoomChange: (Boolean) -> Unit = {},
) {
    val scheme = LegadoTheme.colorScheme
    var local by remember(show, percent) { mutableIntStateOf(percent) }
    var forceZoom by remember(show, forcePageZoom) { mutableStateOf(forcePageZoom) }
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
        title = "网页字体大小",
        endAction = {
            TextButton(
                onClick = {
                    onPercentChange(local)
                    onForcePageZoomChange(forceZoom)
                    onDismiss()
                },
            ) {
                Text("完成", color = scheme.primary)
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Text(
                "$local%",
                color = scheme.onSurface,
                fontSize = 22.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "仅放大或缩小网页文字，不影响应用界面（类似 Chrome / Edge 无障碍字体）。",
                color = scheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("强制页面缩放", color = scheme.onSurface, fontSize = 15.sp)
                    Text(
                        "开启后，即使网站禁止缩放也可捏合放大（类似 Chrome 强制启用缩放）",
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = forceZoom,
                    onCheckedChange = {
                        forceZoom = it
                        onForcePageZoomChange(it)
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Slider(
                value = local.toFloat(),
                onValueChange = {
                    val stepped = (
                        (it / BrowserChromePrefs.TEXT_ZOOM_STEP)
                            .toInt() * BrowserChromePrefs.TEXT_ZOOM_STEP
                        ).coerceIn(
                        BrowserChromePrefs.TEXT_ZOOM_MIN,
                        BrowserChromePrefs.TEXT_ZOOM_MAX,
                    )
                    local = stepped
                    onPercentChange(stepped)
                },
                valueRange = BrowserChromePrefs.TEXT_ZOOM_MIN.toFloat()..
                    BrowserChromePrefs.TEXT_ZOOM_MAX.toFloat(),
                steps = ((BrowserChromePrefs.TEXT_ZOOM_MAX - BrowserChromePrefs.TEXT_ZOOM_MIN) /
                    BrowserChromePrefs.TEXT_ZOOM_STEP) - 1,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = {
                    local = 100
                    onPercentChange(100)
                }) { Text("默认") }
                TextButton(onClick = {
                    val next = (local - BrowserChromePrefs.TEXT_ZOOM_STEP)
                        .coerceAtLeast(BrowserChromePrefs.TEXT_ZOOM_MIN)
                    local = next
                    onPercentChange(next)
                }) { Text("缩小") }
                TextButton(onClick = {
                    val next = (local + BrowserChromePrefs.TEXT_ZOOM_STEP)
                        .coerceAtMost(BrowserChromePrefs.TEXT_ZOOM_MAX)
                    local = next
                    onPercentChange(next)
                }) { Text("放大") }
            }
        }
    }
}
