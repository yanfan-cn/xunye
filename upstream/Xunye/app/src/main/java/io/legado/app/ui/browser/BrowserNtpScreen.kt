package io.legado.app.ui.browser

import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.theme.LocalTopBarBackdrop
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ripple
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import io.legado.app.R
import io.legado.app.browser.engine.BingWallpaperClient
import io.legado.app.browser.engine.BrowseStore
import io.legado.app.browser.engine.BrowserChromePrefs
import io.legado.app.browser.engine.NtpWallpaperIds
import io.legado.app.browser.engine.NtpWallpaperStore
import io.legado.app.browser.engine.BrowserNtpNews
import io.legado.app.browser.engine.BrowserNtpPrefs
import io.legado.app.browser.engine.BrowserNtpScrollMemory
import io.legado.app.browser.engine.BrowserClipboardUrl
import io.legado.app.browser.engine.BrowserNtpSearchRows
import io.legado.app.browser.engine.BrowserOmniboxSuggest
import io.legado.app.browser.engine.BrowserSearchEngine
import io.legado.app.browser.engine.BrowserShortcut
import io.legado.app.browser.engine.BrowserShortcutStore
import io.legado.app.browser.engine.BrowserTabManager
import io.legado.app.browser.engine.resolveBrowserNavigationInput
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.ui.book.info.ChangeCoverSheet
import io.legado.app.ui.config.themeConfig.ThemeModeSelector
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.theme.LegadoTheme
import java.io.File
import io.legado.app.ui.widget.components.icon.MaterialSymbol
import io.legado.app.ui.widget.components.icon.SymbolIcon
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.icon.Symbols
import io.legado.app.ui.widget.components.menu.AnimatedMenuPopup
import io.legado.app.ui.widget.components.menu.MenuPopupAlignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.utils.toastOnUi
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.util.UUID

private data class NtpColors(
    val pageBg: Color,
    val fieldBg: Color,
    val cardBg: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val ntpWordmark: Color,
    val cta: Color,
    val ctaText: Color,
    val divider: Color,
)

/** SPEC-203: 预置/渐变壁纸为浅色背景，logo 固定用深色（与快捷书签浅底文字色一致）。 */
private val NtpDarkWordmark = Color(0xFF1C1B19)

/** SPEC-067/161: NTP follows App ColorScheme (same as「我的」); incognito no longer overrides.
 *  SPEC-203: 浅/深外观切换时整组颜色带 400ms 过渡（原仅 pageBg 有过渡，其余瞬时跳变）。 */
@Composable
private fun rememberNtpColors(forceDark: Boolean?): NtpColors {
    val scheme = LegadoTheme.colorScheme
    val dark = when (forceDark) {
        true -> true
        false -> false
        null -> LegadoTheme.isDark || isSystemInDarkTheme()
    }
    // Prefer App scheme for both light/dark so NTP matches settings pages.
    val target = NtpColors(
        pageBg = scheme.background,
        fieldBg = scheme.surfaceContainerLow,
        cardBg = scheme.surfaceContainerLow,
        primaryText = scheme.onBackground,
        secondaryText = scheme.onSurfaceVariant,
        ntpWordmark = scheme.onBackground,
        cta = scheme.primary,
        ctaText = scheme.onPrimary,
        divider = scheme.outlineVariant.copy(alpha = if (dark) 0.40f else 0.60f),
    )
    return NtpColors(
        pageBg = animateNtpColor(target.pageBg, "ntpPageBg"),
        fieldBg = animateNtpColor(target.fieldBg, "ntpFieldBg"),
        cardBg = animateNtpColor(target.cardBg, "ntpCardBg"),
        primaryText = animateNtpColor(target.primaryText, "ntpPrimaryText"),
        secondaryText = animateNtpColor(target.secondaryText, "ntpSecondaryText"),
        ntpWordmark = animateNtpColor(target.ntpWordmark, "ntpWordmark"),
        cta = animateNtpColor(target.cta, "ntpCta"),
        ctaText = animateNtpColor(target.ctaText, "ntpCtaText"),
        divider = animateNtpColor(target.divider, "ntpDivider"),
    )
}

