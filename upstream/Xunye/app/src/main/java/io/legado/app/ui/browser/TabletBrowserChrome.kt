package io.legado.app.ui.browser

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import io.legado.app.ui.widget.components.icon.SymbolIcon
import io.legado.app.ui.widget.components.menu.MenuBarBoundsProvider
import io.legado.app.ui.widget.components.icon.MaterialSymbol
import io.legado.app.ui.widget.components.icon.Symbols
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.browser.engine.BrowseStore
import io.legado.app.browser.engine.BrowserChromePrefs
import io.legado.app.data.entities.Book
import io.legado.app.browser.engine.BrowserNavHistory
import io.legado.app.browser.engine.BrowserNtpPrefs
import io.legado.app.browser.engine.BrowserOpenBridge
import io.legado.app.browser.engine.BrowserShieldPrefs
import io.legado.app.browser.engine.BrowserTabManager
import io.legado.app.browser.engine.BrowserToolboxPrefs
import io.legado.app.browser.engine.BrowserUa
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.ui.book.read.sheet.EyeProtectionConfigSheet
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/** SPEC-080: Downloads opens in side panel (not full-screen route). */
enum class TabletSidePanel { None, Bookmarks, History, Toolbox, Tabs, Downloads }

/** SPEC-111: match function-bar ChromeIcon slot so strip +/- count align with back/toolbox. */
private val TabletChromeIconSlot = 40.dp

