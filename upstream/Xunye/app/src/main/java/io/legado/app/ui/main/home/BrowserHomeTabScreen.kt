package io.legado.app.ui.main.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.browser.engine.BrowserChromePrefs
import io.legado.app.browser.engine.BrowserFormFactor
import io.legado.app.browser.engine.BrowserOpenBridge
import io.legado.app.browser.engine.BrowserClipboardUrl
import io.legado.app.browser.engine.BrowserTabEmbeddedReader
import io.legado.app.browser.engine.BrowserTabEmbeddedReaderStore
import io.legado.app.browser.engine.BrowserTabManager
import io.legado.app.browser.engine.HideBrowserChromeInReaderState
import io.legado.app.browser.engine.reselectAllOmniboxText
import io.legado.app.data.entities.Book
import io.legado.app.ui.browser.BrowserNtpScreen
import io.legado.app.ui.browser.BrowserSessionActions
import io.legado.app.ui.browser.BrowserSessionScreen
import io.legado.app.ui.browser.BrowserTabsScreen
import io.legado.app.ui.browser.BrowserTheme
import io.legado.app.ui.browser.TabletBrowserLayout
import io.legado.app.ui.browser.TabletBrowserShell
import io.legado.app.ui.browser.TabletEmbeddedReaderHost
import io.legado.app.ui.browser.TabletEmbeddedReaderKind
import io.legado.app.ui.browser.TabletSidePanel
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.SymbolIcon
import io.legado.app.ui.widget.components.icon.Symbols
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home tab: multi-tab NTP ↔ session (SPEC-012/018/108).
 * Tablet / foldable inner screen: Chrome-style top chrome + half-width side panels.
 * Phone / foldable cover: phone chrome.
 * Shell swap uses no fade/scale (liquid-glass RenderThread safety); smoothness comes from
 * MainActivity configChanges + BrowserSessionHolder reattach.
 */