@Composable
private fun animateNtpColor(target: Color, label: String): Color =
    animateColorAsState(targetValue = target, animationSpec = tween(400), label = label).value

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun BrowserNtpScreen(
    incognito: Boolean,
    onIncognitoChange: (Boolean) -> Unit,
    onOpenUrl: (url: String, incognito: Boolean, omniboxLabel: String?) -> Unit,
    onOpenBook: (Book) -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onOpenTabs: () -> Unit = {},
    /** SPEC-250: NTP「AI 助手」→ 现有 AI 对话页。 */
    onOpenAiChat: () -> Unit = {},
    rootModifier: Modifier = Modifier,
    /** SPEC-018/040: tablet NTP layout (55% width, wallpaper, no corner chrome). */
    tabletLayout: Boolean = false,
    /** SPEC-177: phone fullscreen search open → hide main bottom bar. */
    onPhoneSearchActiveChange: (Boolean) -> Unit = {},
    /** SPEC-183: shared-element scopes for the reading-list card covers. */
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: BrowserNtpViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val ntpPrefs = remember { BrowserNtpPrefs(context) }
    val chromePrefs = remember { BrowserChromePrefs(context) }
    val wallpaperStore = remember { NtpWallpaperStore(context) }
    val shortcutStore = remember { BrowserShortcutStore(context) }
    val browseStore = remember { BrowseStore(context) }
    var engine by remember { mutableStateOf(ntpPrefs.searchEngine()) }
    var query by remember { mutableStateOf("") }
    var queryFocused by remember { mutableStateOf(false) }
    val fullscreenSearch = BrowserNtpSearchRows.opensFullscreen(tabletLayout)
    var phoneSearchMounted by remember { mutableStateOf(false) }
    var phoneSearchExpanded by remember { mutableStateOf(false) }
    // SPEC-224: 废除搜索/IME 玻璃抑制。
    /** SPEC-179: keep search cover while session slides in; no collapse-to-NTP flash. */
    var phoneSearchDismissAfterNav by remember { mutableStateOf(false) }
    var ntpRootTop by remember { mutableFloatStateOf(0f) }
    var ntpRootLeft by remember { mutableFloatStateOf(0f) }
    var ntpRootWidth by remember { mutableFloatStateOf(0f) }
    var searchAnchorTop by remember { mutableFloatStateOf(0f) }
    var searchAnchorLeft by remember { mutableFloatStateOf(0f) }
    var searchAnchorWidth by remember { mutableFloatStateOf(0f) }
    BackHandler(enabled = phoneSearchMounted && !phoneSearchDismissAfterNav) {
        phoneSearchExpanded = false
    }
    LaunchedEffect(phoneSearchMounted) {
        onPhoneSearchActiveChange(phoneSearchMounted)
    }
    LaunchedEffect(phoneSearchDismissAfterNav) {
        if (!phoneSearchDismissAfterNav) return@LaunchedEffect
        // SPEC-179 R2: entry into the web page is immediate; hold the search cover
        // until the session actually starts showing, then cover its Material
        // slide-in (240ms) before unmounting — no NTP flash in between.
        val start = System.currentTimeMillis()
        var sessionShowing = false
        while (System.currentTimeMillis() - start < 3000L) {
            val activeId = BrowserTabManager.activeTabId.value
            val active = BrowserTabManager.tabs.value.firstOrNull { it.id == activeId }
            if (BrowserTabManager.isWebSessionShowing(active)) {
                sessionShowing = true
                break
            }
            delay(16)
        }
        delay(if (sessionShowing) 360L else 120L)
        phoneSearchMounted = false
        phoneSearchExpanded = false
        phoneSearchDismissAfterNav = false
    }
    DisposableEffect(Unit) {
        onDispose { onPhoneSearchActiveChange(false) }
    }
    /** SPEC-146 R2: clipboard link candidate while the NTP search is focused. */
    var clipboardUrl by remember { mutableStateOf<String?>(null) }
    var shortcuts by remember { mutableStateOf(shortcutStore.list()) }
    // SPEC-152: 订阅 store 版本号，书签/收藏「添加至主页」后立即反映，无需重启。
    val shortcutVersion by BrowserShortcutStore.version.collectAsState()
    LaunchedEffect(shortcutVersion) {
        shortcuts = shortcutStore.list()
    }
    var engineMenu by remember { mutableStateOf(false) }
    var customizeMenu by remember { mutableStateOf(false) }
    var editShortcut by remember { mutableStateOf<BrowserShortcut?>(null) }
    var addingShortcut by remember { mutableStateOf(false) }
    var coverBook by remember { mutableStateOf<Book?>(null) }
    var wallpaperUrl by remember { mutableStateOf(chromePrefs.wallpaperUrl) }
    var dailyWallpaper by remember { mutableStateOf(chromePrefs.dailyWallpaperEnabled) }
    var wallpaperId by remember { mutableStateOf(wallpaperStore.selectedId) }
    var userWallpaperIds by remember { mutableStateOf(wallpaperStore.userIds()) }
    var showBookshelf by remember { mutableStateOf(ntpPrefs.showBookshelfCard) }
    var showShortcuts by remember { mutableStateOf(ntpPrefs.showShortcutGrid) }
    // SPEC-088: news feed prefs (default all on); sources toggled in card menu.
    var newsFeedEnabled by remember { mutableStateOf(ntpPrefs.newsFeedEnabled) }
    var newsSourceSina by remember { mutableStateOf(ntpPrefs.newsSourceSina) }
    var newsSourceNetEase by remember { mutableStateOf(ntpPrefs.newsSourceNetEase) }
    var newsSourceTencent by remember { mutableStateOf(ntpPrefs.newsSourceTencent) }
    var newsSourceBbcZh by remember { mutableStateOf(ntpPrefs.newsSourceBbcZh) }
    var newsSourceSspai by remember { mutableStateOf(ntpPrefs.newsSourceSspai) }
    var newsItems by remember { mutableStateOf<List<BrowserNtpNews.Item>>(emptyList()) }
    var newsLoading by remember { mutableStateOf(false) }
    var newsLoadingMore by remember { mutableStateOf(false) }
    var newsHasMore by remember { mutableStateOf(false) }
    var newsSourceMenu by remember { mutableStateOf(false) }
    val ntpScrollState = rememberScrollState()
    val newsScope = rememberCoroutineScope()
    // SPEC-192: 滚动期间抑制液态玻璃采样，静止后恢复。
    // SPEC-224: 废除 NTP 滚动玻璃抑制。
    // SPEC-098: arm before sync; remount at 0 must not wipe saved offset (web ScrollMemory pattern).
    DisposableEffect(Unit) {
        BrowserNtpScrollMemory.armRestore()
        onDispose { BrowserNtpScrollMemory.rememberLeaving(ntpScrollState.value) }
    }
    LaunchedEffect(ntpScrollState) {
        snapshotFlow { ntpScrollState.value }.collect { BrowserNtpScrollMemory.remember(it) }
    }

    // SPEC-059/067: NTP follows App ColorScheme (background + surfaceContainerLow).
    val colors = rememberNtpColors(forceDark = null)
    val books by viewModel.recentBooks.collectAsState()
    val tabCount by BrowserTabManager.tabs.collectAsStateWithLifecycle()
    val hasQuery = query.isNotBlank()
    val shortcutColumns = if (tabletLayout) 6 else 4

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        newsScope.launch {
            val id = withContext(Dispatchers.IO) { wallpaperStore.import(uri) } ?: return@launch
            wallpaperId = id
            userWallpaperIds = wallpaperStore.userIds()
        }
    }

    val qrLauncher = rememberLauncherForActivityResult(QrCodeResult()) { result ->
        val raw = result?.trim().orEmpty()
        if (raw.isEmpty()) return@rememberLauncherForActivityResult
        query = raw
        val url = resolveBrowserNavigationInput(raw, engine)
        if (url.isNotEmpty()) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            onOpenUrl(url, incognito, raw)
        }
    }

    var firstFrameDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { firstFrameDone = true }

    LaunchedEffect(dailyWallpaper, firstFrameDone) {
        if (!firstFrameDone) return@LaunchedEffect
        if (dailyWallpaper) {
            wallpaperUrl = withContext(Dispatchers.IO) {
                BingWallpaperClient.ensureToday(context, chromePrefs)
            }.orEmpty()
        } else {
            wallpaperUrl = ""
        }
    }

    fun anyNewsSource() =
        newsSourceSina || newsSourceNetEase || newsSourceTencent ||
            newsSourceBbcZh || newsSourceSspai

    fun newsFlags() = BrowserNtpNews.SourceFlags(
        sina = newsSourceSina,
        netEase = newsSourceNetEase,
        tencent = newsSourceTencent,
        bbcZh = newsSourceBbcZh,
        sspai = newsSourceSspai,
    )

    fun refreshNews(force: Boolean) {
        if (incognito || !newsFeedEnabled) return
        if (!anyNewsSource()) {
            newsItems = emptyList()
            newsLoading = false
            newsHasMore = false
            return
        }
        newsScope.launch {
            val flags = newsFlags()
            if (!force) {
                BrowserNtpNews.cached(flags)?.let {
                    newsItems = it
                    newsHasMore = BrowserNtpNews.hasMore(flags)
                    newsLoading = false
                    return@launch
                }
            }
            // SPEC-094: manual refresh always shows feedback (even when list non-empty).
            newsLoading = true
            if (force) BrowserNtpNews.invalidateCache()
            newsItems = withContext(Dispatchers.IO) {
                BrowserNtpNews.load(flags, force = force)
            }
            newsHasMore = BrowserNtpNews.hasMore(flags)
            newsLoading = false
        }
    }

    fun loadMoreNews() {
        if (incognito || !newsFeedEnabled || newsLoading || newsLoadingMore || !newsHasMore) return
        if (!anyNewsSource()) return
        newsScope.launch {
            val flags = newsFlags()
            newsLoadingMore = true
            newsItems = withContext(Dispatchers.IO) {
                BrowserNtpNews.loadMore(flags)
            }
            newsHasMore = BrowserNtpNews.hasMore(flags)
            newsLoadingMore = false
        }
    }

    // SPEC-088: load when enabled/source changes; cache avoids auto-refresh on return.
    LaunchedEffect(
        firstFrameDone,
        incognito,
        newsFeedEnabled,
        newsSourceSina,
        newsSourceNetEase,
        newsSourceTencent,
        newsSourceBbcZh,
        newsSourceSspai,
    ) {
        if (!firstFrameDone || incognito || !newsFeedEnabled) {
            newsItems = emptyList()
            newsLoading = false
            newsHasMore = false
            return@LaunchedEffect
        }
        if (!anyNewsSource()) {
            newsItems = emptyList()
            newsLoading = false
            newsHasMore = false
            return@LaunchedEffect
        }
        val flags = newsFlags()
        // SPEC-094: prefer cache (3h TTL) — returning from article must not force refresh.
        BrowserNtpNews.cached(flags)?.let {
            newsItems = it
            newsHasMore = BrowserNtpNews.hasMore(flags)
            newsLoading = false
            return@LaunchedEffect
        }
        if (newsItems.isEmpty()) newsLoading = true
        newsItems = withContext(Dispatchers.IO) {
            BrowserNtpNews.load(flags, force = false)
        }
        newsHasMore = BrowserNtpNews.hasMore(flags)
        newsLoading = false
    }

    // SPEC-098: restore like AwContentsHostView.restoreScrollY — wait for list, retry while height grows.
    LaunchedEffect(firstFrameDone, newsLoading, newsItems.size) {
        if (!firstFrameDone) return@LaunchedEffect
        if (!BrowserNtpScrollMemory.pendingRestore) return@LaunchedEffect
        val saved = BrowserNtpScrollMemory.recall()
        if (saved <= 0) {
            BrowserNtpScrollMemory.markRestored()
            return@LaunchedEffect
        }
        // Do not settle on pre-news maxValue; wait until feed rows exist when enabled.
        if (newsFeedEnabled && !incognito && anyNewsSource() && newsItems.isEmpty()) {
            return@LaunchedEffect
        }
        repeat(40) { attempt ->
            if (!BrowserNtpScrollMemory.pendingRestore) return@LaunchedEffect
            val max = ntpScrollState.maxValue
            if (max > 0) {
                ntpScrollState.scrollTo(saved.coerceAtMost(max))
                if (ntpScrollState.value >= saved) {
                    BrowserNtpScrollMemory.markRestored()
                    return@LaunchedEffect
                }
            }
            delay(if (attempt < 2) 16L else 50L)
        }
        if (ntpScrollState.maxValue > 0) {
            ntpScrollState.scrollTo(saved.coerceAtMost(ntpScrollState.maxValue))
        }
        BrowserNtpScrollMemory.markRestored()
    }

    // SPEC-094: near bottom → append next page.
    LaunchedEffect(ntpScrollState, newsHasMore, newsLoadingMore, newsItems.size) {
        snapshotFlow { ntpScrollState.value to ntpScrollState.maxValue }
            .collect { (value, max) ->
                if (!newsHasMore || newsLoadingMore || newsLoading) return@collect
                if (max <= 0) return@collect
                if (value >= max - 1200) {
                    loadMoreNews()
                }
            }
    }

    fun submit() {
        val raw = query.trim()
        val url = resolveBrowserNavigationInput(raw, engine)
        if (url.isEmpty()) return
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        queryFocused = false
        onOpenUrl(url, incognito, raw)
    }

    fun openPhoneSearch() {
        if (!fullscreenSearch) return
        phoneSearchMounted = true
        phoneSearchExpanded = true
    }

    fun openSuggest(item: BrowserOmniboxSuggest.Item) {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        queryFocused = false
        query = item.title.ifBlank { item.url }
        onOpenUrl(item.url, incognito, item.title.ifBlank { item.url })
    }

    /** SPEC-146 R2: open the clipboard link candidate. */
    fun openClipboardLink(url: String) {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        queryFocused = false
        clipboardUrl = null
        query = url
        onOpenUrl(url, incognito, url)
    }

    val querySuggestions = remember(query, queryFocused) {
        if (!queryFocused || query.isBlank()) {
            emptyList()
        } else {
            BrowserOmniboxSuggest.match(query, browseStore.bookmarks(), browseStore.history())
        }
    }

    // SPEC-161/203: incognito keeps the same page appearance; pageBg itself now animates
    // inside rememberNtpColors (whole-palette 400ms transition on light/dark switch).
    val pageBg = colors.pageBg
    // SPEC-059/161: follow app theme only (incognito no longer forces dark).
    val ntpDark = LegadoTheme.isDark || isSystemInDarkTheme()
    // SPEC-158/161: wallpaper stays visible in incognito.
    val dailyWallpaperVisible = wallpaperUrl.isNotBlank() && dailyWallpaper
    val presetWallpaperVisible = !dailyWallpaper
    val userWallpaperVisible = presetWallpaperVisible && !NtpWallpaperIds.isBuiltin(wallpaperId)
    // SPEC-239: dark image presets (赛博/霓虹) need light labels even in light theme.
    val darkPresetVisible = presetWallpaperVisible && NtpWallpaperIds.isDarkPreset(wallpaperId)
    val wallpaperScrim = when {
        dailyWallpaperVisible -> 0.35f
        userWallpaperVisible && ntpDark -> 0.40f
        darkPresetVisible && ntpDark -> 0.28f
        darkPresetVisible -> 0.22f
        ntpDark && presetWallpaperVisible -> 0.28f
        userWallpaperVisible -> 0.32f
        else -> 0f
    }
    val shortcutLightText = ntpDark || wallpaperScrim > 0f || darkPresetVisible
    // SPEC-203: 快捷书签文字色随浅/深过渡，避免瞬时跳变。
    val shortcutLabelColor by animateColorAsState(
        targetValue = if (shortcutLightText) Color.White else Color(0xFF1C1B19),
        animationSpec = tween(400),
        label = "ntpShortcutLabelColor",
    )
    // SPEC-129: dark wallpaper / dark NTP → light status-bar icons (readable on dark).
    // SPEC-147 R12: 改走 StatusBarAppearance 仲裁器——nav3 重锚页面时 effect 会在
    // 同一帧销毁重建，仲裁器延迟一帧恢复默认，可被重新登记取消，避免图标翻转。
    // SPEC-158: light built-in presets keep dark icons; scrim (Bing, photo, dark theme) uses light icons.
    val appIsDark = LegadoTheme.isDark
    val lightStatusIcons = ntpDark || wallpaperScrim > 0f
    DisposableEffect(lightStatusIcons, appIsDark) {
        io.legado.app.base.StatusBarAppearance.request("ntp", lightStatusIcons)
        onDispose {
            io.legado.app.base.StatusBarAppearance.release("ntp")
        }
    }
    Box(
        modifier = rootModifier
            .fillMaxSize()
            .background(pageBg)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                ntpRootTop = pos.y
                ntpRootLeft = pos.x
                ntpRootWidth = coords.size.width.toFloat()
            },
    ) {
        // SPEC-049: sample source must NOT contain glass descendants — nested
        // layerBackdrop + browserControlLiquidGlass caused RenderThread SIGSEGV.
        val ntpBackdrop = rememberLayerBackdrop()
        val sampleWallpaper = firstFrameDone && (dailyWallpaperVisible || presetWallpaperVisible)
        CompositionLocalProvider(
            LocalTopBarBackdrop provides if (sampleWallpaper) ntpBackdrop else null,
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (sampleWallpaper) Modifier.layerBackdrop(ntpBackdrop) else Modifier)
                .background(pageBg),
        ) {
                AnimatedVisibility(
                    visible = dailyWallpaperVisible || presetWallpaperVisible,
                    enter = fadeIn(animationSpec = tween(400)),
                    exit = fadeOut(animationSpec = tween(400)),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        if (dailyWallpaperVisible) {
                            SubcomposeAsyncImage(
                                model = wallpaperUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            NtpWallpaperLayer(
                                wallpaperId = wallpaperId,
                                userFile = wallpaperStore.fileFor(wallpaperId),
                            )
                        }
                        if (wallpaperScrim > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = wallpaperScrim)),
                            )
                        }
                    }
                }
        }

        // SPEC-160: top gradient translucent mask (no blur) for chrome readability.
        // SPEC-203: tablet NTP skips the top scrim; phone keeps it.
        if (!tabletLayout) {
            val scrimBase = if (LegadoTheme.isDark) {
                BrowserUiTokens.PageBgDark
            } else {
                BrowserUiTokens.PageBgLight
            }
            val scrimEdge = BrowserUiTokens.ntpScrimEdgeAlpha(isDark = LegadoTheme.isDark)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1.5f)
                    .fillMaxWidth()
                    .height(BrowserUiTokens.NtpScrimHeightTop)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                scrimBase.copy(alpha = scrimEdge),
                                scrimBase.copy(alpha = 0f),
                            ),
                        ),
                    ),
            )
        }

        // SPEC-049: compact 54dp chrome + glass customize panel (sibling of sample source)
        val chromeTint = colors.primaryText
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(2f)
                .then(
                    if (tabletLayout) {
                        // SPEC-168: no full-width blank band. The menu sits on its own.
                        Modifier.padding(start = BrowserUiTokens.PagePaddingH, top = 8.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(BrowserUiTokens.NtpTopBarHeight)
                            .padding(horizontal = BrowserUiTokens.PagePaddingH)
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BrowserUiTokens.SpaceMd),
        ) {
            // SPEC-069/071: top chrome uses search-field tone; liquid glass → low alpha.
            val glassFill = ntpGlassOverlayAlpha()
            val chromeBtnBg = colors.fieldBg.copy(alpha = glassFill)
            Box {
                NtpGlassIcon(
                    icon = Symbols.Tune,
                    desc = "主页自定义",
                    tint = chromeTint,
                    containerColor = chromeBtnBg,
                    onClick = { customizeMenu = true },
                )
                if (customizeMenu) {
                    NtpCustomizePanel(
                        dailyWallpaper = dailyWallpaper,
                        showBookshelf = showBookshelf,
                        showShortcuts = showShortcuts,
                        newsFeedEnabled = newsFeedEnabled,
                        wallpaperId = wallpaperId,
                        userWallpaperIds = userWallpaperIds,
                        userFile = wallpaperStore::fileFor,
                        textColor = chromeTint,
                        onDismiss = { customizeMenu = false },
                        onDailyWallpaper = {
                            dailyWallpaper = it
                            chromePrefs.dailyWallpaperEnabled = it
                        },
                        onSelectWallpaper = { id ->
                            wallpaperStore.selectedId = id
                            wallpaperId = id
                        },
                        onAddWallpaper = {
                            wallpaperPicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        onDeleteWallpaper = { id ->
                            wallpaperStore.delete(id)
                            wallpaperId = wallpaperStore.selectedId
                            userWallpaperIds = wallpaperStore.userIds()
                        },
                        onShowBookshelf = {
                            showBookshelf = it
                            ntpPrefs.showBookshelfCard = it
                        },
                        onShowShortcuts = {
                            showShortcuts = it
                            ntpPrefs.showShortcutGrid = it
                        },
                        onNewsFeedEnabled = {
                            newsFeedEnabled = it
                            ntpPrefs.newsFeedEnabled = it
                        },
                    )
                }
            }
            if (!tabletLayout) {
            Spacer(modifier = Modifier.weight(1f))
                NtpGlassIcon(
                    icon = Symbols.History,
                    desc = "历史记录",
                    tint = chromeTint,
                    containerColor = chromeBtnBg,
                    onClick = onOpenHistory,
                )
                NtpGlassIcon(
                    icon = Symbols.Bookmark,
                    desc = "收藏",
                    tint = chromeTint,
                    containerColor = chromeBtnBg,
                    onClick = onOpenBookmarks,
                )
                BrowserTabCountButton(
                    count = tabCount.size,
                    tint = chromeTint,
                    useGlass = true,
                    containerColor = chromeBtnBg,
                    menuBelowTopBar = true,
                    onOpenTabs = onOpenTabs,
                    onNewTab = { BrowserTabManager.openNewTab(url = null, select = true) },
                    onCloseAll = { BrowserTabManager.closeAll() },
                )
            }
            }

        // SPEC-126: tablet — full-width scroll so left/right blank areas also scroll;
        // content column stays 75%/55% centered; no large bottom placeholder.
        val orientation = LocalConfiguration.current.orientation
        val tabletPortrait = orientation ==
            android.content.res.Configuration.ORIENTATION_PORTRAIT
        val phoneLandscape = !tabletLayout &&
            orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .then(
                    if (tabletLayout) {
                        Modifier
                    } else {
                        // SPEC-140-R5: horizontal/bottom insets stay fixed on the
                        // scroll container; the TOP shift moved INSIDE the scroll
                        // (spacer below) so scrolled content tucks under the glass
                        // chrome row — no permanent blank band while scrolling.
                        Modifier
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                ),
                            )
                            .padding(horizontal = BrowserUiTokens.PagePaddingH)
                    },
                )
                .verticalScroll(ntpScrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (tabletLayout) {
                // SPEC-170: shift NTP body 90dp; home menu stays outside this scroll.
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(TabletBrowserLayout.TabletNtpContentShiftDownDp.dp),
                )
            } else {
                // SPEC-140-R5: scrollable top spacing — at rest the search capsule
                // sits NtpContentShiftDown below the status bar (135dp below the
                // chrome row per operator directive) and scrolls away with the
                // content instead of clipping at a fixed viewport top.
                val topInset = WindowInsets.safeDrawing
                    .only(WindowInsetsSides.Top)
                    .asPaddingValues()
                    .calculateTopPadding()
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            topInset + if (phoneLandscape) {
                                0.dp
                            } else {
                                BrowserUiTokens.NtpContentShiftDown
                            },
                        ),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth(
                        if (tabletLayout) {
                            if (tabletPortrait) 0.75f else 0.55f
                        } else {
                            TabletBrowserLayout.phoneNtpContentWidthFraction(phoneLandscape)
                        },
                    )
                    .padding(bottom = if (tabletLayout) 16.dp else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            // SPEC-161/170: hide wordmark on daily wallpaper; phone landscape always hides.
            val showLogo = if (tabletLayout) {
                !dailyWallpaper
            } else {
                TabletBrowserLayout.phoneNtpShowWordmarkLogo(
                    landscape = phoneLandscape,
                    dailyWallpaper = dailyWallpaper,
                )
            }
            // SPEC-203/239: logo 深浅按背景判断，不随外观主题——浅色预置/渐变固定深色
            // logo；深色预置（赛博/霓虹）用浅色；自定义图片沿用主题色；无壁纸随页面背景。
            val logoTargetColor = when {
                darkPresetVisible -> Color.White
                userWallpaperVisible -> colors.ntpWordmark
                presetWallpaperVisible -> NtpDarkWordmark
                else -> colors.ntpWordmark
            }
            val logoColor by animateColorAsState(
                targetValue = logoTargetColor,
                animationSpec = tween(400),
                label = "ntpLogoColor",
            )
            if (showLogo) {
                Image(
                    painter = painterResource(R.drawable.ic_ntp_logo),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(logoColor),
                    modifier = Modifier.height(44.dp),
                )
                // SPEC-060: Logo ↔ search spacing 24dp on tablet; phone keeps 16dp
                Spacer(modifier = Modifier.height(if (tabletLayout) 24.dp else 16.dp))
            }

            // SPEC-049/161: dual-row capsule — same colors in incognito.
            val fieldBg = colors.fieldBg
            val textColor = colors.primaryText
            val secondary = colors.secondaryText
            val capsuleShape = RoundedCornerShape(28.dp)
            // SPEC-072: liquid glass 0.4/0.35; filled = 1.
            val capsuleFill = ntpGlassOverlayAlpha()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        if (!phoneSearchMounted) {
                            val pos = coords.positionInWindow()
                            searchAnchorTop = pos.y - ntpRootTop
                            searchAnchorLeft = pos.x - ntpRootLeft
                            searchAnchorWidth = coords.size.width.toFloat()
                        }
                    }
                    .clip(capsuleShape)
                    .browserControlLiquidGlass(capsuleShape, interactive = false, showShadow = false)
                    .background(fieldBg.copy(alpha = capsuleFill)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { engineMenu = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            // SPEC-069: circular engine logo, crop to fill.
                            Image(
                                painter = painterResource(engine.logoRes),
                                contentDescription = engine.displayName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
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
                            textColor = textColor,
                            mutedColor = secondary,
                            verticalOffset = 40.dp,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    if (fullscreenSearch) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                // SPEC-199: 48dp 高热区涟漪随胶囊（父容器只裁两端）。
                                .clip(RoundedCornerShape(24.dp))
                                .clickable { openPhoneSearch() },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                "在 ${engine.displayName} 中搜索或输入网址",
                                color = secondary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { fs ->
                                val wasFocused = queryFocused
                                queryFocused = fs.isFocused
                                if (!wasFocused && fs.isFocused) {
                                    // SPEC-146 R2: read clipboard only on focus gained.
                                    clipboardUrl = BrowserClipboardUrl.latest(context)
                                } else if (wasFocused && !fs.isFocused) {
                                    clipboardUrl = null
                                }
                            },
                        singleLine = true,
                        textStyle = TextStyle(color = textColor, fontSize = 16.sp),
                        cursorBrush = SolidColor(colors.cta),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    "在 ${engine.displayName} 中搜索或输入网址",
                                    color = secondary,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        },
                    )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NtpActionChip(
                            label = stringResource(R.string.ai_chat),
                            icon = {
                                SymbolIcon(
                                    Symbols.AutoAwesome,
                                    null,
                                    tint = secondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            fg = textColor,
                            bg = Color.Transparent,
                            onClick = onOpenAiChat,
                        )
                        NtpActionChip(
                            label = if (incognito) "退出无痕" else "无痕浏览",
                            icon = {
                                SymbolIcon(
                                    Symbols.VisibilityOff,
                                    null,
                                    tint = if (incognito) Color.White else secondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            fg = if (incognito) Color.White else textColor,
                            bg = if (incognito) BrowserUiTokens.Primary else Color.Transparent,
                            onClick = {
                                if (incognito) {
                                    BrowserNtpPrefs.clearIncognitoData()
                                    onIncognitoChange(false)
                                    context.toastOnUi("已退出无痕浏览")
                                } else {
                                    onIncognitoChange(true)
                                    context.toastOnUi("已进入无痕浏览")
                                }
                            },
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NtpGlassIcon(
                            icon = Symbols.QrCodeScanner,
                            desc = "扫码",
                            tint = secondary,
                            onClick = { qrLauncher.launch(null) },
                        )
                        NtpGlassIcon(
                            icon = Symbols.Search,
                            desc = "搜索",
                            tint = if (hasQuery) colors.cta else secondary,
                            filled = hasQuery,
                            enabled = hasQuery,
                            onClick = { if (hasQuery) submit() },
                        )
                    }
                }
            }

            if (!fullscreenSearch && (querySuggestions.isNotEmpty() || clipboardUrl != null)) {
                Spacer(modifier = Modifier.height(10.dp))
                BrowserOmniboxSuggestPanel(
                    items = querySuggestions,
                    onSelect = { openSuggest(it) },
                    clipboardUrl = clipboardUrl,
                    onClipboardSelect = { openClipboardLink(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (showShortcuts) {
                Spacer(modifier = Modifier.height(28.dp))
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (tabletLayout) {
                                BrowserUiTokens.NtpShortcutScreenInset
                            } else {
                                (BrowserUiTokens.NtpShortcutScreenInset - BrowserUiTokens.PagePaddingH)
                                    .coerceAtLeast(0.dp)
                            },
                        ),
                ) {
                    val iconSize = BrowserUiTokens.NtpShortcutIconSize
                    val rowGap = if (shortcutColumns > 1) {
                        // SPEC-152: 扣 2dp 余量——恰好填满时 dp 取整的亚像素溢出
                        // 会让最后一个图标被挤到下一行（一行六个最右空一格）。
                        (((maxWidth - iconSize * shortcutColumns) / (shortcutColumns - 1)) - 2.dp)
                            .coerceAtLeast(0.dp)
                    } else {
                        0.dp
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = shortcutColumns,
                        horizontalArrangement = Arrangement.spacedBy(rowGap),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        shortcuts.forEach { item ->
                            ShortcutChip(
                                shortcut = item,
                                labelColor = shortcutLabelColor,
                                lightText = shortcutLightText,
                                darkBubble = ntpDark,
                                cellWidth = iconSize,
                                loadFavicon = firstFrameDone,
                                onOpen = { onOpenUrl(item.url, incognito, null) },
                                onEdit = { editShortcut = item },
                            )
                        }
                        AddShortcutChip(
                            labelColor = shortcutLabelColor,
                            lightText = shortcutLightText,
                            darkBubble = ntpDark,
                            cellWidth = iconSize,
                            onClick = { addingShortcut = true },
                        )
                    }
                }
            }

            if (showBookshelf) {
                Spacer(modifier = Modifier.height(BrowserUiTokens.SpaceXl))
                val shelfFill = ntpGlassOverlayAlpha()
                val shelfBg = colors.fieldBg.copy(alpha = shelfFill)
                val shelfShape = RoundedCornerShape(BrowserUiTokens.CardCorner)
                // SPEC-189 R3: glass and clip stay on the background sibling. A
                // drawBackdrop layer around the covers rasterizes them, so the
                // shared-element overlay cannot fly the cover to book info.
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(shelfShape)
                            .background(shelfBg, shelfShape)
                            .browserControlLiquidGlass(shelfShape, interactive = false),
                    )
                    Column(modifier = Modifier.padding(BrowserUiTokens.SpaceLg)) {
                        Text(
                            "阅读列表",
                            color = colors.primaryText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = BrowserUiTokens.SpaceMd),
                            textAlign = TextAlign.Start,
                        )
                        if (books.isEmpty()) {
                            Text(
                                "阅读列表暂无内容，可在「阅读列表」页添加",
                                color = colors.secondaryText,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = BrowserUiTokens.SpaceXl),
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            LazyRow(
                                // SPEC-189 R3: eat horizontal remainder and fling after the
                                // row has scrolled. Do not disable the outer pager.
                                modifier = Modifier.nestedScroll(NtpShelfScroll),
                                horizontalArrangement = Arrangement.spacedBy(BrowserUiTokens.SpaceMd),
                                contentPadding = PaddingValues(horizontal = BrowserUiTokens.SpaceXs),
                            ) {
                                items(books, key = { it.bookUrl }) { book ->
                                    // SPEC-199/203: 封面格涟漪 16dp 圆角改画在覆盖层——
                                    // 整格 clip 会叠在封面自身 12dp 圆角上，把顶角裁成
                                    // 16dp、底角仍是 12dp（上大下小）。覆盖层只裁涟漪，
                                    // 封面恢复统一圆角。
                                    val interaction = remember { MutableInteractionSource() }
                                    Box(modifier = Modifier.width(72.dp)) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.combinedClickable(
                                                interactionSource = interaction,
                                                indication = null,
                                                onClick = { onOpenBook(book) },
                                                onLongClick = { coverBook = book },
                                            ),
                                        ) {
                                        // SPEC-160: CoilBookCover renders color/gradient covers too.
                                        CoilBookCover(
                                            name = book.name,
                                            author = book.author,
                                            path = book.getDisplayCover(),
                                            radius = BrowserUiTokens.ButtonCorner,
                                            modifier = Modifier.size(width = 72.dp, height = 96.dp),
                                            bookUrl = book.bookUrl,
                                            preferCache = true,
                                            ignoreUseDefaultCover = true,
                                            // SPEC-183: same key the book-info route falls back to,
                                            // so the cover morphs into the detail page.
                                            sharedCoverKey = bookCoverSharedElementKey(book.bookUrl),
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                        )
                                        Text(
                                            book.name,
                                            color = colors.primaryText,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                        }
                                        // SPEC-203: 涟漪覆盖层（16dp 圆角，不裁内容）。
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .clip(RoundedCornerShape(16.dp))
                                                .indication(interaction, ripple(bounded = true)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SPEC-084/087: optional news feed (hidden in incognito).
            // SPEC-219: same sibling glass stack as 阅读列表 — Surface+glass on Android 17
            // liquid glass painted a solid gray slab.
            if (!incognito && newsFeedEnabled) {
                Spacer(modifier = Modifier.height(BrowserUiTokens.SpaceXl))
                val newsFill = ntpGlassOverlayAlpha()
                val newsBg = colors.fieldBg.copy(alpha = newsFill)
                val newsShape = RoundedCornerShape(BrowserUiTokens.CardCorner)
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(newsShape)
                            .background(newsBg, newsShape)
                            .browserControlLiquidGlass(newsShape, interactive = false),
                    )
                    Column(modifier = Modifier.padding(BrowserUiTokens.SpaceLg)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = BrowserUiTokens.SpaceMd),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "资讯",
                                color = colors.primaryText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { newsSourceMenu = true },
                                modifier = Modifier.size(BrowserUiTokens.ControlMedium),
                            ) {
                                SymbolIcon(
                                    Symbols.FilterList,
                                    contentDescription = "资讯来源",
                                    tint = colors.primaryText,
                                    size = 22.dp,
                                )
                            }
                            IconButton(
                                onClick = { refreshNews(force = true) },
                                enabled = !newsLoading,
                                modifier = Modifier.size(BrowserUiTokens.ControlMedium),
                            ) {
                                SymbolIcon(
                                    Symbols.Refresh,
                                    contentDescription = "刷新资讯",
                                    tint = colors.primaryText,
                                    size = 22.dp,
                                )
                            }
                        }
                        when {
                            newsLoading && newsItems.isEmpty() -> {
                                Text(
                                    "正在加载…",
                                    color = colors.secondaryText,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = BrowserUiTokens.SpaceLg),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            newsLoading && newsItems.isNotEmpty() -> {
                                Text(
                                    "正在刷新…",
                                    color = colors.secondaryText,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = BrowserUiTokens.SpaceSm),
                                    textAlign = TextAlign.Center,
                                )
                                newsItems.forEachIndexed { index, item ->
                                    if (index > 0) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    NtpNewsRow(
                                        item = item,
                                        primaryText = colors.primaryText,
                                        secondaryText = colors.secondaryText,
                                        placeholderBg = colors.fieldBg,
                                        onOpen = { onOpenUrl(item.url, false, item.title) },
                                    )
                                }
                            }
                            newsItems.isEmpty() -> {
                                Text(
                                    "暂无资讯，请检查网络或源开关",
                                    color = colors.secondaryText,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = BrowserUiTokens.SpaceLg),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            else -> {
                                newsItems.forEachIndexed { index, item ->
                                    if (index > 0) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    NtpNewsRow(
                                        item = item,
                                        primaryText = colors.primaryText,
                                        secondaryText = colors.secondaryText,
                                        placeholderBg = colors.fieldBg,
                                        onOpen = { onOpenUrl(item.url, false, item.title) },
                                    )
                                }
                                Text(
                                    when {
                                        newsLoadingMore -> "正在加载更多…"
                                        newsHasMore -> "上滑加载更多"
                                        else -> "没有更多了"
                                    },
                                    color = colors.secondaryText,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = BrowserUiTokens.SpaceMd),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
            } // inner content width column (SPEC-126)
        }

        if (newsSourceMenu && !incognito) {
            NtpNewsSourceMenu(
                sina = newsSourceSina,
                netEase = newsSourceNetEase,
                tencent = newsSourceTencent,
                bbcZh = newsSourceBbcZh,
                sspai = newsSourceSspai,
                textColor = colors.primaryText,
                onDismiss = { newsSourceMenu = false },
                onSina = {
                    newsSourceSina = it
                    ntpPrefs.newsSourceSina = it
                },
                onNetEase = {
                    newsSourceNetEase = it
                    ntpPrefs.newsSourceNetEase = it
                },
                onTencent = {
                    newsSourceTencent = it
                    ntpPrefs.newsSourceTencent = it
                },
                onBbcZh = {
                    newsSourceBbcZh = it
                    ntpPrefs.newsSourceBbcZh = it
                },
                onSspai = {
                    newsSourceSspai = it
                    ntpPrefs.newsSourceSspai = it
                },
            )
        }

    val editing = editShortcut
    if (editing != null || addingShortcut) {
        var title by remember(editing, addingShortcut) {
            mutableStateOf(editing?.title.orEmpty())
        }
        var url by remember(editing, addingShortcut) {
            mutableStateOf(editing?.url.orEmpty())
        }
        AlertDialog(
            onDismissRequest = {
                editShortcut = null
                addingShortcut = false
            },
            title = { Text(if (editing != null) "编辑快捷方式" else "添加快捷方式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("名称（可留空，自动用域名）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("网址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleanUrl = when {
                            url.startsWith("http://") || url.startsWith("https://") -> url.trim()
                            url.isNotBlank() -> "https://${url.trim()}"
                            else -> ""
                        }
                        if (cleanUrl.isEmpty()) {
                            context.toastOnUi("请输入网址")
                            return@TextButton
                        }
                        val hostLabel = try {
                            Uri.parse(cleanUrl).host
                                ?.removePrefix("www.")
                                .orEmpty()
                        } catch (_: Throwable) {
                            ""
                        }
                        val item = BrowserShortcut(
                            id = editing?.id ?: UUID.randomUUID().toString(),
                            title = title.ifBlank { hostLabel.ifBlank { cleanUrl } },
                            url = cleanUrl,
                        )
                        shortcutStore.upsert(item)
                        shortcuts = shortcutStore.list()
                        editShortcut = null
                        addingShortcut = false
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                Row {
                    if (editing != null) {
                        TextButton(
                            onClick = {
                                shortcutStore.remove(editing.id)
                                shortcuts = shortcutStore.list()
                                editShortcut = null
                            },
                        ) { Text("删除") }
                    }
                    TextButton(
                        onClick = {
                            editShortcut = null
                            addingShortcut = false
                        },
                    ) { Text("取消") }
                }
            },
        )
    }

    val pendingCover = coverBook
    ChangeCoverSheet(
        show = pendingCover != null,
        name = pendingCover?.name.orEmpty(),
        author = pendingCover?.author.orEmpty(),
        webCoverUrl = pendingCover?.coverUrl,
        onDismissRequest = { coverBook = null },
        onSelect = { coverUrl ->
            pendingCover?.let { viewModel.updateCustomCover(it.bookUrl, coverUrl) }
            coverBook = null
            context.toastOnUi("封面已更新")
        },
    )
        if (phoneSearchMounted) {
            PhoneNtpSearchOverlay(
                expanded = phoneSearchExpanded,
                anchorTopPx = searchAnchorTop,
                anchorLeftPx = searchAnchorLeft,
                anchorWidthPx = searchAnchorWidth,
                rootWidthPx = ntpRootWidth,
                incognito = incognito,
                engine = engine,
                onEngineChange = {
                    engine = it
                    ntpPrefs.searchEngineId = it.id
                },
                browseStore = browseStore,
                pageBg = colors.pageBg,
                fieldBg = colors.fieldBg,
                cardBg = colors.cardBg,
                textColor = colors.primaryText,
                secondary = colors.secondaryText,
                cursor = colors.cta,
                onNavigate = { url, label ->
                    keyboardController?.hide()
                    // SPEC-179: open session first; keep overlay covering NTP during slide-in.
                    onOpenUrl(url, incognito, label)
                    phoneSearchDismissAfterNav = true
                },
                onExited = {
                    if (phoneSearchDismissAfterNav) return@PhoneNtpSearchOverlay
                    phoneSearchMounted = false
                    phoneSearchExpanded = false
                },
            )
        }
        } // CompositionLocalProvider
    } // root Box
}

@Composable
private fun NtpGlassIcon(
    icon: MaterialSymbol,
    desc: String,
    tint: Color,
    filled: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(BrowserUiTokens.ControlMedium)
            .browserControlLiquidGlass(
                CircleShape,
                interactive = false,
                showShadow = false,
                enableLens = false,
            )
            .clip(CircleShape)
            .then(
                if (containerColor != Color.Transparent) {
                    Modifier.background(containerColor)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SymbolIcon(
            icon,
            contentDescription = desc,
            tint = tint.copy(alpha = if (enabled) 1f else 0.45f),
            filled = filled,
            size = 22.dp,
        )
    }
}

@Composable
private fun NtpNewsRow(
    item: BrowserNtpNews.Item,
    primaryText: Color,
    secondaryText: Color,
    placeholderBg: Color,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                color = primaryText,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                item.source.label,
                color = secondaryText,
                fontSize = 12.sp,
            )
        }
        val image = item.imageUrl
        if (!image.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(BrowserUiTokens.SpaceMd))
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(image)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 84.dp, height = 56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(placeholderBg),
            )
        }
    }
}

@Composable
private fun NtpNewsSourceMenu(
    sina: Boolean,
    netEase: Boolean,
    tencent: Boolean,
    bbcZh: Boolean,
    sspai: Boolean,
    textColor: Color,
    onDismiss: () -> Unit,
    onSina: (Boolean) -> Unit,
    onNetEase: (Boolean) -> Unit,
    onTencent: (Boolean) -> Unit,
    onBbcZh: (Boolean) -> Unit,
    onSspai: (Boolean) -> Unit,
) {
    // SPEC-094: anchor above floating bottom bar (same liquid glass as home customize menu).
    val density = LocalDensity.current
    val bottomLift = with(density) {
        (
            BrowserUiTokens.BottomBarHeight +
                BrowserUiTokens.FloatingChromeBottomGap +
                BrowserUiTokens.FloatingChromeTopGap +
                16.dp
            ).roundToPx()
    }
    AnimatedMenuPopup(
        alignment = MenuPopupAlignment.AboveEnd,
        offset = IntOffset(x = with(density) { (-16).dp.roundToPx() }, y = -bottomLift),
        onDismissRequest = onDismiss,
    ) {
        val shape = RoundedCornerShape(BrowserUiTokens.CardCorner)
        Column(
            modifier = Modifier
                .width(220.dp)
                .clip(shape)
                .browserControlLiquidGlass(
                    shape,
                    interactive = false,
                    readableOverlay = true,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("来源", color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            NtpCustomizeRow("新浪", sina, textColor, onSina)
            NtpCustomizeRow("网易", netEase, textColor, onNetEase)
            NtpCustomizeRow("腾讯", tencent, textColor, onTencent)
            NtpCustomizeRow("BBC中文", bbcZh, textColor, onBbcZh)
            NtpCustomizeRow("少数派", sspai, textColor, onSspai)
        }
    }
}

@Composable
private fun NtpWallpaperLayer(
    wallpaperId: String,
    userFile: File?,
) {
    val drawable = ntpBuiltinDrawable(wallpaperId)
    when {
        drawable != null -> Image(
            painter = painterResource(drawable),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        userFile != null && userFile.isFile -> AsyncImage(
            model = userFile,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        else -> Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(ntpGradientTop(wallpaperId), Color.White)),
                ),
        )
    }
}

@Composable
private fun NtpWallpaperThumb(
    selected: Boolean,
    borderColor: Color,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(Modifier.size(width = 56.dp, height = 56.dp)) {
        Box(
            Modifier
                .size(52.dp)
                .align(Alignment.BottomStart)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick),
        ) {
            content()
            Box(
                Modifier
                    .fillMaxSize()
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) borderColor else borderColor.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(10.dp),
                    ),
            )
        }
        if (onDelete != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

private fun ntpBuiltinDrawable(id: String): Int? = when (id) {
    NtpWallpaperIds.MORNING -> R.drawable.ntp_wall_morning
    NtpWallpaperIds.MIST -> R.drawable.ntp_wall_mist
    NtpWallpaperIds.LINEN -> R.drawable.ntp_wall_linen
    NtpWallpaperIds.CYBER -> R.drawable.ntp_wall_cyber
    NtpWallpaperIds.NEON -> R.drawable.ntp_wall_neon
    else -> null
}

private fun ntpGradientTop(id: String): Color = when (id) {
    NtpWallpaperIds.GRAD_GREEN -> Color(0xFFC9E6D2)
    NtpWallpaperIds.GRAD_PINK -> Color(0xFFF6D5DE)
    else -> Color(0xFFB7D6F2)
}

@Composable
private fun NtpCustomizePanel(
    dailyWallpaper: Boolean,
    showBookshelf: Boolean,
    showShortcuts: Boolean,
    newsFeedEnabled: Boolean,
    wallpaperId: String,
    userWallpaperIds: List<String>,
    userFile: (String) -> File,
    textColor: Color,
    onDismiss: () -> Unit,
    onDailyWallpaper: (Boolean) -> Unit,
    onSelectWallpaper: (String) -> Unit,
    onAddWallpaper: () -> Unit,
    onDeleteWallpaper: (String) -> Unit,
    onShowBookshelf: (Boolean) -> Unit,
    onShowShortcuts: (Boolean) -> Unit,
    onNewsFeedEnabled: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val appShellGateway: AppShellSettingsGateway = koinInject()
    val appShell by appShellGateway.settings.collectAsStateWithLifecycle(
        initialValue = appShellGateway.currentSettings,
    )
    AnimatedMenuPopup(
        alignment = MenuPopupAlignment.BelowStart,
        offset = IntOffset(
            x = 0,
            y = with(density) {
                (
                    (BrowserUiTokens.NtpTopBarHeight - BrowserUiTokens.ControlMedium) / 2 +
                        BrowserUiTokens.ControlMedium +
                        8.dp
                    ).roundToPx()
            },
        ),
        onDismissRequest = onDismiss,
    ) {
        val shape = RoundedCornerShape(BrowserUiTokens.CardCorner)
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(shape)
                .browserControlLiquidGlass(
                    shape,
                    interactive = false,
                    readableOverlay = true,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("外观", color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            // SPEC-060: follow / light / dark → AppShell themeMode
            ThemeModeSelector(
                selectedMode = appShell.themeMode,
                onModeSelected = { mode ->
                    scope.launch {
                        appShellGateway.update { it.copy(themeMode = mode) }
                    }
                },
                iconsOnly = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("主页", color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            NtpCustomizeRow("每日壁纸", dailyWallpaper, textColor, onDailyWallpaper)
            // SPEC-239: drop the "背景" section title; keep thumbs + label.
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NtpWallpaperIds.builtins.forEach { id ->
                    NtpWallpaperThumb(
                        selected = wallpaperId == id,
                        borderColor = textColor,
                        onClick = { onSelectWallpaper(id) },
                    ) {
                        NtpWallpaperLayer(wallpaperId = id, userFile = null)
                    }
                }
                userWallpaperIds.forEach { id ->
                    NtpWallpaperThumb(
                        selected = wallpaperId == id,
                        borderColor = textColor,
                        onClick = { onSelectWallpaper(id) },
                        onDelete = { onDeleteWallpaper(id) },
                    ) {
                        AsyncImage(
                            model = userFile(id),
                            contentDescription = "相册",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Box(
                    modifier = Modifier.height(56.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(textColor.copy(alpha = 0.08f))
                            .clickable(onClick = onAddWallpaper),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+", color = textColor, fontSize = 22.sp)
                    }
                }
            }
            Text(
                NtpWallpaperIds.label(wallpaperId),
                color = textColor.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
                            NtpCustomizeRow("阅读列表卡", showBookshelf, textColor, onShowBookshelf)
            NtpCustomizeRow("快捷书签", showShortcuts, textColor, onShowShortcuts)
            // SPEC-087: master switch only; per-source toggles live in the news card.
            NtpCustomizeRow("资讯信息流", newsFeedEnabled, textColor, onNewsFeedEnabled)
        }
    }
}

@Composable
private fun NtpCustomizeRow(
    title: String,
    checked: Boolean,
    textColor: Color,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = textColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            modifier = Modifier.scale(BrowserUiTokens.NtpCustomizeSwitchScale),
        )
    }
}

@Composable
private fun NtpActionChip(
    label: String,
    icon: @Composable () -> Unit,
    fg: Color,
    bg: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .browserControlLiquidGlass(RoundedCornerShape(16.dp), interactive = false)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = fg, fontSize = 13.sp, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutChip(
    shortcut: BrowserShortcut,
    labelColor: Color,
    lightText: Boolean,
    darkBubble: Boolean,
    cellWidth: androidx.compose.ui.unit.Dp,
    loadFavicon: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
) {
    // SPEC-198/202: 涟漪 24dp 圆角改画在与内容分离的覆盖层——整格 clip 会把
    // 底部标签文字切进圆角弧线（四字标签首字被斜切），覆盖层只裁涟漪不裁内容。
    val interaction = remember { MutableInteractionSource() }
    Box(modifier = Modifier.width(cellWidth)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onOpen,
                onLongClick = onEdit,
            ),
        ) {
            FaviconBubble(
            title = shortcut.title,
            pageUrl = shortcut.url,
            urls = if (loadFavicon) shortcut.faviconCandidates() else emptyList(),
            localIconRes = shortcut.localIconRes(),
            darkBubble = darkBubble,
        )
        Text(
            shortcut.title,
            color = labelColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(
                shadow = Shadow(
                    color = if (lightText) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.55f),
                    offset = Offset(0f, 1f),
                    blurRadius = 6f,
                ),
            ),
            modifier = Modifier.padding(top = 8.dp),
        )
        }
        // SPEC-202: 涟漪覆盖层——随内容尺寸，24dp 圆角，只裁涟漪不裁文字。
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(24.dp))
                .indication(interaction, ripple(bounded = true)),
        )
    }
}

@Composable
private fun AddShortcutChip(
    labelColor: Color,
    lightText: Boolean,
    darkBubble: Boolean,
    cellWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    // SPEC-198/202: 与 ShortcutChip 同款——涟漪画在覆盖层，整格 clip 不再裁文字。
    val interaction = remember { MutableInteractionSource() }
    Box(modifier = Modifier.width(cellWidth)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        ) {
        val bubbleFill = ntpGlassOverlayAlpha(forceDark = if (darkBubble) true else null)
        Box(
            modifier = Modifier
                .size(BrowserUiTokens.NtpShortcutIconSize)
                .clip(CircleShape)
                .browserControlLiquidGlass(
                    CircleShape,
                    interactive = false,
                    showShadow = false,
                    enableLens = false,
                )
                .background(
                    if (darkBubble) {
                        Color(0xFF2A2A2A).copy(alpha = bubbleFill)
                    } else {
                        // SPEC-067/071: option-row tone; translucent under liquid glass.
                        LegadoTheme.colorScheme.surfaceContainerLow.copy(alpha = bubbleFill)
                    },
                ),
            // SPEC-069: no ring border on add shortcut.
            contentAlignment = Alignment.Center,
        ) {
            SymbolIcon(Symbols.Add, contentDescription = "添加", tint = labelColor)
        }
        Text(
            "添加",
            color = labelColor,
            fontSize = 12.sp,
            style = TextStyle(
                shadow = Shadow(
                    color = if (lightText) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.55f),
                    offset = Offset(0f, 1f),
                    blurRadius = 6f,
                ),
            ),
            modifier = Modifier.padding(top = BrowserUiTokens.SpaceSm),
        )
        }
        // SPEC-202: 涟漪覆盖层（同 ShortcutChip）。
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(24.dp))
                .indication(interaction, ripple(bounded = true)),
        )
    }
}