@Composable
fun TabletBrowserShell(
    address: TextFieldValue,
    onAddressChange: (TextFieldValue) -> Unit,
    onAddressFocusChange: (Boolean) -> Unit = {},
    addressFocused: Boolean = false,
    /** SPEC-146 R2: clipboard link candidate while the omnibox is focused. */
    clipboardUrl: String? = null,
    onSubmitAddress: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onReader: () -> Unit,
    onOpenBookshelf: () -> Unit = {},
    onOpenMy: () -> Unit = {},
    chromeVisible: Boolean,
    /**
     * SPEC-130: when true (NTP / reader), chrome stays in layout so content is not covered.
     * When false (web session + auto-hide), chrome overlays to avoid AwContents resize jank.
     */
    pinChromeInLayout: Boolean = true,
    sidePanel: TabletSidePanel,
    onSidePanelChange: (TabletSidePanel) -> Unit,
    onOpenUrl: (String) -> Unit,
    /** SPEC-238: 侧栏收藏/书签页点阅读书签 → 打开阅读器（对齐手机端 SPEC-090）。 */
    onOpenBook: (Book) -> Unit = {},
    onOpenBrowserSettings: () -> Unit = {},
    onOpenClearBrowsingData: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    sessionActions: BrowserSessionActions? = null,
    /** SPEC-242: toolbox「全屏」— hide shell chrome. */
    onEnterChromeFullscreen: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scheme = LegadoTheme.colorScheme
    val context = LocalContext.current
    val chromePrefs = remember { BrowserChromePrefs(context) }
    val shield = remember { BrowserShieldPrefs(context) }
    val store = remember { BrowseStore(context) }
    val scope = rememberCoroutineScope()
    var uaMode by remember { mutableStateOf(chromePrefs.uaModeOrDefault(true)) }
    var adBlockEnabled by remember { mutableStateOf(shield.adBlockEnabled) }
    var webDarkMode by remember { mutableStateOf(chromePrefs.webContentDarkMode) }
    var textZoomPercent by remember { mutableIntStateOf(chromePrefs.textZoomPercent) }
    var showTextZoomSheet by remember { mutableStateOf(false) }
    var showEyeConfigSheet by remember { mutableStateOf(false) }
    var readerModeMenu by remember { mutableStateOf(false) }
    var readerAutoOpenUi by remember { mutableStateOf(false) }
    var extremePurgeUi by remember { mutableStateOf(true) }
    BackHandler(enabled = readerModeMenu) { readerModeMenu = false }
    val themeSettingsGateway = remember {
        GlobalContext.get().get<ThemeSettingsGateway>()
    }
    val themeSettings by themeSettingsGateway.settings.collectAsStateWithLifecycle(
        themeSettingsGateway.currentSettings,
    )
    val addressSuggestions = remember(address, addressFocused) {
        if (!addressFocused || address.text.isBlank()) {
            emptyList()
        } else {
            io.legado.app.browser.engine.BrowserOmniboxSuggest.match(
                address.text,
                store.bookmarks(),
                store.history(),
            )
        }
    }

    // SPEC-055/080: full-screen tabs hide tablet browser chrome.
    val showBrowserChrome = chromeVisible && sidePanel != TabletSidePanel.Tabs
    // Tab strip 40dp + function bar TopBarHeight — for toolbox offset under overlay chrome.
    val overlayChromeBodyHeight = 40.dp + BrowserUiTokens.TopBarHeight
    // SPEC-080: system back closes any side panel / tabs overlay (stay in app).
    BackHandler(enabled = sidePanel != TabletSidePanel.None) {
        onSidePanelChange(TabletSidePanel.None)
    }
    val useOverlayChrome = !pinChromeInLayout
    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(scheme.surface)) {
        // SPEC-130: status bar always opaque — web never draws under system icons.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceContainer)
                .statusBarsPadding(),
        )
        if (pinChromeInLayout && showBrowserChrome) {
            // SPEC-200 R2: 栏边界=标签条+功能栏整块，长按菜单整体置于顶栏下 4dp。
            // SPEC-214 A: 液态玻璃时仅标签条保留实色带，功能栏悬浮玻璃控件。
            MenuBarBoundsProvider {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.surfaceContainer),
                ) {
                TabletTabStrip(
                    onSelectTab = { id ->
                        BrowserTabManager.switchTo(id)
                        onSidePanelChange(TabletSidePanel.None)
                    },
                    onCloseTab = { BrowserTabManager.close(it) },
                    onOpenTabs = {
                        onSidePanelChange(
                            if (sidePanel == TabletSidePanel.Tabs) {
                                TabletSidePanel.None
                            } else {
                                TabletSidePanel.Tabs
                            },
                        )
                    },
                    onNewTab = { BrowserTabManager.openNewTab(url = null, select = true) },
                    onCloseAll = { BrowserTabManager.closeAll() },
                    onAddBookmark = {
                        sessionActions?.addBookmark()
                            ?: context.toastOnUi("请先打开网页")
                    },
                )
                }
                TabletFunctionBar(
                    address = address,
                    onAddressChange = onAddressChange,
                    onAddressFocusChange = onAddressFocusChange,
                    onSubmitAddress = onSubmitAddress,
                    onBack = onBack,
                    onForward = onForward,
                    onRefresh = onRefresh,
                    onOpenBookmarks = {
                        onSidePanelChange(
                            if (sidePanel == TabletSidePanel.Bookmarks) {
                                TabletSidePanel.None
                            } else {
                                TabletSidePanel.Bookmarks
                            },
                        )
                    },
                    onOpenHistory = {
                        onSidePanelChange(
                            if (sidePanel == TabletSidePanel.History) {
                                TabletSidePanel.None
                            } else {
                                TabletSidePanel.History
                            },
                        )
                    },
                    onOpenBookshelf = onOpenBookshelf,
                    onOpenMy = onOpenMy,
                    onReader = onReader,
                    onReaderLongClick = {
                        readerAutoOpenUi =
                            sessionActions?.readerAutoOpenEnabled?.invoke() == true
                        extremePurgeUi =
                            sessionActions?.extremePurgeEnabled?.invoke() != false
                        readerModeMenu = true
                    },
                    readerModeMenu = readerModeMenu,
                    readerAutoOpenUi = readerAutoOpenUi,
                    onReaderAutoOpenUiChange = { readerAutoOpenUi = it },
                    extremePurgeUi = extremePurgeUi,
                    onExtremePurgeUiChange = { extremePurgeUi = it },
                    onReaderModeMenuDismiss = { readerModeMenu = false },
                    sessionActions = sessionActions,
                    onOpenToolbox = {
                        onSidePanelChange(
                            if (sidePanel == TabletSidePanel.Toolbox) {
                                TabletSidePanel.None
                            } else {
                                TabletSidePanel.Toolbox
                            },
                        )
                    },
                )
            }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
            if (addressSuggestions.isNotEmpty() || clipboardUrl != null) {
                BrowserOmniboxSuggestPanel(
                    items = addressSuggestions,
                    onSelect = { item ->
                        onSidePanelChange(TabletSidePanel.None)
                        onOpenUrl(item.url)
                    },
                    clipboardUrl = clipboardUrl,
                    onClipboardSelect = { url ->
                        onSidePanelChange(TabletSidePanel.None)
                        onOpenUrl(url)
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp)
                        .padding(
                            top = 8.dp +
                                if (useOverlayChrome && showBrowserChrome) {
                                    overlayChromeBodyHeight
                                } else {
                                    0.dp
                                },
                        ),
                )
            }
            // SPEC-025/080/111: dim for side drawers / floating toolbox.
            val showSideDrawer = sidePanel == TabletSidePanel.Bookmarks ||
                sidePanel == TabletSidePanel.History ||
                sidePanel == TabletSidePanel.Downloads
            val showToolboxCard = sidePanel == TabletSidePanel.Toolbox
            if (showSideDrawer || showToolboxCard) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scheme.scrim.copy(alpha = 0.32f))
                        .clickable { onSidePanelChange(TabletSidePanel.None) },
                )
            }
            // SPEC-121/126/130: toolbox under chrome.
            TabletToolboxFloatingCard(
                visible = showToolboxCard,
                extraTopPadding = if (useOverlayChrome && showBrowserChrome) {
                    overlayChromeBodyHeight
                } else {
                    0.dp
                },
                sessionActions = sessionActions,
                onSidePanelChange = onSidePanelChange,
                onOpenBrowserSettings = onOpenBrowserSettings,
                adBlockEnabled = adBlockEnabled,
                onAdBlockEnabledChange = { enabled ->
                    adBlockEnabled = enabled
                    shield.adBlockEnabled = enabled
                    if (enabled) sessionActions?.injectAdShield()
                    context.toastOnUi(
                        if (enabled) "已开启广告拦截" else "已关闭广告拦截（新请求生效）",
                    )
                },
                uaMode = uaMode,
                onToggleUa = {
                    val next = when (uaMode) {
                        BrowserUa.Mode.Mobile -> BrowserUa.Mode.Desktop
                        BrowserUa.Mode.Desktop -> BrowserUa.Mode.Mobile
                    }
                    uaMode = next
                    chromePrefs.uaMode = next
                    sessionActions?.applyUserAgentAndReload(next)
                    context.toastOnUi("已切换为${next.label} UA")
                },
                webDarkMode = webDarkMode,
                onWebDarkModeChange = { mode ->
                    webDarkMode = mode
                    chromePrefs.webContentDarkMode = mode
                    sessionActions?.applyWebContentDarkMode(mode)
                    context.toastOnUi(
                        when (mode) {
                            BrowserChromePrefs.WEB_DARK_OFF -> "网页背景：浅色"
                            BrowserChromePrefs.WEB_DARK_ON -> "网页背景：深色"
                            else -> "网页背景：跟随系统"
                        },
                    )
                },
                eyeProtectionEnabled = themeSettings.eyeProtectionEnabled,
                onToggleEyeProtection = {
                    val next = !themeSettings.eyeProtectionEnabled
                    scope.launch {
                        themeSettingsGateway.update { it.copy(eyeProtectionEnabled = next) }
                    }
                    context.toastOnUi(if (next) "已开启护眼模式" else "已关闭护眼模式")
                },
                onOpenEyeProtectionSettings = { showEyeConfigSheet = true },
                textZoomPercent = textZoomPercent,
                onOpenTextZoom = { showTextZoomSheet = true },
                onEnterChromeFullscreen = onEnterChromeFullscreen,
            )
            // SPEC-111: slide only (no fade) for history/bookmarks/downloads side drawers.
            androidx.compose.animation.AnimatedVisibility(
                visible = showSideDrawer,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
            ) {
                val portrait = LocalConfiguration.current.orientation ==
                    Configuration.ORIENTATION_PORTRAIT
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(TabletBrowserLayout.sidePanelWidthFraction(portrait))
                        .background(scheme.surfaceContainerHigh)
                        .clickable(enabled = false) {},
                ) {
                    when (sidePanel) {
                        TabletSidePanel.Bookmarks -> BrowserBookmarksScreen(
                            onBack = { onSidePanelChange(TabletSidePanel.None) },
                            onOpenUrl = {
                                onOpenUrl(it)
                                onSidePanelChange(TabletSidePanel.None)
                            },
                            // SPEC-238: 阅读书签原先落默认空 lambda（点了无反应）。
                            onOpenBook = {
                                onOpenBook(it)
                                onSidePanelChange(TabletSidePanel.None)
                            },
                            inSidePanel = true,
                        )
                        TabletSidePanel.History -> BrowserHistoryScreen(
                            onBack = { onSidePanelChange(TabletSidePanel.None) },
                            onOpenUrl = {
                                onOpenUrl(it)
                                onSidePanelChange(TabletSidePanel.None)
                            },
                            onOpenClearData = {
                                onSidePanelChange(TabletSidePanel.None)
                                onOpenClearBrowsingData()
                            },
                            inSidePanel = true,
                        )
                        TabletSidePanel.Downloads -> BrowserDownloadsScreen(
                            onBack = { onSidePanelChange(TabletSidePanel.None) },
                            onOpenPageArchive = { path, baseUrl, title ->
                                BrowserOpenBridge.offerLocalHtml(
                                    path = path,
                                    baseUrl = baseUrl,
                                    title = title,
                                )
                                onSidePanelChange(TabletSidePanel.None)
                            },
                        )
                        else -> Unit
                    }
                }
            }
            // SPEC-079: no enter/exit animation (match phone Nav None).
            if (sidePanel == TabletSidePanel.Tabs) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scheme.surface)
                        .clickable(enabled = false) {},
                ) {
                    BrowserTabsScreen(
                        onBack = { onSidePanelChange(TabletSidePanel.None) },
                        onOpenActiveTab = { onSidePanelChange(TabletSidePanel.None) },
                        // SPEC-210: shell already applied statusBarsPadding.
                        applyStatusBarInsets = false,
                    )
                }
            }
        }
    }

    // SPEC-130: session auto-hide → overlay chrome (WebView size stays put).
    if (useOverlayChrome) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            // SPEC-242: shorter slide-only enter (fade+AV remount felt janky on scroll-up).
            AnimatedVisibility(
                visible = showBrowserChrome,
                enter = slideInVertically(animationSpec = tween(120)) { -it },
                exit = slideOutVertically(animationSpec = tween(140)) { -it },
            ) {
                // SPEC-200 R2: 栏边界=标签条+功能栏整块，长按菜单整体置于顶栏下 4dp。
                // SPEC-214 A: 液态玻璃时仅标签条实色带。
                MenuBarBoundsProvider {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(scheme.surfaceContainer),
                    ) {
                    TabletTabStrip(
                        onSelectTab = { id ->
                            BrowserTabManager.switchTo(id)
                            onSidePanelChange(TabletSidePanel.None)
                        },
                        onCloseTab = { BrowserTabManager.close(it) },
                        onOpenTabs = {
                            onSidePanelChange(
                                if (sidePanel == TabletSidePanel.Tabs) {
                                    TabletSidePanel.None
                                } else {
                                    TabletSidePanel.Tabs
                                },
                            )
                        },
                        onNewTab = { BrowserTabManager.openNewTab(url = null, select = true) },
                        onCloseAll = { BrowserTabManager.closeAll() },
                        onAddBookmark = {
                            sessionActions?.addBookmark()
                                ?: context.toastOnUi("请先打开网页")
                        },
                    )
                    }
                    TabletFunctionBar(
                        address = address,
                        onAddressChange = onAddressChange,
                        onAddressFocusChange = onAddressFocusChange,
                        onSubmitAddress = onSubmitAddress,
                        onBack = onBack,
                        onForward = onForward,
                        onRefresh = onRefresh,
                        onOpenBookmarks = {
                            onSidePanelChange(
                                if (sidePanel == TabletSidePanel.Bookmarks) {
                                    TabletSidePanel.None
                                } else {
                                    TabletSidePanel.Bookmarks
                                },
                            )
                        },
                        onOpenHistory = {
                            onSidePanelChange(
                                if (sidePanel == TabletSidePanel.History) {
                                    TabletSidePanel.None
                                } else {
                                    TabletSidePanel.History
                                },
                            )
                        },
                        onOpenBookshelf = onOpenBookshelf,
                        onOpenMy = onOpenMy,
                        onReader = onReader,
                        onReaderLongClick = {
                            readerAutoOpenUi =
                                sessionActions?.readerAutoOpenEnabled?.invoke() == true
                            extremePurgeUi =
                                sessionActions?.extremePurgeEnabled?.invoke() != false
                            readerModeMenu = true
                        },
                        readerModeMenu = readerModeMenu,
                        readerAutoOpenUi = readerAutoOpenUi,
                        onReaderAutoOpenUiChange = { readerAutoOpenUi = it },
                        extremePurgeUi = extremePurgeUi,
                        onExtremePurgeUiChange = { extremePurgeUi = it },
                        onReaderModeMenuDismiss = { readerModeMenu = false },
                        sessionActions = sessionActions,
                        onOpenToolbox = {
                            onSidePanelChange(
                                if (sidePanel == TabletSidePanel.Toolbox) {
                                    TabletSidePanel.None
                                } else {
                                    TabletSidePanel.Toolbox
                                },
                            )
                        },
                    )
                }
                }
            }
        }
    }

    BrowserTextZoomSheet(
        show = showTextZoomSheet,
        percent = textZoomPercent,
        onDismiss = { showTextZoomSheet = false },
        onPercentChange = { zoom ->
            textZoomPercent = zoom
            chromePrefs.textZoomPercent = zoom
            sessionActions?.applyTextZoom(zoom)
        },
        forcePageZoom = chromePrefs.forcePageZoom,
        onForcePageZoomChange = { enabled ->
            chromePrefs.forcePageZoom = enabled
            sessionActions?.applyForcePageZoom(enabled)
        },
    )

    EyeProtectionConfigSheet(
        show = showEyeConfigSheet,
        enabled = themeSettings.eyeProtectionEnabled,
        intensity = themeSettings.colorTemperature,
        autoNight = themeSettings.eyeProtectionAutoNight,
        schedule = themeSettings.eyeProtectionSchedule,
        startTime = themeSettings.eyeProtectionStartTime,
        endTime = themeSettings.eyeProtectionEndTime,
        onDismissRequest = { showEyeConfigSheet = false },
        onEnabledChange = { value ->
            scope.launch {
                themeSettingsGateway.update { it.copy(eyeProtectionEnabled = value) }
            }
        },
        onIntensityChange = { value ->
            scope.launch {
                themeSettingsGateway.update { it.copy(colorTemperature = value) }
            }
        },
        onAutoNightChange = { value ->
            scope.launch {
                themeSettingsGateway.update { it.copy(eyeProtectionAutoNight = value) }
            }
        },
        onScheduleChange = { value ->
            scope.launch {
                themeSettingsGateway.update { it.copy(eyeProtectionSchedule = value) }
            }
        },
        onStartTimeChange = { value ->
            scope.launch {
                themeSettingsGateway.update { it.copy(eyeProtectionStartTime = value) }
            }
        },
        onEndTimeChange = { value ->
            scope.launch {
                themeSettingsGateway.update { it.copy(eyeProtectionEndTime = value) }
            }
        },
    )
    }
}

