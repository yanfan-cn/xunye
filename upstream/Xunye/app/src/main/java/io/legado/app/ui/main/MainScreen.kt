package io.legado.app.ui.main

import io.legado.app.ui.widget.components.icon.SymbolIcon
import io.legado.app.ui.widget.components.icon.Symbols
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.kyant.capsule.ContinuousCapsule
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import io.legado.app.browser.engine.BrowserFormFactor
import io.legado.app.ui.browser.BrowserUiTokens
import io.legado.app.ui.browser.TabletBrowserLayout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.legado.app.R
import io.legado.app.ui.book.readaloud.ReadAloudBarCapsuleEndPadding
import io.legado.app.ui.book.readaloud.ReadAloudBarCapsuleSize
import io.legado.app.ui.book.readaloud.ReadAloudBarCapsuleSlot
import io.legado.app.ui.book.readaloud.ReadAloudPlayerOverlayBus
import io.legado.app.ui.book.readaloud.morph.ReadAloudMorphState
import io.legado.app.ui.main.bookshelf.BookShelfItem
import io.legado.app.ui.main.bookshelf.BookshelfRouteScreen
import io.legado.app.ui.main.bookshelf.BookshelfViewModel
import io.legado.app.ui.main.explore.ExploreRouteScreen
import io.legado.app.ui.main.home.BrowserHomeTabScreen
import io.legado.app.ui.main.my.MyRouteScreen
import io.legado.app.ui.main.my.PrefClickEvent
import io.legado.app.ui.main.rss.RssRouteScreen
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.FloatingBottomBar
import io.legado.app.ui.widget.components.FloatingBottomBarItem
import io.legado.app.ui.widget.components.GlassDefaults
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.navigation.AppNavigationBar
import io.legado.app.ui.widget.components.navigation.AppNavigationBarItem
import io.legado.app.ui.widget.components.pager.rememberPagerFlingPassThroughConnection
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.startActivityForBook
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private val MainNavigationTabMinWidth = 76.dp

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class
)
@Composable
fun MainScreen(
    mainUiState: MainUiState,
    onIntent: (MainUiIntent) -> Unit,
    effects: kotlinx.coroutines.flow.Flow<MainEffect>,
    useRail: Boolean,
    /** 听书胶囊的形变状态：胶囊要排在悬浮底栏同一行，需要把锚点上报给同一个进度源。 */
    readAloudMorph: ReadAloudMorphState?,
    homePlaybackCapsuleEnabled: Boolean,
    capsuleAnchorPreview: io.legado.app.domain.model.PlaybackCapsuleState?,
    onOpenSettings: () -> Unit,
    onNavigateToBrowserSettings: () -> Unit,
    onNavigateToBrowserBookmarks: () -> Unit,
    onNavigateToBrowserDownloads: () -> Unit = {},
    onNavigateToBrowserHistory: () -> Unit,
    onNavigateToBrowserClearData: () -> Unit = {},
    onNavigateToBrowserTabs: () -> Unit = {},
    onNavigateToBrowserReader: (bookUrl: String) -> Unit,
    onNavigateToBrowserManga: (bookUrl: String) -> Unit = {},
    onNavigateToChat: () -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    onNavigateToScopedSearch: (String) -> Unit,
    onNavigateToRemoteImport: () -> Unit,
    onNavigateToLocalImport: (autoPick: String?) -> Unit,
    onNavigateToCache: (Long) -> Unit,
    onNavigateToBookCacheManage: () -> Unit,
    onOpenBookshelfBook: (BookShelfItem, String?) -> Unit,
    onNavigateToBackupSettings: () -> Unit,
    onNavigateToBookInfo: (name: String, author: String, bookUrl: String, origin: String?, coverPath: String?, sharedCoverKey: String?) -> Unit,
    onNavigateToExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onNavigateToSourceLogin: (type: io.legado.app.ui.login.SourceLoginType, sourceUrl: String) -> Unit,
    onNavigateToBookSourceManage: () -> Unit,
    onNavigateToBookSourceEdit: (String?) -> Unit,
    onNavigateToRssSourceManage: () -> Unit,
    onNavigateToRssSourceEdit: (String?) -> Unit,
    onNavigateToRssSort: (sourceUrl: String, sortUrl: String?, key: String?) -> Unit,
    onNavigateToRssRead: (
        title: String?,
        origin: String,
        link: String?,
        openUrl: String?,
        startPage: Boolean
    ) -> Unit,
    onNavigateToRssFavorites: () -> Unit,
    onNavigateToRuleSub: () -> Unit,
    onNavigateToReadRecord: () -> Unit,
    onNavigateToReadRecordOverview: () -> Unit,
    onNavigateToHighlightTagRule: () -> Unit,
    onNavigateToAbout: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    /** SPEC-152: 主栈（根标签返回拦截器）；为空时不拦截。 */
    backStack: MutableList<NavKey>? = null,
    /** SPEC-152: 向 Activity 注册/注销「根标签页返回 → 回主页」拦截器（返回 true=已拦截）。 */
    onRegisterRootTabBack: ((() -> Boolean)?) -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // SPEC-119: open http(s) in browser Home tab (set before pagerState exists).
    var pendingInAppBrowserUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(effects, context) {
        effects.collectLatest { effect ->
            when (effect) {
                is MainEffect.OpenUrl -> {
                    val url = effect.url
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        pendingInAppBrowserUrl = url
                    } else {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, url.toUri())
                        )
                    }
                }

                is MainEffect.CopyUrl -> context.sendToClip(effect.url)
                is MainEffect.StartActivity -> {
                    context.startActivity(Intent(context, effect.destination).apply {
                        effect.configTag?.let { putExtra("configTag", it) }
                    })
                }

                MainEffect.ExitApp -> (context as? ComponentActivity)?.finish()
                MainEffect.NavigateToReadRecord -> onNavigateToReadRecord()
                MainEffect.NavigateToHighlightTagRule -> onNavigateToHighlightTagRule()
                MainEffect.NavigateToAbout -> onNavigateToAbout()
            }
        }
    }

    val hazeState = remember { HazeState() }
    val customSecondaryColor = if (LegadoTheme.isDark) {
        mainUiState.secondaryThemeColorNight.takeIf { it != 0 }
            ?: mainUiState.secondaryThemeColor
    } else {
        mainUiState.secondaryThemeColor
    }
    val floatingBarSurfaceColor = if (mainUiState.deepPersonalizationActive && customSecondaryColor != 0) {
        Color(customSecondaryColor)
    } else {
        LegadoTheme.colorScheme.surface
    }
    val floatingBarBackdrop = rememberLayerBackdrop {
        drawRect(floatingBarSurfaceColor)
        drawContent()
    }
    val destinations = mainUiState.destinations

    // SPEC-018: cold start always opens browser Home (NTP), not bookshelf.
    val initialPage = remember(destinations) {
        val index = destinations.indexOfFirst { it == MainDestination.Home }
        if (index != -1) index else 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { destinations.size }
    val currentDestination = destinations.getOrNull(pagerState.currentPage)
    val onBrowserHome = currentDestination == MainDestination.Home
    val mainContext = LocalContext.current
    // SPEC-188: leave browser main tab (阅读列表 / 我的) → stop video (听音频例外).
    LaunchedEffect(onBrowserHome) {
        if (!onBrowserHome) {
            io.legado.app.ui.browser.video.BrowserVideoLeaveGuard
                .stopExceptAudioListen(mainContext)
        }
    }
    // SPEC-060: from 阅读列表, system back returns to browser Home instead of exiting.
    val bookshelfPageIndex = remember(destinations) {
        destinations.indexOfFirst { it == MainDestination.Bookshelf }
    }
    val homePageIndex = remember(destinations) {
        destinations.indexOfFirst { it == MainDestination.Home }.coerceAtLeast(0)
    }
    // SPEC-119: 软件更新等 → 巡页内打开并切到浏览器主页。
    LaunchedEffect(pendingInAppBrowserUrl) {
        val url = pendingInAppBrowserUrl ?: return@LaunchedEffect
        io.legado.app.browser.engine.BrowserOpenBridge.offer(url)
        pagerState.animateScrollToPage(homePageIndex)
        pendingInAppBrowserUrl = null
    }
    // SPEC-164: desktop shortcuts → new tab / 阅读列表.
    LaunchedEffect(Unit) {
        MainShortcutBus.events.collect { event ->
            when (event) {
                MainShortcutBus.Event.NewTab -> {
                    pagerState.animateScrollToPage(homePageIndex)
                    io.legado.app.browser.engine.BrowserTabManager.openNewTab(url = null, select = true)
                }
                MainShortcutBus.Event.BookshelfTab -> {
                    if (bookshelfPageIndex >= 0) {
                        pagerState.animateScrollToPage(bookshelfPageIndex)
                    }
                }
                // SPEC-221: 软件更新/开发人员 → 强制落在浏览器主页标签。
                MainShortcutBus.Event.BrowserHome -> {
                    pagerState.animateScrollToPage(homePageIndex)
                }
            }
        }
    }
    BackHandler(
        enabled = (
            backStack?.let { it.size <= 1 } ?: true
            ) &&
            currentDestination != null &&
            currentDestination != MainDestination.Home,
    ) {
        // SPEC-060: 书架 → 主页；SPEC-152: 「我的」等根标签页同理（平板反馈：返回直接回桌面）。
        coroutineScope.launch {
            pagerState.animateScrollToPage(homePageIndex)
        }
    }
    // SPEC-152: 预测性返回关闭时，Activity 级 BackHandler 会抢先把根标签返回当成退出；
    // 把拦截器注册给 Activity，让根标签返回在两种预测性返回设置下都回主页。
    DisposableEffect(destinations, homePageIndex) {
        onRegisterRootTabBack(
            if (homePageIndex >= 0) {
                {
                    if (pagerState.currentPage != homePageIndex) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(homePageIndex)
                        }
                        true
                    } else {
                        false
                    }
                }
            } else {
                null
            },
        )
        onDispose { onRegisterRootTabBack(null) }
    }
    // SPEC-008: hide main app bottom bar while a browser session is open on Home.
    // SPEC-177: also hide while phone NTP fullscreen search is open.
    var browserSessionActive by rememberSaveable { mutableStateOf(false) }
    var phoneNtpSearchActive by rememberSaveable { mutableStateOf(false) }
    val hideMainBottomBarForBrowserSession =
        onBrowserHome && (browserSessionActive || phoneNtpSearchActive)
    // SPEC-025: tablet never shows the floating bottom bar.
    val isTabletFormFactor = BrowserFormFactor.isTablet(LocalConfiguration.current)
    val pagerNestedScrollConnection = rememberPagerFlingPassThroughConnection(
        state = pagerState,
        orientation = Orientation.Horizontal,
    )
    val idleNestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {}
    }
    var bookshelfScrollToTopRequest by remember { mutableLongStateOf(0L) }
    fun requestBookshelfScrollToTop() {
        bookshelfScrollToTopRequest++
    }

    fun handleMainDestinationClick(index: Int, destination: MainDestination) {
        // SPEC-266: ignore stale indices under rapid tap / destination list churn.
        if (index !in destinations.indices) return
        if (
            destination == MainDestination.Bookshelf &&
            pagerState.currentPage == index &&
            pagerState.targetPage == index
        ) {
            requestBookshelfScrollToTop()
            return
        }
        coroutineScope.launch {
            // SPEC-266: piled animateScrollToPage on fast bottom-bar taps can race the pager;
            // snap while a scroll is already in flight.
            if (pagerState.isScrollInProgress) {
                pagerState.scrollToPage(index)
            } else {
                pagerState.animateScrollToPage(index)
            }
        }
    }
    LaunchedEffect(destinations) {
        if (destinations.isNotEmpty() && pagerState.currentPage !in destinations.indices) {
            pagerState.scrollToPage(destinations.lastIndex)
        }
    }
    val labelVisibilityMode = mainUiState.labelVisibilityMode
    val isUnlabeled = labelVisibilityMode == "unlabeled"
    val useFloatingBottomBar =
        shouldUseHomePlaybackCapsule(
            onMainRoute = true, showBottomView = mainUiState.showBottomView,
            useFloatingBottomBar = mainUiState.useFloatingBottomBar, useRail = useRail,
        )
    // SPEC-056: liquid glass follows global chromeSurfaceStyle (API 13+ when selected).
    val useLiquidGlass = useFloatingBottomBar &&
            io.legado.app.ui.theme.ChromeSurfaceStylePolicy.isLiquidGlassActive(
                io.legado.app.ui.theme.LocalAppUiConfiguration.current.theme.chromeSurfaceStyle,
            )
    val alwaysShowLabel = labelVisibilityMode == "labeled"
    val showLabel = !isUnlabeled

    val navState = rememberWideNavigationRailState(
        initialValue = if (mainUiState.navExtended)
            WideNavigationRailValue.Expanded
        else
            WideNavigationRailValue.Collapsed
    )

    Row(modifier = Modifier.fillMaxSize()) {
        if (useRail && mainUiState.showBottomView) {
            WideNavigationRail(
                state = navState,
                header = {
                    val expanded = navState.targetValue == WideNavigationRailValue.Expanded

                    Column {
                        IconButton(
                            modifier = Modifier.padding(start = 24.dp),
                            onClick = {
                                coroutineScope.launch {
                                    val targetExpanded = !expanded
                                    if (targetExpanded) navState.expand()
                                    else navState.collapse()
                                    onIntent(MainUiIntent.SetNavigationRailExpanded(targetExpanded))
                                }
                            }
                        ) {
                            SymbolIcon(
                                if (expanded)
                                    Symbols.MenuOpen
                                else
                                    Symbols.Menu,
                                contentDescription = stringResource(
                                    if (expanded) R.string.collapse else R.string.expand
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        ExtendedFloatingActionButton(
                            modifier = Modifier.padding(start = 20.dp),
                            onClick = { onNavigateToSearch(null) },
                            expanded = expanded,
                            icon = { AppIcon(Symbols.Search, contentDescription = null) },
                            text = { AppText(stringResource(R.string.search)) }
                        )
                    }
                }
            ) {
                destinations.forEachIndexed { index, destination ->
                    val selected = pagerState.targetPage == index
                    var showGroupMenu by remember { mutableStateOf(false) }
                    val haptic = LocalHapticFeedback.current
                    val destinationLabel = stringResource(destination.labelId)

                    WideNavigationRailItem(
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = destinationLabel
                        },
                        railExpanded = navState.targetValue == WideNavigationRailValue.Expanded,
                        selected = selected,
                        onClick = {
                            handleMainDestinationClick(index, destination)
                        },
                        icon = {
                            Box {
                                NavigationIcon(
                                    destination = destination,
                                    selected = selected,
                                    customIconPath = mainUiState.customIconPath(destination),
                                    modifier = if (destination == MainDestination.Bookshelf) {
                                        Modifier.combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                handleMainDestinationClick(index, destination)
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                showGroupMenu = true
                                            }
                                        )
                                    } else Modifier
                                )

                                if (destination == MainDestination.Bookshelf && showGroupMenu) {
                                    BookshelfRailGroupMenuRoute(
                                        expanded = showGroupMenu,
                                        onDismissRequest = { showGroupMenu = false },
                                        onBeforeSelectGroup = {
                                            if (pagerState.currentPage != index) {
                                                pagerState.scrollToPage(index)
                                            }
                                        }
                                    )
                                }
                            }
                        },
                        label = if (labelVisibilityMode != "unlabeled") {
                            { AppText(stringResource(destination.labelId)) }
                        } else null
                    )
                }
            }
        }

        AppScaffold(
            modifier = Modifier.weight(1f),
            bottomBar = {
                if (
                    !useRail &&
                    mainUiState.showBottomView &&
                    !useFloatingBottomBar &&
                    !hideMainBottomBarForBrowserSession
                ) {
                    AppNavigationBar(
                        showLabel = showLabel,
                        alwaysShowLabel = alwaysShowLabel
                    ) {
                        destinations.forEachIndexed { index, destination ->
                            val selected = pagerState.targetPage == index
                            val customIconPath = mainUiState.customIconPath(destination)
                            val selectedCustomIconPath =
                                mainUiState.selectedCustomIconPath(destination)
                            val destinationLabel = stringResource(destination.labelId)
                            AppNavigationBarItem(
                                modifier = Modifier.semantics(mergeDescendants = true) {
                                    contentDescription = destinationLabel
                                },
                                selected = selected,
                                onClick = {
                                    handleMainDestinationClick(index, destination)
                                },
                                labelString = stringResource(destination.labelId),
                                iconVector = AppIcons.mainDestination(destination, selected),
                                m3Icon = {
                                    NavigationIcon(
                                        destination = destination,
                                        selected = selected,
                                        customIconPath = if (selected) {
                                            selectedCustomIconPath.ifEmpty { customIconPath }
                                        } else customIconPath,
                                    )
                                },
                                m3IndicatorColor = GlassDefaults.glassColor(
                                    noBlurColor = LegadoTheme.colorScheme.secondaryContainer,
                                    blurAlpha = GlassDefaults.ThickBlurAlpha
                                ),
                                m3ShowLabel = showLabel,
                                m3AlwaysShowLabel = alwaysShowLabel,
                                useCustomIcon =
                                    customIconPath.isNotEmpty() || selectedCustomIconPath.isNotEmpty(),
                            )
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets(0)
        ) { _ ->
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // SPEC-257: disable haze/backdrop sampling while the main pager is
                // scrolling into 「我的」/阅读列表 — liquid glass + swipe was the jank source.
                val pagerSettled = !pagerState.isScrollInProgress
                Box(
                    modifier = Modifier.then(
                        // SPEC-009/021: never sample browser Home (NTP or session) —
                        // haze/layerBackdrop + pager swipe = jank; Chromium sample = blank.
                        if (useLiquidGlass && !onBrowserHome && pagerSettled) {
                            Modifier
                                .hazeSource(hazeState)
                                .layerBackdrop(floatingBarBackdrop)
                        } else {
                            Modifier
                        }
                    )
                ) {
                    HorizontalPager(
                        state = pagerState,
                        // Browser home: do not nest-scroll with pager (causes scroll jank).
                        pageNestedScrollConnection = if (onBrowserHome) {
                            idleNestedScrollConnection
                        } else {
                            pagerNestedScrollConnection
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                with(sharedTransitionScope) {
                                    if (this != null) Modifier.skipToLookaheadSize() else Modifier
                                }
                            ),
                        // SPEC-021: restore finger-follow tab swipe (Legado MD3 parity).
                        // Nested scroll stays idle on browser Home so vertical web scroll is not stolen.
                        // SPEC-221: NTP 搜索页打开时禁止左右滑到阅读列表/我的。
                        userScrollEnabled = TabletBrowserLayout.pagerUserScrollEnabled(
                            isTablet = isTabletFormFactor,
                            onBrowserHome = onBrowserHome,
                            sessionActive = browserSessionActive,
                            phoneNtpSearchActive = phoneNtpSearchActive,
                        ),
                        // SPEC-195: 回归原版参数 4（原版 legado-with-MD3 同位置 = 4）。
                        // 决策链：193 归零的理由（热邻居抢占主线程致滑动掉帧）已被 LOG-194 真机
                        // 推翻（warm=0 滑动依旧掉帧）；194 回调 1 后切标签仍非常卡（真机）。
                        // 详情已搬出 Main（SPEC-193），193 冻结归因的前提不复存在。
                        // 巡页 3 标签页：4 与 2 效果等价（全部常驻），取 4 保持与原版一致。
                        beyondViewportPageCount = 4,
                    ) { page ->
                        val destination = destinations.getOrNull(page) ?: return@HorizontalPager
                        val pageLifecycleOwner = rememberMainPageLifecycleOwner(
                            isActive = page == pagerState.currentPage
                        )
                        CompositionLocalProvider(LocalLifecycleOwner provides pageLifecycleOwner) {
                            when (destination) {
                            MainDestination.Home -> BrowserHomeTabScreen(
                                onOpenBook = { book ->
                                    onNavigateToBookInfo(
                                        book.name,
                                        book.author,
                                        book.bookUrl,
                                        book.origin,
                                        book.getDisplayCover(),
                                        // SPEC-183: source cover joins the shared-element morph.
                                        bookCoverSharedElementKey(book.bookUrl),
                                    )
                                },
                                onSessionActiveChange = { browserSessionActive = it },
                                onPhoneSearchActiveChange = { phoneNtpSearchActive = it },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onOpenBrowserSettings = onNavigateToBrowserSettings,
                                onOpenBookmarks = onNavigateToBrowserBookmarks,
                                onOpenDownloads = onNavigateToBrowserDownloads,
                                onOpenHistory = onNavigateToBrowserHistory,
                                onOpenClearBrowsingData = onNavigateToBrowserClearData,
                                onOpenTabs = onNavigateToBrowserTabs,
                                onOpenReader = onNavigateToBrowserReader,
                                onOpenMangaReader = onNavigateToBrowserManga,
                                // SPEC-238: 平板侧栏阅读书签 → 阅读器定位书签章节（手机端同语义）。
                                onOpenReadingBookmark = { context.startActivityForBook(it) },
                                onOpenBookshelf = {
                                    val index = destinations.indexOfFirst {
                                        it == MainDestination.Bookshelf
                                    }
                                    if (index >= 0) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                },
                                onOpenMy = {
                                    val index = destinations.indexOfFirst {
                                        it == MainDestination.My
                                    }
                                    if (index >= 0) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                },
                                onOpenAiChat = onNavigateToChat,
                            )

                            MainDestination.Bookshelf -> BookshelfRouteScreen(
                                scrollToTopRequest = bookshelfScrollToTopRequest,
                                onScrollToTopRequestHandled = { handledRequest ->
                                    if (bookshelfScrollToTopRequest == handledRequest) {
                                        bookshelfScrollToTopRequest = 0L
                                    }
                                },
                                onBookClick = { book, sharedCoverKey ->
                                    onOpenBookshelfBook(book, sharedCoverKey)
                                },
                                onBookLongClick = { book, sharedCoverKey ->
                                    onNavigateToBookInfo(
                                        book.name,
                                        book.author,
                                        book.bookUrl,
                                        book.origin,
                                        book.getDisplayCover(),
                                        sharedCoverKey
                                    )
                                },
                                onNavigateToSearch = { query -> onNavigateToSearch(query) },
                                onNavigateBackToBrowser = if (isTabletFormFactor) {
                                    {
                                        val index = destinations.indexOfFirst {
                                            it == MainDestination.Home
                                        }
                                        if (index >= 0) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        }
                                    }
                                } else {
                                    null
                                },
                                onNavigateToRemoteImport = onNavigateToRemoteImport,
                                onNavigateToLocalImport = onNavigateToLocalImport,
                                onNavigateToCache = onNavigateToCache,
                                onNavigateToSettings = onOpenSettings,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                            )

                            MainDestination.Explore -> ExploreRouteScreen(
                                onOpenExploreShow = onNavigateToExploreShow,
                                onOpenLogin = { sourceUrl ->
                                    onNavigateToSourceLogin(
                                        io.legado.app.ui.login.SourceLoginType.BookSource,
                                        sourceUrl,
                                    )
                                },
                                onOpenEdit = onNavigateToBookSourceEdit,
                                onOpenSearch = onNavigateToScopedSearch,
                            )
                            MainDestination.Rss -> RssRouteScreen(
                                onOpenSort = { sourceUrl, sortUrl, key ->
                                    onNavigateToRssSort(sourceUrl, sortUrl, key)
                                },
                                onOpenRead = { title, origin, link, openUrl, startPage ->
                                    onNavigateToRssRead(title, origin, link, openUrl, startPage)
                                },
                                onOpenFavorites = onNavigateToRssFavorites,
                                onOpenRuleSub = onNavigateToRuleSub,
                                onOpenLogin = { sourceUrl ->
                                    onNavigateToSourceLogin(
                                        io.legado.app.ui.login.SourceLoginType.RssSource,
                                        sourceUrl,
                                    )
                                },
                                onOpenSourceEdit = onNavigateToRssSourceEdit,
                                onOpenSourceManage = onNavigateToRssSourceManage,
                            )
                            MainDestination.My -> MyRouteScreen(
                                onOpenSettings = onOpenSettings,
                                onNavigateToChat = onNavigateToChat,
                                onNavigate = { event ->
                                    when (event) {
                                        PrefClickEvent.OpenBrowserDownloads -> onNavigateToBrowserDownloads()
                                        PrefClickEvent.OpenBrowserBookmarks -> onNavigateToBrowserBookmarks()
                                        PrefClickEvent.OpenBookSourceManage -> onNavigateToBookSourceManage()
                                        PrefClickEvent.OpenReadRecord -> onNavigateToReadRecord()
                                        PrefClickEvent.OpenLocalImport -> onNavigateToLocalImport(null)
                                        else -> onIntent(MainUiIntent.HandlePreferenceClick(event))
                                    }
                                }
                            )
                        }
                        }
                    }
                }

                if (useFloatingBottomBar && !hideMainBottomBarForBrowserSession && !isTabletFormFactor) {
                    var readAloudCapsuleShown by remember { mutableStateOf(false) }
                    var playbackControlsExpanded by rememberSaveable { mutableStateOf(false) }
                    val expansion by animateFloatAsState(
                        targetValue = if (playbackControlsExpanded && readAloudCapsuleShown) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = 1f,
                            stiffness = 260f,
                            visibilityThreshold = 0.0001f
                        ),
                        label = "homePlaybackControls",
                    )
                    val phoneLandscape = LocalConfiguration.current.orientation ==
                        Configuration.ORIENTATION_LANDSCAPE
                    val navAtEnd = TabletBrowserLayout.phoneHomeNavAtEnd(
                        isTablet = false,
                        landscape = phoneLandscape,
                    )
                    // SPEC-160: browser home gradient translucent mask behind nav (no blur).
                    if (onBrowserHome) {
                        val scrim = LegadoTheme.colorScheme.background
                        val edge = BrowserUiTokens.ntpScrimEdgeAlpha(LegadoTheme.isDark)
                        if (navAtEnd) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(BrowserUiTokens.NtpScrimHeightBottom)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                scrim.copy(alpha = 0f),
                                                scrim.copy(alpha = edge),
                                            ),
                                        ),
                                    ),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(BrowserUiTokens.NtpScrimHeightBottom)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                scrim.copy(alpha = 0f),
                                                scrim.copy(alpha = edge),
                                            ),
                                        ),
                                    ),
                            )
                        }
                    }
                    // SPEC-133/170: landscape phone — vertical nav on the end, vertically centered.
                    if (navAtEnd) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.End),
                                )
                                .padding(end = 12.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .clip(ContinuousCapsule)
                                        .background(
                                            LegadoTheme.colorScheme.surfaceContainer.copy(
                                                alpha = if (useLiquidGlass) 0.85f else 1f,
                                            ),
                                        )
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    destinations.forEachIndexed { index, destination ->
                                        val selected = pagerState.targetPage == index
                                        val customIconPath = mainUiState.customIconPath(destination)
                                        val selectedCustomIconPath =
                                            mainUiState.selectedCustomIconPath(destination)
                                        val destinationLabel = stringResource(destination.labelId)
                                        Column(
                                            modifier = Modifier
                                                .defaultMinSize(minWidth = 56.dp, minHeight = 52.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable {
                                                    handleMainDestinationClick(index, destination)
                                                }
                                                .padding(vertical = 6.dp, horizontal = 4.dp)
                                                .semantics(mergeDescendants = true) {
                                                    contentDescription = destinationLabel
                                                },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            NavigationIcon(
                                                destination = destination,
                                                customIconPath = if (selected) {
                                                    selectedCustomIconPath.ifEmpty { customIconPath }
                                                } else customIconPath,
                                                selected = selected,
                                            )
                                            if (showLabel && (alwaysShowLabel || selected)) {
                                                AppText(
                                                    text = stringResource(destination.labelId),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                                ReadAloudBarCapsuleSlot(
                                    enabled = homePlaybackCapsuleEnabled,
                                    onOpenPlayer = { ReadAloudPlayerOverlayBus.request(it) },
                                    morph = readAloudMorph,
                                    anchorPreview = capsuleAnchorPreview,
                                    backdrop = floatingBarBackdrop,
                                    isBlurEnabled = useLiquidGlass,
                                    controlsExpanded = playbackControlsExpanded,
                                    expansion = expansion,
                                    width = ReadAloudBarCapsuleSize,
                                    expandedWidth = ReadAloudBarCapsuleSize,
                                    onControlsExpandedChange = { playbackControlsExpanded = it },
                                    onOccupiedWidthChanged = { readAloudCapsuleShown = it > 0 },
                                )
                            }
                        }
                    }
                    if (!navAtEnd) BoxWithConstraints(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(
                                bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues()
                                    .calculateBottomPadding()
                            )
                    ) {
                        val gap = ReadAloudBarCapsuleEndPadding
                        val capsuleReserve by animateDpAsState(
                            targetValue = if (readAloudCapsuleShown) ReadAloudBarCapsuleSize + gap else 0.dp,
                            // 入场前立即留出空间；退场后逐步归还，避免停播时导航宽度跳变。
                            animationSpec = if (readAloudCapsuleShown) snap() else spring(
                                dampingRatio = 1f,
                                stiffness = 260f
                            ),
                            label = "homePlaybackReserve",
                        )
                        val navigationAvailableWidth =
                            (maxWidth - capsuleReserve).coerceAtLeast(0.dp)
                        // 保留原导航项的自然宽度和居中位置，不把没有胶囊的底栏拉满。
                        val normalNavigationWidth =
                            (MainNavigationTabMinWidth * destinations.size + 8.dp)
                                .coerceAtMost(navigationAvailableWidth)
                        val navigationStartInset =
                            (navigationAvailableWidth - normalNavigationWidth) / 2
                        val expandedCapsuleWidth = (maxWidth - ReadAloudBarCapsuleSize - gap)
                            .coerceAtLeast(ReadAloudBarCapsuleSize)
                        val capsuleWidth = ReadAloudBarCapsuleSize +
                                (expandedCapsuleWidth - ReadAloudBarCapsuleSize) * expansion
                        val navigationWidth = normalNavigationWidth +
                                (ReadAloudBarCapsuleSize.coerceAtMost(maxWidth) - normalNavigationWidth) * expansion
                        FloatingBottomBar(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = navigationStartInset * (1f - expansion))
                                .width(navigationWidth),
                            compactProgress = expansion,
                            expandedWidth = normalNavigationWidth,
                            onRestoreNavigation = { playbackControlsExpanded = false },
                            selectedIndex = { pagerState.targetPage },
                            onSelected = { index ->
                                destinations.getOrNull(index)?.let { destination ->
                                    handleMainDestinationClick(index, destination)
                                }
                            },
                            onReselected = { index ->
                                destinations.getOrNull(index)?.let { destination ->
                                    handleMainDestinationClick(index, destination)
                                }
                            },
                            backdrop = floatingBarBackdrop,
                            tabsCount = destinations.size,
                            isBlurEnabled = useLiquidGlass,
                            hasCustomIcons = destinations.any { dest ->
                                mainUiState.customIconPath(dest).isNotEmpty() ||
                                        mainUiState.selectedCustomIconPath(dest).isNotEmpty()
                            }
                        ) {
                            destinations.forEachIndexed { index, destination ->
                                val selected = pagerState.targetPage == index
                                val customIconPath = mainUiState.customIconPath(destination)
                                val selectedCustomIconPath =
                                    mainUiState.selectedCustomIconPath(destination)
                                val destinationLabel = stringResource(destination.labelId)
                                FloatingBottomBarItem(
                                    enabled = !playbackControlsExpanded && expansion <= 0.05f,
                                    onClick = {
                                        handleMainDestinationClick(index, destination)
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(
                                            minWidth = MainNavigationTabMinWidth
                                        )
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = destinationLabel
                                        }
                                ) {
                                    NavigationIcon(
                                        destination = destination,
                                        customIconPath = if (selected) {
                                            selectedCustomIconPath.ifEmpty { customIconPath }
                                        } else customIconPath,
                                        selected = selected
                                    )
                                    if (
                                        showLabel && (alwaysShowLabel || selected)
                                    ) {
                                        AppText(
                                            text = stringResource(destination.labelId),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        // 打开听书播放页：与阅读器共用同一条全局请求通道。
                        ReadAloudBarCapsuleSlot(
                            enabled = homePlaybackCapsuleEnabled,
                            onOpenPlayer = { ReadAloudPlayerOverlayBus.request(it) },
                            morph = readAloudMorph,
                            anchorPreview = capsuleAnchorPreview,
                            // 与悬浮底栏共用同一份 backdrop，液态玻璃开关一致。
                            backdrop = floatingBarBackdrop,
                            isBlurEnabled = useLiquidGlass,
                            controlsExpanded = playbackControlsExpanded,
                            expansion = expansion,
                            width = capsuleWidth,
                            expandedWidth = expandedCapsuleWidth,
                            onControlsExpandedChange = { playbackControlsExpanded = it },
                            onOccupiedWidthChanged = { readAloudCapsuleShown = it > 0 },
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberMainPageLifecycleOwner(isActive: Boolean): LifecycleOwner {
    val parentLifecycle = LocalLifecycleOwner.current.lifecycle
    val currentActive by rememberUpdatedState(isActive)
    val owner = remember(parentLifecycle) { MainPageLifecycleOwner() }

    DisposableEffect(parentLifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            owner.update(parentLifecycle.currentState, currentActive)
        }
        parentLifecycle.addObserver(observer)
        owner.update(parentLifecycle.currentState, currentActive)
        onDispose {
            parentLifecycle.removeObserver(observer)
            owner.destroy()
        }
    }

    LaunchedEffect(isActive, parentLifecycle) {
        owner.update(parentLifecycle.currentState, isActive)
    }

    return owner
}

private class MainPageLifecycleOwner : LifecycleOwner {

    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle = registry

    fun update(parentState: Lifecycle.State, isActive: Boolean) {
        registry.currentState = when {
            parentState == Lifecycle.State.DESTROYED -> Lifecycle.State.DESTROYED
            parentState == Lifecycle.State.INITIALIZED -> Lifecycle.State.INITIALIZED
            parentState == Lifecycle.State.CREATED -> Lifecycle.State.CREATED
            isActive -> parentState
            else -> Lifecycle.State.STARTED
        }
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

@Composable
private fun BookshelfRailGroupMenuRoute(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onBeforeSelectGroup: suspend () -> Unit,
    viewModel: BookshelfViewModel = koinViewModel()
) {
    val groupState by viewModel.groupSelectorState.collectAsStateWithLifecycle()
    BookshelfRailGroupMenu(
        expanded = expanded,
        state = groupState,
        onDismissRequest = onDismissRequest,
        onSelectGroup = { groupId ->
            onBeforeSelectGroup()
            viewModel.onIntent(
                io.legado.app.ui.main.bookshelf.BookshelfIntent.ChangeGroup(groupId)
            )
        },
    )
}

@Composable
private fun BookshelfRailGroupMenu(
    expanded: Boolean,
    state: io.legado.app.ui.main.bookshelf.BookshelfGroupSelectorState,
    onDismissRequest: () -> Unit,
    onSelectGroup: suspend (Long) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    RoundDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) { dismiss ->
        state.groups.forEachIndexed { groupIndex, group ->
            RoundDropdownMenuItem(
                text = group.groupName,
                onClick = {
                    coroutineScope.launch {
                        onSelectGroup(group.groupId)
                        dismiss()
                    }
                },
                trailingIcon = {
                    if (state.selectedGroupIndex == groupIndex) {
                        SymbolIcon(Symbols.Check,
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun NavigationIcon(
    destination: MainDestination,
    selected: Boolean,
    customIconPath: String,
    modifier: Modifier = Modifier
) {
    if (customIconPath.isNotEmpty()) {
        AsyncImage(
            model = customIconPath,
            contentDescription = null,
            modifier = modifier.size(40.dp)
        )
    } else {
        val icon = AppIcons.mainDestination(destination, selected)
        AppIcon(icon, contentDescription = null, modifier = modifier)
    }
}