@Composable
private fun FaviconBubble(
    title: String,
    pageUrl: String,
    urls: List<String>,
    darkBubble: Boolean,
    @androidx.annotation.DrawableRes localIconRes: Int? = null,
) {
    val letter = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "#"
    var index by remember(title, urls.size) { mutableIntStateOf(0) }
    val url = urls.getOrNull(index).orEmpty()
    val cachedBitmap = remember(pageUrl) {
        io.legado.app.browser.engine.BrowserFaviconCache.get(pageUrl)
    }
    val bubbleFill = ntpGlassOverlayAlpha(forceDark = if (darkBubble) true else null)
    Box(
        modifier = Modifier
            .size(BrowserUiTokens.NtpShortcutIconSize)
            .clip(CircleShape)
            .browserControlLiquidGlass(
                CircleShape,
                interactive = false,
                showShadow = false,
                enableLens = false,
            )
            .background(
                if (darkBubble) {
                    Color(0xFF2A2A2A).copy(alpha = bubbleFill)
                } else {
                    // SPEC-067/071: option-row tone; translucent under liquid glass.
                    LegadoTheme.colorScheme.surfaceContainerLow.copy(alpha = bubbleFill)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            color = if (darkBubble) {
                Color(0xFFF6F1EA)
            } else {
                LegadoTheme.colorScheme.primary
            },
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // SPEC-117: prefer bundled high-res icon (e.g. Baidu) over blurry remote favicon.
        // SPEC-221: Chromium onReceivedIcon cache before remote resolvers.
        if (localIconRes != null) {
            Image(
                painter = painterResource(localIconRes),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            Image(
                bitmap = cachedBitmap.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (url.isNotBlank()) {
            // SPEC-069: crop favicon to fill the circle (no inner padding).
            // SPEC-119: decode at 256px so 52dp shortcut icons stay sharp.
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .size(Size(256, 256))
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { },
                error = {
                    LaunchedEffect(url) {
                        if (index < urls.lastIndex) index += 1
                    }
                },
            )
        }
    }
}