/** SPEC-080/111: left + (align back) + scrollable tabs + trailing count (align toolbox). */
@Composable
private fun TabletTabStrip(
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onOpenTabs: () -> Unit,
    onNewTab: () -> Unit,
    onCloseAll: () -> Unit,
    onAddBookmark: () -> Unit,
) {
    val scheme = LegadoTheme.colorScheme
    val tabs by BrowserTabManager.tabs.collectAsStateWithLifecycle()
    val activeId by BrowserTabManager.activeTabId.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = BrowserUiTokens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // SPEC-111: fixed leftmost +, same 40dp slot / padding as function-bar back.
        Box(
            modifier = Modifier.size(TabletChromeIconSlot),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onNewTab, modifier = Modifier.size(TabletChromeIconSlot)) {
                SymbolIcon(
                    symbol = Symbols.Add,
                    contentDescription = "新建标签页",
                    modifier = Modifier.size(22.dp),
                    tint = scheme.onSurface,
                )
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.Bottom,
        ) {
            tabs.forEach { tab ->
                val selected = tab.id == activeId
                Row(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(if (selected) scheme.surface else scheme.surfaceContainerHighest)
                        .clickable { onSelectTab(tab.id) }
                        .padding(horizontal = 10.dp)
                        .width(140.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = BrowserTabManager.tabDisplayTitle(tab),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        color = scheme.onSurface,
                    )
                    SymbolIcon(
                        symbol = Symbols.Close,
                        contentDescription = "关闭",
                        // SPEC-199: 关闭小钮涟漪随圆形。
                        modifier = Modifier.size(16.dp).clip(CircleShape).clickable { onCloseTab(tab.id) },
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        // SPEC-081/111: count in same 40dp trailing slot as toolbox ChromeIcon.
        Box(
            modifier = Modifier.size(TabletChromeIconSlot),
            contentAlignment = Alignment.Center,
        ) {
            BrowserTabCountButton(
                count = tabs.size,
                tint = scheme.onSurface,
                useGlass = false,
                onOpenTabs = onOpenTabs,
                onNewTab = onNewTab,
                onCloseAll = onCloseAll,
                onAddBookmark = onAddBookmark,
                containerColor = scheme.surface,
                size = TabletTabCountSize,
                shape = TabletTabCountShape,
                drawBorder = false,
                menuBelowTopBar = true,
            )
        }
    }
}

/**
 * SPEC-129/130: toolbox under chrome; width portrait 50% / landscape 40%;
 * inner pad 16dp; [extraTopPadding] clears overlay chrome body.
 */
@Composable
private fun TabletToolboxFloatingCard(
    visible: Boolean,
    extraTopPadding: Dp = 0.dp,
    sessionActions: BrowserSessionActions?,
    onSidePanelChange: (TabletSidePanel) -> Unit,
    onOpenBrowserSettings: () -> Unit,
    adBlockEnabled: Boolean,
    onAdBlockEnabledChange: (Boolean) -> Unit,
    uaMode: BrowserUa.Mode,
    onToggleUa: () -> Unit,
    webDarkMode: String,
    onWebDarkModeChange: (String) -> Unit,
    eyeProtectionEnabled: Boolean,
    onToggleEyeProtection: () -> Unit,
    onOpenEyeProtectionSettings: () -> Unit,
    textZoomPercent: Int,
    onOpenTextZoom: () -> Unit,
    onEnterChromeFullscreen: () -> Unit = {},
) {
    val scheme = LegadoTheme.colorScheme
    val context = LocalContext.current
    val toolboxPrefs = remember { BrowserToolboxPrefs(context) }
    var toolboxOrder by remember { mutableStateOf(toolboxPrefs.orderedIds()) }
    var toolboxEditing by remember { mutableStateOf(false) }
    val panelShape = RoundedCornerShape(24.dp)
    val portrait = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_PORTRAIT
    val widthFraction = TabletBrowserLayout.toolboxWidthFraction(portrait)
    // SPEC-133: fixed height like phone sheet — LazyVerticalGrid.weight needs a bounded parent.
    // Do NOT wrap BrowserToolboxBody in verticalScroll (conflicts with the grid).
    val toolboxHeight = LocalConfiguration.current.screenHeightDp.dp *
        TabletBrowserLayout.tabletToolboxHeightFraction()
    // SPEC-214 ②: tablet toolbox always solid (slide-only enter; no panel glass).
    val solidToolbox = TabletBrowserLayout.tabletToolboxUsesSolidSurfaces(
        currentChromeSurfaceStyle(),
    )
    // SPEC-257: align with bookmarks/history side drawer (full-width slide, no short /4 hop).
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp + extraTopPadding, end = 16.dp),
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd,
        ) {
            Column(
                Modifier
                    .fillMaxWidth(widthFraction)
                    .height(toolboxHeight)
                    .clip(panelShape)
                    .background(scheme.surface, panelShape)
                    .clickable(enabled = false) {}
                    .padding(16.dp),
            ) {
                if (toolboxEditing) {
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
                        TextButton(
                            onClick = {
                                toolboxPrefs.saveOrder(toolboxOrder)
                                toolboxEditing = false
                            },
                        ) { Text("完成") }
                    }
                }
                BrowserToolboxBody(
                    order = toolboxOrder,
                    onOrderChange = { toolboxOrder = it },
                    editing = toolboxEditing,
                    onEditingChange = { toolboxEditing = it },
                    solidSurfaces = solidToolbox,
                    actions = BrowserToolboxActions(
                        onOpenBookmarks = { onSidePanelChange(TabletSidePanel.Bookmarks) },
                        onOpenHistory = { onSidePanelChange(TabletSidePanel.History) },
                        onOpenDownloads = { onSidePanelChange(TabletSidePanel.Downloads) },
                        onOpenSettings = {
                            onSidePanelChange(TabletSidePanel.None)
                            onOpenBrowserSettings()
                        },
                        onNewIncognitoTab = {
                            onSidePanelChange(TabletSidePanel.None)
                            BrowserTabManager.openNewTab(
                                url = null,
                                incognito = true,
                                select = true,
                            )
                        },
                        onAddBookmark = {
                            sessionActions?.addBookmark()
                                ?: context.toastOnUi("请先打开网页")
                        },
                        pageBookmarked = sessionActions?.pageBookmarked == true,
                        onSavePage = {
                            sessionActions?.saveCurrentPage()
                                ?: context.toastOnUi("请先打开网页")
                            onSidePanelChange(TabletSidePanel.None)
                        },
                        onShare = {
                            sessionActions?.shareCurrentPage()
                                ?: context.toastOnUi("请先打开网页")
                            onSidePanelChange(TabletSidePanel.None)
                        },
                        onCopyLink = {
                            sessionActions?.copyCurrentUrl()
                                ?: context.toastOnUi("当前没有可复制的链接")
                        },
                        onPickBlock = {
                            sessionActions?.startPickMode()
                                ?: context.toastOnUi("请先打开网页")
                            onSidePanelChange(TabletSidePanel.None)
                        },
                        adBlockEnabled = adBlockEnabled,
                        onToggleAdBlock = { onAdBlockEnabledChange(!adBlockEnabled) },
                        uaMode = uaMode,
                        onToggleUa = onToggleUa,
                        webDarkMode = webDarkMode,
                        onWebDarkModeChange = onWebDarkModeChange,
                        eyeProtectionEnabled = eyeProtectionEnabled,
                        onToggleEyeProtection = onToggleEyeProtection,
                        onOpenEyeProtectionSettings = onOpenEyeProtectionSettings,
                        textZoomPercent = textZoomPercent,
                        onOpenTextZoom = onOpenTextZoom,
                        onFindInPage = {
                            onSidePanelChange(TabletSidePanel.None)
                            sessionActions?.openFindInPage()
                        },
                        videoCapsuleAvailable =
                            sessionActions?.videoCapsuleAvailable?.invoke() == true,
                        onOpenVideoCapsule = {
                            onSidePanelChange(TabletSidePanel.None)
                            sessionActions?.openVideoCapsule()
                                ?: context.toastOnUi("请先打开网页")
                        },
                        onEnterFullscreen = {
                            onSidePanelChange(TabletSidePanel.None)
                            onEnterChromeFullscreen()
                        },
                        onTranslatePage = {
                            onSidePanelChange(TabletSidePanel.None)
                            sessionActions?.translatePage()
                                ?: context.toastOnUi("请先打开网页")
                        },
                        onOpenTranslationSettings = {
                            onSidePanelChange(TabletSidePanel.None)
                            context.startActivity(
                                io.legado.app.ui.main.MainIntent.createSettingsIntent(
                                    context,
                                    io.legado.app.ui.main.MainRouteConst.ROUTE_SETTINGS_TRANSLATION,
                                ),
                            )
                        },
                        pageTranslated = sessionActions?.pageTranslated == true,
                    ),
                    onDismiss = { onSidePanelChange(TabletSidePanel.None) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabletFunctionBar(
    address: TextFieldValue,
    onAddressChange: (TextFieldValue) -> Unit,
    onAddressFocusChange: (Boolean) -> Unit = {},
    onSubmitAddress: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookshelf: () -> Unit,
    onOpenMy: () -> Unit = {},
    onReader: () -> Unit,
    onReaderLongClick: () -> Unit,
    readerModeMenu: Boolean,
    readerAutoOpenUi: Boolean,
    onReaderAutoOpenUiChange: (Boolean) -> Unit,
    extremePurgeUi: Boolean,
    onExtremePurgeUiChange: (Boolean) -> Unit,
    onReaderModeMenuDismiss: () -> Unit,
    sessionActions: BrowserSessionActions?,
    onOpenToolbox: () -> Unit,
) {
    val scheme = LegadoTheme.colorScheme
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val ntpPrefs = remember { BrowserNtpPrefs(context) }
    var engine by remember { mutableStateOf(ntpPrefs.searchEngine()) }
    var engineMenu by remember { mutableStateOf(false) }
    // SPEC-163
    var backHistoryMenu by remember { mutableStateOf(false) }
    var forwardHistoryMenu by remember { mutableStateOf(false) }
    var backHistoryItems by remember { mutableStateOf<List<BrowserNavHistory.Item>>(emptyList()) }
    var forwardHistoryItems by remember { mutableStateOf<List<BrowserNavHistory.Item>>(emptyList()) }
    val readerCapsuleShape = RoundedCornerShape(24.dp)
    val omniboxShape = RoundedCornerShape(24.dp)
    val omniboxHeight = TabletBrowserLayout.TabletOmniboxHeightDp.dp
    val itemGap = TabletBrowserLayout.ChromeItemGapDp.dp
    // SPEC-214 ①：液态玻璃仅 omnibox + 净读（图标不上玻璃，避免 RenderThread DirtyStack 溢出）。
    val liquidGlass = TabletBrowserLayout.tabletFunctionBarUsesLiquidGlass(
        currentChromeSurfaceStyle(),
    )

    // SPEC-168: one gap between every control. Omnibox and 净读 are 40dp.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BrowserUiTokens.TopBarHeight)
            // ①: keep solid strip under icons; only field/capsule are glass.
            .background(scheme.surface)
            .padding(horizontal = itemGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(itemGap),
    ) {
        ChromeIcon(
            icon = Symbols.ArrowBack,
            desc = "后退",
            onClick = onBack,
            onLongClick = {
                val items = sessionActions?.backHistoryItems?.invoke().orEmpty()
                if (items.isEmpty()) {
                    context.toastOnUi("没有可后退的页面")
                } else {
                    backHistoryItems = items
                    forwardHistoryMenu = false
                    backHistoryMenu = true
                }
            },
            menu = {
                BrowserNavHistoryMenu(
                    expanded = backHistoryMenu,
                    items = backHistoryItems,
                    onDismiss = { backHistoryMenu = false },
                    onSelect = { sessionActions?.goHistoryOffset?.invoke(it.steps) },
                    aboveAnchor = false,
                )
            },
        )
        ChromeIcon(
            icon = Symbols.ArrowForwardIos,
            desc = "前进",
            onClick = onForward,
            onLongClick = {
                val items = sessionActions?.forwardHistoryItems?.invoke().orEmpty()
                if (items.isEmpty()) {
                    context.toastOnUi("没有可前进的页面")
                } else {
                    forwardHistoryItems = items
                    backHistoryMenu = false
                    forwardHistoryMenu = true
                }
            },
            menu = {
                BrowserNavHistoryMenu(
                    expanded = forwardHistoryMenu,
                    items = forwardHistoryItems,
                    onDismiss = { forwardHistoryMenu = false },
                    onSelect = { sessionActions?.goHistoryOffset?.invoke(it.steps) },
                    aboveAnchor = false,
                )
            },
        )
        ChromeIcon(
            icon = Symbols.Refresh,
            desc = "刷新",
            onClick = onRefresh,
            onLongClick = { sessionActions?.scrollToTop?.invoke() },
        )
        // SPEC-126/168: 我的, then reading list, then the omnibox.
        ChromeIcon(Symbols.Person, "我的", onOpenMy)
        ChromeIcon(Symbols.Book, "阅读列表", onOpenBookshelf)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(omniboxHeight)
                .then(
                    if (liquidGlass) {
                        Modifier.browserControlLiquidGlass(
                            shape = omniboxShape,
                            interactive = false,
                            showShadow = false,
                        )
                    } else {
                        Modifier
                            .clip(omniboxShape)
                            .background(scheme.surfaceContainerHighest)
                    },
                )
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        // SPEC-199: 26dp 方槽涟漪随圆形。
                        .clip(CircleShape)
                        .background(scheme.surface)
                        .clickable { engineMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(engine.logoRes),
                        contentDescription = engine.displayName,
                        modifier = Modifier.size(20.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                SearchEnginePickerPopup(
                    expanded = engineMenu,
                    onDismiss = { engineMenu = false },
                    selected = engine,
                    onSelect = {
                        engine = it
                        ntpPrefs.searchEngineId = it.id
                    },
                    textColor = scheme.onSurface,
                    mutedColor = scheme.onSurfaceVariant,
                    verticalOffset = omniboxHeight + itemGap,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = address,
                onValueChange = onAddressChange,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onAddressFocusChange(it.isFocused) },
                singleLine = true,
                textStyle = TextStyle(color = scheme.onSurface, fontSize = 14.sp),
                cursorBrush = SolidColor(BrowserUiTokens.Primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    // SPEC-219: submit before hide/clearFocus so draft is not restored empty.
                    if (address.text.isNotBlank()) {
                        onSubmitAddress()
                        keyboardController?.hide()
                        focusManager.clearFocus(force = true)
                    }
                }),
                // SPEC-146 R3: fresh search box hint while idle at NTP/home.
                decorationBox = { inner ->
                    Box {
                        if (address.text.isEmpty()) {
                            Text(
                                "搜索或输入网址",
                                color = scheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            // SPEC-254/262: 需要翻译或已译（可还原）时显示地址栏翻译键。
            val showTranslateKey = sessionActions?.pageNeedsTranslate == true ||
                sessionActions?.pageTranslated == true
            if (showTranslateKey) {
                IconButton(
                    onClick = {
                        sessionActions.translatePage()
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    SymbolIcon(
                        symbol = Symbols.Translate,
                        contentDescription = if (sessionActions?.pageTranslated == true) {
                            "恢复原文"
                        } else {
                            "翻译"
                        },
                        modifier = Modifier.size(22.dp),
                        tint = scheme.onSurface,
                    )
                }
            }
            // SPEC-259: toggle add / remove bookmark for current URL.
            val bookmarked = sessionActions?.pageBookmarked == true
            IconButton(
                onClick = {
                    sessionActions?.addBookmark()
                        ?: context.toastOnUi("请先打开网页")
                },
                modifier = Modifier.size(36.dp),
            ) {
                SymbolIcon(
                    symbol = if (bookmarked) Symbols.BookmarkRemove else Symbols.BookmarkAdd,
                    contentDescription = if (bookmarked) "移出收藏" else "加入收藏",
                    modifier = Modifier.size(22.dp),
                    tint = scheme.onSurface,
                )
            }
        }
        ChromeIcon(Symbols.Bookmark, "收藏", onOpenBookmarks)
        ChromeIcon(Symbols.History, "历史", onOpenHistory)
        ChromeIcon(Symbols.Box, "工具箱", onOpenToolbox)
        // SPEC-126: 「阅读」capsule at far right (icon + label, 24dp radius fill).
        Box {
            Row(
                modifier = Modifier
                    .height(omniboxHeight)
                    .then(
                        if (liquidGlass) {
                            Modifier.browserControlLiquidGlass(
                                shape = readerCapsuleShape,
                                interactive = false,
                                showShadow = false,
                            )
                        } else {
                            Modifier
                                .clip(readerCapsuleShape)
                                .background(scheme.surfaceContainerHighest, readerCapsuleShape)
                        },
                    )
                    .combinedClickable(
                        onClick = onReader,
                        onLongClick = onReaderLongClick,
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SymbolIcon(
                    symbol = Symbols.Book2,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = scheme.onSurface,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "净读",
                    color = scheme.onSurface,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
            }
            BrowserReaderModeMenu(
                expanded = readerModeMenu,
                onDismiss = onReaderModeMenuDismiss,
                autoOpenEnabled = readerAutoOpenUi,
                onAutoOpenChange = { enabled ->
                    val actions = sessionActions
                    if (actions == null) {
                        context.toastOnUi("请先打开网页")
                        return@BrowserReaderModeMenu
                    }
                    onReaderAutoOpenUiChange(enabled)
                    actions.setReaderAutoOpen(enabled)
                    context.toastOnUi(
                        if (enabled) "已开启自动净读模式" else "已关闭自动净读模式",
                    )
                },
                extremePurgeEnabled = extremePurgeUi,
                onExtremePurgeChange = { enabled ->
                    val actions = sessionActions
                    if (actions == null) {
                        context.toastOnUi("请先打开网页")
                        return@BrowserReaderModeMenu
                    }
                    onExtremePurgeUiChange(enabled)
                    actions.setExtremePurge(enabled)
                    context.toastOnUi(
                        if (enabled) "已开启极致净化" else "已关闭极致净化",
                    )
                },
                onTextMode = {
                    sessionActions?.openReaderText?.invoke()
                        ?: context.toastOnUi("请先打开网页")
                },
                onImageMode = {
                    sessionActions?.openReaderImage?.invoke()
                        ?: context.toastOnUi("请先打开网页")
                },
                onMixedMode = {
                    sessionActions?.openReaderMixed?.invoke()
                        ?: context.toastOnUi("请先打开网页")
                },
                belowAnchor = true,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChromeIcon(
    icon: MaterialSymbol,
    desc: String,
    onClick: () -> Unit,
    tint: Color? = null,
    onLongClick: (() -> Unit)? = null,
    menu: (@Composable () -> Unit)? = null,
) {
    val scheme = LegadoTheme.colorScheme
    Box(
        modifier = Modifier.size(TabletChromeIconSlot),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(TabletChromeIconSlot)
                // SPEC-199: 平板功能键涟漪随圆形。
                // SPEC-214 ①: icons stay non-glass (omnibox/净读 only).
                .clip(CircleShape)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    } else {
                        Modifier.clickable(onClick = onClick)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            SymbolIcon(
                symbol = icon,
                contentDescription = desc,
                modifier = Modifier.size(22.dp),
                tint = tint ?: scheme.onSurface,
            )
        }
        menu?.invoke()
    }
}