@Composable
fun BrowserHomeTabScreen(
    onOpenBook: (Book) -> Unit,
    onSessionActiveChange: (Boolean) -> Unit = {},
    onPhoneSearchActiveChange: (Boolean) -> Unit = {},
    onOpenBrowserSettings: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenClearBrowsingData: () -> Unit = {},
    onOpenTabs: () -> Unit = {},
    onOpenReader: (bookUrl: String) -> Unit = {},
    onOpenMangaReader: (bookUrl: String) -> Unit = {},
    /** SPEC-238: 平板侧栏阅读书签 → 阅读器定位书签章节（区别于 onOpenBook→详情页）。 */
    onOpenReadingBookmark: (Book) -> Unit = {},
    onOpenBookshelf: () -> Unit = {},
    onOpenMy: () -> Unit = {},
    /** SPEC-250: NTP「AI 助手」→ 现有 AI 对话。 */
    onOpenAiChat: () -> Unit = {},
    /** SPEC-183: shared-element scopes for the reading-list card covers. */
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    BrowserTheme {
        BrowserHomeTabBody(
            onOpenBook = onOpenBook,
            onSessionActiveChange = onSessionActiveChange,
            onPhoneSearchActiveChange = onPhoneSearchActiveChange,
            onOpenBrowserSettings = onOpenBrowserSettings,
            onOpenBookmarks = onOpenBookmarks,
            onOpenDownloads = onOpenDownloads,
            onOpenHistory = onOpenHistory,
            onOpenClearBrowsingData = onOpenClearBrowsingData,
            onOpenTabs = onOpenTabs,
            onOpenReader = onOpenReader,
            onOpenMangaReader = onOpenMangaReader,
            onOpenReadingBookmark = onOpenReadingBookmark,
            onOpenBookshelf = onOpenBookshelf,
            onOpenMy = onOpenMy,
            onOpenAiChat = onOpenAiChat,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}

@Composable
private fun BrowserHomeTabBody(
    onOpenBook: (Book) -> Unit,
    onSessionActiveChange: (Boolean) -> Unit,
    onPhoneSearchActiveChange: (Boolean) -> Unit,
    onOpenBrowserSettings: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenClearBrowsingData: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenReader: (bookUrl: String) -> Unit,
    onOpenMangaReader: (bookUrl: String) -> Unit,
    onOpenReadingBookmark: (Book) -> Unit,
    onOpenBookshelf: () -> Unit,
    onOpenMy: () -> Unit,
    onOpenAiChat: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val context = LocalContext.current
    // SPEC-108: react to fold/unfold (do not remember forever).
    val isTablet = BrowserFormFactor.isTablet(LocalConfiguration.current)
    val chromePrefs = remember { BrowserChromePrefs(context) }
    val tabs by BrowserTabManager.tabs.collectAsStateWithLifecycle()
    val activeId by BrowserTabManager.activeTabId.collectAsStateWithLifecycle()
    val active = tabs.firstOrNull { it.id == activeId } ?: tabs.firstOrNull()

    val pendingOpen by BrowserOpenBridge.pendingUrl.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpen) {
        val url = pendingOpen ?: return@LaunchedEffect
        BrowserTabManager.openUrlInActive(url)
        BrowserOpenBridge.consume()
    }
    val pendingLocalHtml by BrowserOpenBridge.pendingLocalHtml.collectAsStateWithLifecycle()
    LaunchedEffect(pendingLocalHtml) {
        val open = pendingLocalHtml ?: return@LaunchedEffect
        BrowserOpenBridge.consumeLocalHtml()
        BrowserOpenBridge.armLocalHtml(open)
        BrowserTabManager.openUrlInActive(
            open.baseUrl.takeIf { it.startsWith("http") } ?: "about:blank",
            omniboxLabel = open.title.ifBlank { "已保存网页" },
        )
    }

    // SPEC-094: showHome keeps url/title for tab restore while showing NTP.
    // SPEC-097/101: logical session vs chrome — defer sessionActive=false until exit slide ends
    // so MainScreen bottom bar / pager do not recompose away the outgoing page.
    val logicalSessionActive = BrowserTabManager.isWebSessionShowing(active)
    val showNtp = !logicalSessionActive
    LaunchedEffect(logicalSessionActive) {
        if (logicalSessionActive) {
            onSessionActiveChange(true)
        }
    }
    DisposableEffect(Unit) {
        onDispose { onSessionActiveChange(false) }
    }
    val onSessionExitFinished = remember(onSessionActiveChange) {
        {
            BrowserTabManager.releaseSessionHostIfParked()
            onSessionActiveChange(false)
        }
    }

    var sidePanel by remember { mutableStateOf(TabletSidePanel.None) }
    var chromeVisible by remember { mutableStateOf(true) }
    // SPEC-242: toolbox fullscreen — reset on tab switch (no cross-tab memory).
    var browserFullscreen by remember(active?.id) { mutableStateOf(false) }
    BackHandler(enabled = browserFullscreen) {
        browserFullscreen = false
        chromeVisible = true
    }
    val chromeScope = rememberCoroutineScope()
    var chromeDebounceJob by remember { mutableStateOf<Job?>(null) }
    // SPEC-146 R1: address carries TextFieldValue so focus-gain can select-all.
    // SPEC-146 R3: NTP (showHome) → empty address; idle web → page title.
    fun idleTabletAddress(tab: io.legado.app.browser.engine.BrowserTab?): TextFieldValue =
        when {
            tab == null -> TextFieldValue("")
            tab.showHome -> TextFieldValue("")
            else -> TextFieldValue(BrowserTabManager.tabDisplayTitle(tab))
        }
    var address by remember { mutableStateOf(idleTabletAddress(active)) }
    var addressFocused by remember { mutableStateOf(false) }
    var clipboardUrl by remember { mutableStateOf<String?>(null) }
    var submittingAddress by remember { mutableStateOf(false) }
    var sessionActions by remember { mutableStateOf<BrowserSessionActions?>(null) }
    // SPEC-208: per-tab embedded reader memory (not process-persistent).
    val embeddedByTab by BrowserTabEmbeddedReaderStore.byTabId.collectAsStateWithLifecycle()
    val activeEmbedded = active?.id?.let { embeddedByTab[it] }
    val embeddedKind = when (activeEmbedded?.kind) {
        BrowserTabEmbeddedReader.KIND_NOVEL -> TabletEmbeddedReaderKind.Novel
        BrowserTabEmbeddedReader.KIND_MANGA -> TabletEmbeddedReaderKind.Manga
        else -> null
    }
    val embeddedReaderUrl = activeEmbedded?.bookUrl
    val hideChromeInReader by HideBrowserChromeInReaderState.value.collectAsStateWithLifecycle()
    val readerHidesBrowserChrome = embeddedKind != null && hideChromeInReader

    fun openEmbeddedReader(kind: TabletEmbeddedReaderKind, bookUrl: String) {
        val tabId = active?.id ?: return
        BrowserTabEmbeddedReaderStore.open(
            tabId = tabId,
            kind = if (kind == TabletEmbeddedReaderKind.Novel) {
                BrowserTabEmbeddedReader.KIND_NOVEL
            } else {
                BrowserTabEmbeddedReader.KIND_MANGA
            },
            bookUrl = bookUrl,
        )
        if (!hideChromeInReader) chromeVisible = true
        sidePanel = TabletSidePanel.None
    }

    fun closeEmbeddedReader() {
        active?.id?.let { BrowserTabEmbeddedReaderStore.clear(it) }
    }

    // SPEC-125: idle address = page title / host (not search draft).
    LaunchedEffect(active?.id) {
        addressFocused = false
        clipboardUrl = null
        address = idleTabletAddress(active)
    }
    LaunchedEffect(active?.title, active?.url, active?.showHome) {
        if (!addressFocused) {
            address = idleTabletAddress(active)
        }
    }

    // SPEC-108: collapsing to cover closes tablet side panels.
    LaunchedEffect(isTablet) {
        if (!isTablet) sidePanel = TabletSidePanel.None
    }

    AnimatedContent(
        targetState = isTablet,
        transitionSpec = {
            // SPEC-108 / 079: no fade/scale over browser + liquid glass.
            EnterTransition.None togetherWith ExitTransition.None
        },
        label = "browserShellFormFactor",
    ) { tablet ->
    if (!tablet) {
        PhoneBrowserHome(
            showNtp = showNtp,
            activeUrl = active?.url,
            omniboxLabel = active?.omniboxLabel,
            initialTitle = active?.title,
            navEpoch = active?.navEpoch ?: 0L,
            incognito = active?.incognito == true,
            onOpenBook = onOpenBook,
            onOpenBrowserSettings = onOpenBrowserSettings,
            onOpenBookmarks = onOpenBookmarks,
            onOpenDownloads = onOpenDownloads,
            onOpenHistory = onOpenHistory,
            onOpenTabs = onOpenTabs,
            onOpenReader = onOpenReader,
            onOpenMangaReader = onOpenMangaReader,
            onOpenAiChat = onOpenAiChat,
            sessionKey = active?.id,
            onSessionExitFinished = onSessionExitFinished,
            onPhoneSearchActiveChange = onPhoneSearchActiveChange,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
        )
    } else {
    TabletBrowserShell(
        address = address,
        onAddressChange = { address = it },
        onAddressFocusChange = { focused ->
            if (!addressFocused && focused) {
                // SPEC-125/146 R3: NTP (showHome) stays empty for search; web → URL.
                // SPEC-146 R1 / SPEC-207: select all（获焦后再补一次，避免点击落点盖选区）。
                if (active?.showHome == true) {
                    address = TextFieldValue("")
                } else {
                    val editUrl = active?.url.orEmpty()
                    address = if (editUrl.isNotBlank()) {
                        TextFieldValue(editUrl, TextRange(0, editUrl.length))
                    } else {
                        TextFieldValue("")
                    }
                    chromeScope.launch {
                        reselectAllOmniboxText(
                            get = { address },
                            set = { address = it },
                        )
                    }
                }
                // SPEC-146 R2: clipboard link candidate.
                clipboardUrl = BrowserClipboardUrl.latest(context)
            } else if (
                TabletBrowserLayout.omniboxShouldRestoreOnFocusLost(
                    wasFocused = addressFocused,
                    nowFocused = focused,
                    submitting = submittingAddress,
                )
            ) {
                // SPEC-219: IME often clears focus before onGo; delay restore so
                // submit can flip submittingAddress first.
                val draft = address.text
                chromeScope.launch {
                    delay(64)
                    if (!submittingAddress && !addressFocused) {
                        clipboardUrl = null
                        // Only restore if text was not replaced by submit.
                        if (address.text == draft) {
                            address = idleTabletAddress(active)
                        }
                    }
                }
            }
            val wasFocused = addressFocused
            addressFocused = focused
            if (focused) {
                submittingAddress = false
            }
        },
        addressFocused = addressFocused,
        clipboardUrl = clipboardUrl,
        onSubmitAddress = {
            // SPEC-219: capture text first — IME may clear focus before onGo finishes.
            val raw = address.text.trim()
            if (raw.isNotEmpty()) {
                submittingAddress = true
                clipboardUrl = null
                address = TextFieldValue(raw)
                active?.id?.let { BrowserTabEmbeddedReaderStore.clear(it) }
                BrowserTabManager.updateActiveOmniboxLabel(raw)
                // SPEC-120: always resolve search/host → URL (fixes net::ERROR on NTP).
                val url = io.legado.app.browser.engine.resolveBrowserNavigationInput(
                    raw,
                    io.legado.app.browser.engine.BrowserNtpPrefs(context).searchEngine(),
                )
                if (url.isNotEmpty()) {
                    // SPEC-219: always openUrlInActive — stale sessionActions.loadUrl on NTP
                    // can no-op after park/destroy and skip the ?: fallback.
                    BrowserTabManager.openUrlInActive(url, omniboxLabel = raw)
                }
                addressFocused = false
            }
        },
        onBack = { sessionActions?.goBack() ?: BrowserTabManager.clearActiveToNtp() },
        onForward = { sessionActions?.goForward() },
        onRefresh = { sessionActions?.reload() },
        onReader = { sessionActions?.openReader() },
        onOpenBookshelf = onOpenBookshelf,
        onOpenMy = onOpenMy,
        // SPEC-208/242: reader or toolbox fullscreen hides tab+omnibox chrome.
        chromeVisible = when {
            readerHidesBrowserChrome || browserFullscreen -> false
            else -> chromeVisible || !logicalSessionActive || embeddedKind != null
        },
        // NTP/reader: pin chrome in layout. Web session: overlay for auto-hide (SPEC-130).
        pinChromeInLayout = when {
            readerHidesBrowserChrome || browserFullscreen -> false
            else -> !logicalSessionActive || embeddedKind != null ||
                !chromePrefs.autoHideTopBar
        },
        sidePanel = sidePanel,
        onSidePanelChange = { sidePanel = it },
        onOpenUrl = { BrowserTabManager.openUrlInActive(it) },
        // SPEC-238: 侧栏阅读书签 → 阅读器（onOpenBook 已被书籍详情语义占用）。
        onOpenBook = onOpenReadingBookmark,
        onOpenBrowserSettings = onOpenBrowserSettings,
        onOpenClearBrowsingData = onOpenClearBrowsingData,
        onOpenDownloads = onOpenDownloads,
        sessionActions = sessionActions,
        onEnterChromeFullscreen = {
            browserFullscreen = true
            chromeVisible = false
            sidePanel = TabletSidePanel.None
        },
    ) {
        Box(Modifier.fillMaxSize()) {
        BrowserNtpSessionSwitch(
            showNtp = showNtp,
            onSessionExitFinished = onSessionExitFinished,
            ntp = {
                BrowserNtpScreen(
                    incognito = active?.incognito == true,
                    onIncognitoChange = { BrowserTabManager.setActiveIncognito(it) },
                    onOpenUrl = { openUrl, inIncognito, label ->
                        BrowserTabManager.setActiveIncognito(inIncognito)
                        BrowserTabManager.openUrlInActive(openUrl, inIncognito, omniboxLabel = label)
                    },
                    onOpenBook = onOpenBook,
                    onOpenHistory = { sidePanel = TabletSidePanel.History },
                    onOpenBookmarks = { sidePanel = TabletSidePanel.Bookmarks },
                    onOpenTabs = { sidePanel = TabletSidePanel.Tabs },
                    onOpenAiChat = onOpenAiChat,
                    tabletLayout = true,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            },
            session = {
                val tab = active
                val url = tab?.url
                if (tab != null && !url.isNullOrBlank()) {
                BrowserSessionScreen(
                    initialUrl = url,
                    showNavigationBack = true,
                    onNavigateBack = { BrowserTabManager.clearActiveToNtp() },
                    incognito = tab.incognito,
                    sessionKey = tab.id,
                    initialOmniboxLabel = tab.omniboxLabel,
                    initialTitle = tab.title,
                    navEpoch = tab.navEpoch,
                    onOpenBrowserSettings = onOpenBrowserSettings,
                    onOpenBookmarks = { sidePanel = TabletSidePanel.Bookmarks },
                    onOpenDownloads = onOpenDownloads,
                    onOpenHistory = { sidePanel = TabletSidePanel.History },
                    onOpenTabs = { sidePanel = TabletSidePanel.Tabs },
                    onOpenReader = { openEmbeddedReader(TabletEmbeddedReaderKind.Novel, it) },
                    onOpenMangaReader = { openEmbeddedReader(TabletEmbeddedReaderKind.Manga, it) },
                    onPageMeta = { pageUrl, title ->
                        if (!addressFocused) {
                            address = idleTabletAddress(tab)
                        }
                        BrowserTabManager.updateActiveMeta(pageUrl, title)
                    },
                    tabletEmbedded = true,
                    onBindActions = { sessionActions = it },
                    onScrollDirection = { dir ->
                        if (browserFullscreen) return@BrowserSessionScreen
                        if (embeddedKind != null) {
                            chromeDebounceJob?.cancel()
                            chromeVisible = true
                            return@BrowserSessionScreen
                        }
                        if (!chromePrefs.autoHideTopBar) {
                            chromeDebounceJob?.cancel()
                            chromeVisible = true
                            return@BrowserSessionScreen
                        }
                        // SPEC-242: show chrome immediately on scroll-up; short debounce only on hide.
                        val wantVisible = dir != "down"
                        if (wantVisible == chromeVisible) return@BrowserSessionScreen
                        chromeDebounceJob?.cancel()
                        if (wantVisible) {
                            chromeVisible = true
                        } else {
                            chromeDebounceJob = chromeScope.launch {
                                delay(100L)
                                chromeVisible = false
                            }
                        }
                    },
                )
                }
            },
        )
        if (browserFullscreen && logicalSessionActive && embeddedKind == null) {
            val scheme = LegadoTheme.colorScheme
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 54.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(scheme.surface.copy(alpha = 0.82f))
                    .clickable {
                        browserFullscreen = false
                        chromeVisible = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                SymbolIcon(
                    Symbols.CloseFullscreen,
                    contentDescription = "退出全屏",
                    tint = scheme.onSurface.copy(alpha = 0.92f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        // SPEC-257/266: tablet 净读 enter/exit — slide only (no fade over liquid-glass reader chrome).
        val overlayUrl = embeddedReaderUrl
        val showEmbeddedReader = embeddedKind != null && !overlayUrl.isNullOrBlank()
        var exitKind by remember { mutableStateOf<TabletEmbeddedReaderKind?>(null) }
        var exitUrl by remember { mutableStateOf<String?>(null) }
        if (embeddedKind != null) exitKind = embeddedKind
        if (!overlayUrl.isNullOrBlank()) exitUrl = overlayUrl
        AnimatedVisibility(
            visible = showEmbeddedReader,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f),
            enter = slideInVertically(
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 5 },
            ),
            exit = slideOutVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                targetOffsetY = { it / 6 },
            ),
        ) {
            val kind = exitKind
            val url = exitUrl
            if (kind != null && !url.isNullOrBlank()) {
                // Key by tab+url so switching tabs remounts the correct reader (SPEC-208).
                androidx.compose.runtime.key(active?.id, url, kind) {
                    TabletEmbeddedReaderHost(
                        kind = kind,
                        bookUrl = url,
                        onClose = { closeEmbeddedReader() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        }
    }
    } // tablet shell
    } // AnimatedContent
}

@Composable
private fun BrowserNtpSessionSwitch(
    showNtp: Boolean,
    onSessionExitFinished: () -> Unit = {},
    ntp: @Composable () -> Unit,
    session: @Composable () -> Unit,
) {
    // SPEC-100/101: slide-only (no fade/scale over glass / AwContents).
    // SPEC-138: NTP is a permanent composed underlay while the session is on
    // stage; pop just slides the session out to reveal the live NTP — no blank
    // frame during the slide and no NTP recomposition (JIT recompile) on exit.
    val scheme = LegadoTheme.colorScheme
    val spec = tween<IntOffset>(durationMillis = 240, easing = FastOutSlowInEasing)
    val sessionState = remember {
        MutableTransitionState(!showNtp).apply { targetState = !showNtp }
    }
    val exitFinished by rememberUpdatedState(onSessionExitFinished)

    LaunchedEffect(showNtp) {
        val wantSession = !showNtp
        if (sessionState.isIdle &&
            sessionState.currentState == wantSession && sessionState.targetState == wantSession
        ) {
            // Remount / already settled: still sync bar + release (idempotent).
            if (showNtp) exitFinished()
            return@LaunchedEffect
        }
        sessionState.targetState = wantSession
        if (showNtp) {
            // Pop: session slides out to the right on top; the live NTP is
            // already composed underneath (SPEC-138), so the reveal needs no
            // extra composition on the animated frames.
            snapshotFlow { sessionState.isIdle && !sessionState.currentState }.first { it }
            exitFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.surface),
    ) {
        ntp()
        AnimatedVisibility(
            visibleState = sessionState,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f),
            enter = slideInHorizontally(animationSpec = spec) { fullWidth -> fullWidth },
            exit = slideOutHorizontally(animationSpec = spec) { fullWidth -> fullWidth },
        ) {
            session()
        }
    }
}

@Composable
private fun PhoneBrowserHome(
    showNtp: Boolean,
    activeUrl: String?,
    omniboxLabel: String?,
    initialTitle: String?,
    navEpoch: Long,
    incognito: Boolean,
    onOpenBook: (Book) -> Unit,
    onOpenBrowserSettings: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenReader: (bookUrl: String) -> Unit,
    onOpenMangaReader: (bookUrl: String) -> Unit,
    onOpenAiChat: () -> Unit = {},
    sessionKey: String?,
    onSessionExitFinished: () -> Unit,
    onPhoneSearchActiveChange: (Boolean) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    // SPEC-170: landscape tabs = tablet-like overlay; portrait keeps route navigation.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useOverlayTabs = TabletBrowserLayout.phoneUsesTabletLikePanels(landscape)
    var showTabsOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(useOverlayTabs) {
        if (!useOverlayTabs) showTabsOverlay = false
    }
    val openTabs: () -> Unit = {
        if (useOverlayTabs) showTabsOverlay = true else onOpenTabs()
    }
    BackHandler(enabled = showTabsOverlay) { showTabsOverlay = false }

    Box(Modifier.fillMaxSize()) {
        BrowserNtpSessionSwitch(
            showNtp = showNtp,
            onSessionExitFinished = onSessionExitFinished,
            ntp = {
                BrowserNtpScreen(
                    incognito = incognito,
                    onIncognitoChange = { BrowserTabManager.setActiveIncognito(it) },
                    onOpenUrl = { openUrl, inIncognito, label ->
                        BrowserTabManager.setActiveIncognito(inIncognito)
                        BrowserTabManager.openUrlInActive(openUrl, inIncognito, omniboxLabel = label)
                    },
                    onOpenBook = onOpenBook,
                    onOpenHistory = onOpenHistory,
                    onOpenBookmarks = onOpenBookmarks,
                    onOpenTabs = openTabs,
                    onOpenAiChat = onOpenAiChat,
                    tabletLayout = false,
                    onPhoneSearchActiveChange = onPhoneSearchActiveChange,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            },
            session = {
                val url = activeUrl
                if (!url.isNullOrBlank()) {
                    BrowserSessionScreen(
                        initialUrl = url,
                        showNavigationBack = true,
                        onNavigateBack = { BrowserTabManager.clearActiveToNtp() },
                        incognito = incognito,
                        sessionKey = sessionKey,
                        initialOmniboxLabel = omniboxLabel,
                        initialTitle = initialTitle,
                        navEpoch = navEpoch,
                        onOpenBrowserSettings = onOpenBrowserSettings,
                        onOpenBookmarks = onOpenBookmarks,
                        onOpenDownloads = onOpenDownloads,
                        onOpenHistory = onOpenHistory,
                        onOpenTabs = openTabs,
                        onOpenReader = onOpenReader,
                        onOpenMangaReader = onOpenMangaReader,
                        onPageMeta = { pageUrl, title ->
                            BrowserTabManager.updateActiveMeta(pageUrl, title)
                        },
                        tabletEmbedded = false,
                    )
                }
            },
        )
        if (showTabsOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(8f)
                    .background(LegadoTheme.colorScheme.surface)
                    .clickable(enabled = false) {},
            ) {
                BrowserTabsScreen(
                    onBack = { showTabsOverlay = false },
                    onOpenActiveTab = { showTabsOverlay = false },
                )
            }
        }
    }
}
