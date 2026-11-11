package io.legado.app.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.format.DateUtils
import android.view.InputDevice
import io.legado.app.browser.engine.BrowserFormFactor
import io.legado.app.browser.engine.BrowserKernelCompat
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.core.ui.player.playerUnderlaySemantics
import io.legado.app.data.repository.ReadAloudSettingsRepository
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.PlaybackCapsuleGateway
import io.legado.app.domain.model.PlaybackCapsuleSource
import io.legado.app.domain.model.PlaybackCapsuleState
import io.legado.app.domain.model.settings.effectivePredictiveBackEnabled
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.Backup
import io.legado.app.help.update.AppUpdateGitHub
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.AudioPlay
import io.legado.app.service.WebService
import io.legado.app.ui.about.MarkdownSheet
import io.legado.app.ui.about.UpdateDialog
import io.legado.app.ui.book.audio.AudioPlayViewModel
import io.legado.app.ui.book.read.ReadAloudControlsRequestBus
import io.legado.app.ui.book.read.ReadAloudVoiceRequestBus
import io.legado.app.ui.book.read.ReadBookInputHandler
import io.legado.app.ui.book.read.ReadBookRouteHost
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.book.readaloud.ReadAloudPlayerMorphHost
import io.legado.app.ui.book.readaloud.ReadAloudPlayerOverlayBus
import io.legado.app.ui.book.readaloud.ReadAloudShellHost
import io.legado.app.ui.book.readaloud.morph.CapsuleAnchorKind
import io.legado.app.ui.book.readaloud.morph.LocalReadAloudMorph
import io.legado.app.ui.book.readaloud.morph.rememberReadAloudMorphState
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerViewModel
import io.legado.app.ui.main.bookshelf.BookshelfCoverPreloader
import io.legado.app.ui.theme.GlassReadiness
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.welcome.WelcomeActivity
import io.legado.app.ui.widget.components.privacy.PrivateAppStartGate
import io.legado.app.utils.LogUtils
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 主界面
 */
open class MainActivity : BaseComposeActivity(), AudioPlay.CallBack,
    io.legado.app.ui.book.manga.MangaReaderKeyHost {

    private data class RouteEvent(
        val route: NavKey,
        val resetToHome: Boolean,
    )

    /** 当前激活的有声书播放器 ViewModel（由有声书路由在生命周期内设置/清理） */
    internal var activeAudioPlayViewModel: AudioPlayViewModel? = null

    /** 全局 Compose 文本弹层状态，供遗留命令式路径展示 Markdown/文本内容 */
    private val textSheetFlow = MutableStateFlow<TextSheetData?>(null)

    fun showTextSheet(title: String, content: String, onDismiss: (() -> Unit)? = null) {
        textSheetFlow.value = TextSheetData(title, content, onDismiss)
    }

    companion object {
        @Volatile
        var hasActiveReadBookRoute: Boolean = false

        @Volatile
        var hasActiveAudioPlayRoute: Boolean = false

        @Volatile
        var hasActiveSourceLoginRoute: Boolean = false

        fun createLauncherIntent(context: Context): Intent =
            MainIntent.createLauncherIntent(context)

        fun createHomeIntent(context: Context): Intent = MainIntent.createHomeIntent(context)
        fun createSourceLoginIntent(
            context: Context,
            type: io.legado.app.ui.login.SourceLoginType,
            sourceKey: String? = null,
            bookUrl: String? = null,
        ): Intent = MainIntent.createSourceLoginIntent(context, type, sourceKey, bookUrl)

        fun createWebViewIntent(
            context: Context,
            title: String? = null,
            url: String,
            sourceOrigin: String? = null,
            sourceName: String? = null,
            sourceType: Int? = null,
            sourceVerificationEnable: Boolean = false,
            refetchAfterSuccess: Boolean = true,
            html: String? = null,
        ): Intent = MainIntent.createWebViewIntent(
            context, title, url, sourceOrigin, sourceName, sourceType,
            sourceVerificationEnable, refetchAfterSuccess, html,
        )

        fun createBookSourceManageIntent(context: Context, importSource: String? = null) =
            MainIntent.createBookSourceManageIntent(context, importSource)

        fun createBookSourceEditIntent(context: Context, sourceUrl: String? = null) =
            MainIntent.createBookSourceEditIntent(context, sourceUrl)

        fun createRssSourceManageIntent(context: Context) =
            MainIntent.createRssSourceManageIntent(context)

        fun createRssSourceEditIntent(context: Context, sourceUrl: String? = null) =
            MainIntent.createRssSourceEditIntent(context, sourceUrl)

        fun createBookSourceDebugIntent(context: Context, sourceUrl: String?) =
            MainIntent.createBookSourceDebugIntent(context, sourceUrl)

        fun createRssSourceDebugIntent(context: Context, sourceUrl: String?) =
            MainIntent.createRssSourceDebugIntent(context, sourceUrl)
        fun createIntent(context: Context, configTag: String? = null): Intent =
            MainIntent.createIntent(context, configTag)

        fun createRssSortIntent(
            context: Context,
            sourceUrl: String,
            sortUrl: String? = null,
            key: String? = null
        ): Intent = MainIntent.createRssSortIntent(context, sourceUrl, sortUrl, key)

        fun createRssReadIntent(
            context: Context,
            title: String? = null,
            origin: String,
            link: String? = null,
            openUrl: String? = null
        ): Intent = MainIntent.createRssReadIntent(context, title, origin, link, openUrl)

        fun createBookshelfManageScreenIntent(context: Context, groupId: Long = -1L): Intent =
            MainIntent.createBookshelfManageScreenIntent(context, groupId)

        fun createCacheIntent(context: Context, groupId: Long = -1L): Intent =
            MainIntent.createCacheIntent(context, groupId)

        fun createBookCacheManageIntent(context: Context): Intent =
            MainIntent.createBookCacheManageIntent(context)

        fun createReadBookIntent(
            context: Context,
            bookUrl: String? = null,
            readAloud: Boolean = false,
            inBookshelf: Boolean = true,
            chapterChanged: Boolean = false,
        ): Intent = MainIntent.createReadBookIntent(
            context = context,
            bookUrl = bookUrl,
            readAloud = readAloud,
            inBookshelf = inBookshelf,
            chapterChanged = chapterChanged,
        )

        fun createReadBookMediaControlIntent(context: Context): Intent =
            MainIntent.createReadBookMediaControlIntent(context)

        fun createReadMangaIntent(
            context: Context,
            bookUrl: String? = null,
            inBookshelf: Boolean = true,
            chapterChanged: Boolean = false,
        ): Intent = MainIntent.createReadMangaIntent(
            context = context,
            bookUrl = bookUrl,
            inBookshelf = inBookshelf,
            chapterChanged = chapterChanged,
        )

        fun createAudioPlayIntent(
            context: Context,
            bookUrl: String? = null,
            inBookshelf: Boolean = true,
        ): Intent = MainIntent.createAudioPlayIntent(
            context = context,
            bookUrl = bookUrl,
            inBookshelf = inBookshelf,
        )

        fun createSearchIntent(
            context: Context,
            key: String? = null,
            scopeRaw: String? = null
        ): Intent = MainIntent.createSearchIntent(context, key, scopeRaw)

        fun createBookInfoIntent(
            context: Context,
            name: String? = null,
            author: String? = null,
            bookUrl: String,
            origin: String? = null,
            coverPath: String? = null
        ): Intent =
            MainIntent.createBookInfoIntent(context, name, author, bookUrl, origin, coverPath)

        fun createBookCharacterDetailIntent(
            context: Context,
            bookUrl: String,
            characterId: String? = null,
        ): Intent = MainIntent.createBookCharacterDetailIntent(context, bookUrl, characterId)

        fun createBookCharacterNetworkIntent(
            context: Context,
            bookUrl: String,
        ): Intent = MainIntent.createBookCharacterNetworkIntent(context, bookUrl)

        fun createBookKnowledgeListIntent(
            context: Context,
            bookUrl: String,
        ): Intent = MainIntent.createBookKnowledgeListIntent(context, bookUrl)

        fun createBookCharacterListIntent(
            context: Context,
            bookUrl: String,
        ): Intent = MainIntent.createBookCharacterListIntent(context, bookUrl)

        fun createBookKnowledgeDetailIntent(
            context: Context,
            bookUrl: String,
            entryId: String? = null,
        ): Intent = MainIntent.createBookKnowledgeDetailIntent(context, bookUrl, entryId)

        fun createBookEventListIntent(
            context: Context,
            bookUrl: String,
        ): Intent = MainIntent.createBookEventListIntent(context, bookUrl)

        fun createBookEventDetailIntent(
            context: Context,
            bookUrl: String,
            eventId: String? = null,
        ): Intent = MainIntent.createBookEventDetailIntent(context, bookUrl, eventId)

        fun createExploreShowIntent(
            context: Context,
            exploreName: String? = null,
            sourceUrl: String,
            exploreUrl: String? = null,
        ): Intent = MainIntent.createExploreShowIntent(context, exploreName, sourceUrl, exploreUrl)
    }

    private val viewModel by viewModel<MainViewModel>()
    private val bookshelfCoverPreloader by inject<BookshelfCoverPreloader>()
    private val otherSettingsGateway by inject<OtherSettingsGateway>()
    private val mangaSettingsGateway by inject<MangaSettingsGateway>()
    private val backupSettingsGateway by inject<BackupSettingsGateway>()
    private val readAloudSettingsRepository by inject<ReadAloudSettingsRepository>()
    private val navRouteTracker by inject<MainNavRouteTracker>()
    private val routeEvents = MutableSharedFlow<RouteEvent>(extraBufferCapacity = 1)
    private val localNetworkPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            WebService.startForeground(this)
        } else {
            toastOnUi(R.string.web_service_local_network_permission_denied)
        }
    }
    private var shouldApplyDefaultToRead = true
    internal var activeReadBookInputHandler: ReadBookInputHandler? = null
    override var activeMangaKeyHandler: ((Int) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        shouldApplyDefaultToRead = savedInstanceState == null
        super.onCreate(savedInstanceState)

        // SPEC-123 diag: capture extra Chromium switches via
        // `am start ... --es xunye_switches "--switch=a,--switch-b"`, applied next start.
        runCatching { BrowserKernelCompat.captureSwitchesExtra(this, intent?.getStringExtra("xunye_switches")) }
        // SPEC-165: register shortcuts after the first frame. Doing it in
        // ReaderProvider.onCreate (1.6.8) crashed the process on the splash window.
        window.decorView.post {
            runCatching { io.legado.app.api.ShortCuts.buildShortCuts(applicationContext) }
                .onFailure { android.util.Log.e("ShortCuts", "register", it) }
            // SPEC-257: warm Chromium after first frame so restore attach is cheaper.
            runCatching {
                io.legado.app.browser.engine.BrowserSessionHolder.warm(applicationContext)
            }
        }
        MainShortcutBus.consumeIntentExtras(intent)

        if (checkStartupRoute()) return

        // 智能自启：如果上次是手动开启状态（web_service_auto 为 true），则自启；
        // 本地网络权限缺失时先申请，磁贴等入口也通过该 extra 转发到这里。
        val requestWebService = otherSettingsGateway.currentSettings.webServiceAutoStart ||
                intent?.getBooleanExtra(MainIntent.EXTRA_WEB_SERVICE_LOCAL_NETWORK, false) == true
        if (requestWebService) {
            startWebServiceWithLocalNetworkPermission()
        }

        lifecycleScope.launch {
            //版本更新
            upVersion()
            //备份同步
            backupSync()
            //自动更新书籍
            val isAutoRefreshedBook = savedInstanceState?.getBoolean("isAutoRefreshedBook") ?: false
            if (otherSettingsGateway.currentSettings.autoRefresh && !isAutoRefreshedBook) {
                viewModel.upAllBookToc()
            }
        }

        // 书架封面预热：必须早于书架首帧发起。卡片请求带了 placeholderMemoryCacheKey，
        // 内存缓存里已有同一键时 Coil 会在真实加载之前就把缓存图交给 target，于是进入书架
        // 的第一帧就是封面，而不是"灰底 → 稍后出现"。独立协程，不阻塞上面的启动关键路径；
        // 预热失败对 UI 无影响（卡片自己的请求会照常决定成功/错误态）。
        lifecycleScope.launch {
            runCatching { bookshelfCoverPreloader.preloadCurrentGroupFirstScreen() }
        }
    }

    /**
     * Android 17 (API 37) 起 Web 服务需要本地网络权限才能接受局域网入站连接。
     * 已授予直接启动；未授予先申请，授予后由 launcher 回调补启。
     */
    private fun startWebServiceWithLocalNetworkPermission() {
        if (WebService.hasLocalNetworkPermission(this)) {
            WebService.startForeground(this)
        } else {
            localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // SPEC-166: shortcut bitmaps follow the system light/dark switch.
        window.decorView.post {
            runCatching { io.legado.app.api.ShortCuts.buildShortCuts(applicationContext) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // SPEC-164: shortcut extras (new tab / bookshelf tab) even when already on Home.
        MainShortcutBus.consumeIntentExtras(intent)
        if (intent.getBooleanExtra(MainIntent.EXTRA_OPEN_READ_ALOUD_PLAYER, false)) {
            ReadAloudPlayerOverlayBus.request()
            return
        }
        if (intent.getBooleanExtra(MainIntent.EXTRA_WEB_SERVICE_LOCAL_NETWORK, false)) {
            startWebServiceWithLocalNetworkPermission()
            return
        }
        if (!intent.hasExplicitStartRoute()) return
        routeEvents.tryEmit(
            RouteEvent(
                route = MainNavigator.resolveStartRoute(intent),
                resetToHome = MainIntent.shouldOpenRouteWithHomeParent(intent),
            )
        )
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val orientation = resources.configuration.orientation
        val smallestWidthDp = resources.configuration.smallestScreenWidthDp
        val configuration = LocalAppUiConfiguration.current
        val tabletInterface = configuration.appShell.tabletInterface
        val defaultToReadFlow = remember(otherSettingsGateway) {
            otherSettingsGateway.settings
                .map { it.defaultToRead }
                .distinctUntilChanged()
        }
        val defaultToRead by defaultToReadFlow.collectAsStateWithLifecycle(
            otherSettingsGateway.currentSettings.defaultToRead,
        )
        val mangaSettings by mangaSettingsGateway.settings.collectAsStateWithLifecycle(
            mangaSettingsGateway.currentSettings,
        )

        // SPEC-018: tablets never show the main bookshelf navigation rail.
        val useRail = if (smallestWidthDp >= 600) {
            false
        } else {
            when (tabletInterface) {
                "always" -> true
                "landscape" -> orientation == Configuration.ORIENTATION_LANDSCAPE
                "off" -> false
                "auto" -> false
                else -> false
            }
        }

        val startRoutes = remember(defaultToRead) {
            val resolved = MainNavigator.resolveStartRoute(intent)
            val hasExplicitStartRoute = intent?.hasExplicitStartRoute() == true
            when {
                resolved is MainRouteAudioPlay -> arrayOf(MainRouteHome)
                MainIntent.shouldOpenRouteWithHomeParent(intent) -> {
                    if (resolved == MainRouteHome) {
                        arrayOf(MainRouteHome)
                    } else {
                        arrayOf(MainRouteHome, resolved)
                    }
                }
                resolved is MainRouteSourceLogin -> {
                    arrayOf(MainRouteHome, resolved)
                }
                resolved == MainRouteBrowserDownloads -> {
                    arrayOf(MainRouteHome, resolved)
                }
                else -> {
                    arrayOf(resolved)
                }
            }
        }
        // SPEC-190: 阅读器已改为独立 Activity。「启动直接进阅读器」改为
        // MainActivity 起来后拉起 ReadBookActivity（系统窗口转场，不再占 nav3 栈）。
        var defaultReaderLaunched by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(defaultToRead) {
            if (
                !defaultReaderLaunched &&
                shouldApplyDefaultToRead &&
                defaultToRead &&
                MainNavigator.resolveStartRoute(intent) == MainRouteHome &&
                intent?.hasExplicitStartRoute() != true
            ) {
                defaultReaderLaunched = true
                startActivity(MainActivity.createReadBookIntent(this@MainActivity))
            }
        }
        val backStack = rememberNavBackStack(*startRoutes)
        SideEffect { navRouteTracker.onBackStackChanged(backStack) }
        // SPEC-224: 废除导航转场玻璃抑制（GlassMotionGate）。

        // SPEC-148 R3: 首帧渲染完成后再过两帧才放行液态玻璃，把玻璃特效层
        // 建立/着色器编译从冷启动首帧关键路径移走（此前走 fallback 半透明纯色）。
        LaunchedEffect(Unit) {
            withFrameNanos {}
            withFrameNanos {}
            GlassReadiness.markReady()
        }

        // 悬浮胶囊是全局叠层：数据来自全局朗读会话与设置，不依赖阅读器是否在栈上。
        val pageShellPlayerViewModel: ReadAloudPlayerViewModel =
            org.koin.compose.koinInject()
        val pageShellPlayerState by pageShellPlayerViewModel.uiState.collectAsStateWithLifecycle()
        val pageShellAloudSettings by pageShellPlayerViewModel.readAloudSettings
            .collectAsStateWithLifecycle()
        val pageShellShowCapsule = pageShellAloudSettings.showReadAloudCapsule
        val pageShellCapsuleScope = rememberCoroutineScope()

        // 两种播放页共享同窗口形变容器，导航栈保留原页面作为动画背景。
        val initialAudioRoute =
            remember { MainNavigator.resolveStartRoute(intent) as? MainRouteAudioPlay }
        var initialAudioHandled by rememberSaveable { mutableStateOf(false) }
        var readAloudPlayerVisible by rememberSaveable {
            mutableStateOf(
                initialAudioRoute == null &&
                        intent.getBooleanExtra(MainIntent.EXTRA_OPEN_READ_ALOUD_PLAYER, false)
            )
        }
        var audioPlayerVisible by rememberSaveable { mutableStateOf(initialAudioRoute != null) }
        var audioPlayerBookUrl by rememberSaveable { mutableStateOf(initialAudioRoute?.bookUrl.orEmpty()) }
        var audioPlayerInBookshelf by rememberSaveable {
            mutableStateOf(
                initialAudioRoute?.inBookshelf ?: true
            )
        }
        var playerSource by rememberSaveable {
            mutableStateOf(if (initialAudioRoute != null) PlaybackCapsuleSource.AudioBook else PlaybackCapsuleSource.ReadAloud)
        }
        val readAloudMorph = rememberReadAloudMorphState()
        val playbackGateway: PlaybackCapsuleGateway = org.koin.compose.koinInject()
        val playbackCapsuleState by playbackGateway.state.collectAsStateWithLifecycle()
        val playerOpenMutex = remember { Mutex() }
        val morphPresent by remember { derivedStateOf { readAloudMorph.progress.value > 0f } }

        suspend fun openPlayer(request: PlaybackCapsuleState) = playerOpenMutex.withLock {
            val source = request.source ?: PlaybackCapsuleSource.ReadAloud
            val switchingPlayer = playerSource != source ||
                    (source == PlaybackCapsuleSource.AudioBook && audioPlayerBookUrl != request.bookUrl)
            if (switchingPlayer && readAloudMorph.progress.value > 0f) {
                // 先交回起点，防止两个宿主同时驱动同一进度，或飞行中途换封面。
                readAloudPlayerVisible = false
                audioPlayerVisible = false
                // 当前宿主响应 visible=false 完成收起，避免两个协程争用 Animatable。
                snapshotFlow { readAloudMorph.progress.value }.first { it <= 0f }
            }
            playerSource = source
            if (source == PlaybackCapsuleSource.AudioBook) {
                audioPlayerBookUrl = request.bookUrl
                audioPlayerInBookshelf = request.inBookshelf
                audioPlayerVisible = true
                readAloudPlayerVisible = false
            } else {
                readAloudPlayerVisible = true
                audioPlayerVisible = false
            }
        }
        LaunchedEffect(Unit) {
            if (!initialAudioHandled) {
                initialAudioHandled = true
                initialAudioRoute?.let { route ->
                    openPlayer(
                        PlaybackCapsuleState(
                            source = PlaybackCapsuleSource.AudioBook,
                            bookUrl = route.bookUrl.orEmpty(),
                            inBookshelf = route.inBookshelf,
                        )
                    )
                }
            }
            // SPEC-221: 阅读器独立 Activity 前台时由其挂 morph；Main 勿在背后抢事件。
            ReadAloudPlayerOverlayBus.events.collect { request ->
                if (ReaderForegroundTracker.readerActivityInForeground) return@collect
                openPlayer(request)
            }
        }
        val playerVisible = readAloudPlayerVisible || audioPlayerVisible
        val matchingCapsule = !playerVisible || capsuleMatchesPlayer(
            playbackCapsuleState, playerSource, audioPlayerBookUrl,
        )
        SideEffect {
            readAloudMorph.reportCapsuleCoverLinked(
                playbackCapsuleState.source == null || matchingCapsule,
            )
            if (!pageShellShowCapsule) readAloudMorph.clearStartAnchors()
        }
        // 只用于首帧锚点布局，不把尚未启动的朗读伪装成正在播放。
        val capsuleAnchorPreview = if (playerVisible && pageShellShowCapsule) {
            PlaybackCapsuleState(source = playerSource)
        } else null
        // 两种胶囊互斥：主页悬浮底栏用圆形胶囊，其余页面用全局可拖拽胶囊。
        // SPEC-235: 平板形态因子不渲染悬浮底栏，必须回落全局胶囊。
        val currentRoute = backStack.lastOrNull()
        val onMainRoute = currentRoute is MainRouteHome
        val isTabletFormFactor = BrowserFormFactor.isTablet(LocalConfiguration.current)
        val useFloatingBottomBar = LocalAppUiConfiguration.current.appShell.useFloatingBottomBar
        val useHomeCapsule = shouldUseHomePlaybackCapsule(
            onMainRoute, configuration.appShell.showBottomView, useFloatingBottomBar, useRail,
            homeBarPresent = !isTabletFormFactor,
        )
        SideEffect {
            readAloudMorph.expectCapsuleAnchors(
                if (!pageShellShowCapsule) null
                else if (useHomeCapsule) CapsuleAnchorKind.HomeBar else CapsuleAnchorKind.Global,
            )
        }

        SideEffect {
            shouldApplyDefaultToRead = false
        }

        LaunchedEffect(backStack) {
            routeEvents.collect { event ->
                val audioRoute = event.route as? MainRouteAudioPlay
                if (audioRoute != null) {
                    openPlayer(
                        PlaybackCapsuleState(
                            source = PlaybackCapsuleSource.AudioBook,
                            bookUrl = audioRoute.bookUrl.orEmpty(),
                            inBookshelf = audioRoute.inBookshelf,
                        )
                    )
                } else {
                    MainNavigator.navigateToRoute(
                        backStack = backStack,
                        route = event.route,
                        tracker = navRouteTracker,
                        resetToHome = event.resetToHome,
                    )
                }
            }
        }

        // SPEC-112: 平板嵌入阅读等壳内请求压栈（引擎/音色、角色配音）。
        LaunchedEffect(backStack) {
            MainRouteRequestBus.events.collect { route ->
                // SPEC-192: 设置族路由改道 SettingsActivity，不再进 Main 的 nav3 栈。
                val settingsStartRoute = when (route) {
                    MainRouteSettings -> MainRouteConst.ROUTE_SETTINGS
                    MainRouteSettingsBrowser -> MainRouteConst.ROUTE_SETTINGS_BROWSER
                    is MainRouteBrowserPrivacy -> MainRouteConst.ROUTE_BROWSER_PRIVACY
                    is MainRouteBrowserPickBlock -> MainRouteConst.ROUTE_BROWSER_PICK_BLOCK
                    is MainRouteBrowserClearData -> MainRouteConst.ROUTE_BROWSER_CLEAR_DATA
                    is MainRouteSettingsOther -> MainRouteConst.ROUTE_SETTINGS_OTHER
                    is MainRouteSettingsRead -> MainRouteConst.ROUTE_SETTINGS_READ
                    is MainRouteSettingsCover -> MainRouteConst.ROUTE_SETTINGS_COVER
                    is MainRouteSettingsCoverAlbums -> MainRouteConst.ROUTE_SETTINGS_COVER_ALBUMS
                    is MainRouteSettingsTheme -> MainRouteConst.ROUTE_SETTINGS_THEME
                    is MainRouteSettingsThemeManage -> MainRouteConst.ROUTE_SETTINGS_THEME_MANAGE
                    is MainRouteSettingsCustomTheme -> MainRouteConst.ROUTE_SETTINGS_CUSTOM_THEME
                    is MainRouteSettingsBackup -> MainRouteConst.ROUTE_SETTINGS_BACKUP
                    is MainRouteSettingsAi -> MainRouteConst.ROUTE_SETTINGS_AI
                    is MainRouteSettingsAiSummary -> MainRouteConst.ROUTE_SETTINGS_AI_SUMMARY
                    is MainRouteSettingsAiPrompt -> MainRouteConst.ROUTE_SETTINGS_AI_PROMPT
                    is MainRouteSettingsAiUsage -> MainRouteConst.ROUTE_SETTINGS_AI_USAGE
                    is MainRouteSettingsDownloadCache -> MainRouteConst.ROUTE_SETTINGS_DOWNLOAD_CACHE
                    is MainRouteSettingsTranslation -> MainRouteConst.ROUTE_SETTINGS_TRANSLATION
                    is MainRouteSettingsPrivate -> MainRouteConst.ROUTE_SETTINGS_PRIVATE
                    else -> null
                }
                if (settingsStartRoute != null) {
                    startActivity(MainIntent.createSettingsIntent(this@MainActivity, settingsStartRoute))
                } else {
                    MainNavigator.navigateToRoute(
                        backStack = backStack,
                        route = route,
                        tracker = navRouteTracker,
                    )
                }
            }
        }

        // SPEC-113: 朗读胶囊「切换音色」→ 引擎与音色页。
        LaunchedEffect(backStack) {
            ReadAloudVoiceRequestBus.events.collect {
                val bookUrl = playbackGateway.state.value.bookUrl
                    .takeIf { it.isNotBlank() }
                MainNavigator.navigateToRoute(
                    backStack = backStack,
                    route = MainRouteCloudTtsEngines(bookUrl),
                    tracker = navRouteTracker,
                )
            }
        }

        LaunchedEffect(backStack) {
            snapshotFlow { backStack.toList() }
                .collect {
                    // 兜底同步：预测性返回、系统返回手势等不经过 navigateToRoute/navigateBack 的路径
                    navRouteTracker.onBackStackChanged(it)
                    MainNavigator.onBackStackChanged()
                }
        }
        // SPEC-152: 根标签页返回拦截器（平板「我的」页按返回 → 回主页而非退出）。
        // MainScreen 在页面组合时注册，离组时注销。
        var rootTabBackHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }
        SharedTransitionLayout {
            // 启动验证做成单独的 screen：门槛未过时完全不组合应用界面，
            // 因此验证页背后看不到书架/阅读界面，也没有可交互的入口
            PrivateAppStartGate {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .playerUnderlaySemantics(playerVisible || morphPresent)
                    ) {
                        NavDisplay(
                            backStack = backStack,
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                            sceneStrategies = listOf(
                                ModalOverlaySceneStrategy(),
                                // SPEC-139: history/bookmarks/tabs/clear-data
                                // self-slide over a composed Home underlay.
                                SlideOverlaySceneStrategy { contentKey ->
                                    slideOverlayStillInStack(contentKey, backStack)
                                },
                                SinglePaneSceneStrategy(),
                            ),
                            // SPEC-101: Keep underneath on push; pop = top slides right only.
                            transitionSpec = {
                                slideInHorizontally(
                                    animationSpec = tween(
                                        durationMillis = 280,
                                        easing = FastOutSlowInEasing,
                                    ),
                                    initialOffsetX = { fullWidth -> fullWidth },
                                ) togetherWith ExitTransition.KeepUntilTransitionsFinished
                            },
                            popTransitionSpec = {
                                EnterTransition.None togetherWith slideOutHorizontally(
                                    animationSpec = tween(
                                        durationMillis = 280,
                                        easing = FastOutSlowInEasing,
                                    ),
                                    targetOffsetX = { fullWidth -> fullWidth },
                                )
                            },
                            predictivePopTransitionSpec = { _ ->
                                EnterTransition.None togetherWith slideOutHorizontally(
                                    animationSpec = tween(
                                        durationMillis = 280,
                                        easing = FastOutSlowInEasing,
                                    ),
                                    targetOffsetX = { fullWidth -> fullWidth },
                                )
                            },
                            onBack = { MainNavigator.navigateBack(this@MainActivity, backStack) },
                            entryProvider = mainEntryProvider(
                                backStack = backStack,
                                configuration = configuration,
                                showMangaUi = mangaSettings.showMangaUi,
                                useRail = useRail,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                onRegisterRootTabBack = { rootTabBackHandler = it },
                                onNavigateToRoute = { route ->
                                    if (route is MainRouteAudioPlay) {
                                        pageShellCapsuleScope.launch {
                                            openPlayer(
                                                PlaybackCapsuleState(
                                                    source = PlaybackCapsuleSource.AudioBook,
                                                    bookUrl = route.bookUrl.orEmpty(),
                                                    inBookshelf = route.inBookshelf,
                                                )
                                            )
                                        }
                                    } else {
                                        MainNavigator.navigateToRoute(
                                            backStack,
                                            route,
                                            navRouteTracker
                                        )
                                    }
                                },
                                onNavigateBack = {
                                    MainNavigator.navigateBack(
                                        this@MainActivity,
                                        backStack,
                                        navRouteTracker
                                    )
                                },
                                readAloudMorph = readAloudMorph.takeIf { useHomeCapsule },
                                homePlaybackCapsuleEnabled = useHomeCapsule,
                                capsuleAnchorPreview = capsuleAnchorPreview,
                            )
                        )
                        // 主页开启悬浮底栏时使用圆形胶囊，其余页面使用全局可拖拽胶囊。
                        // 隐藏的胶囊不再上报形变锚点。
                        CompositionLocalProvider(
                            LocalReadAloudMorph provides readAloudMorph.takeIf { !useHomeCapsule }
                        ) {
                            ReadAloudShellHost(
                                showCapsule = pageShellShowCapsule,
                                hidden = useHomeCapsule,
                                anchorPreview = capsuleAnchorPreview,
                                onCapsulePositionChanged = { x, y ->
                                    pageShellCapsuleScope.launch {
                                        readAloudSettingsRepository.putCapsulePosition(x, y)
                                    }
                                },
                                onOpenPlayer = { ReadAloudPlayerOverlayBus.request(it) },
                            )
                        }
                    }
                    // 听书播放页：同窗口 morph 面板，从胶囊位置长出来。
                    if (playerSource == PlaybackCapsuleSource.ReadAloud) ReadAloudPlayerMorphHost(
                        playerViewModel = pageShellPlayerViewModel,
                        playerState = pageShellPlayerState,
                        morph = readAloudMorph,
                        visible = readAloudPlayerVisible,
                        awaitCapsuleAnchor = pageShellShowCapsule,
                        predictiveBackEnabled = configuration.appShell.effectivePredictiveBackEnabled(),
                        onDismiss = { readAloudPlayerVisible = false },
                        onSwitchToClassic = { bookUrl ->
                            // 前台是阅读器 Activity：让它直接落在经典朗读控制页；
                            // 否则没有可用的阅读界面，打开一个新的。
                            if (isReaderOnTop(backStack)) {
                                ReadAloudControlsRequestBus.request()
                            } else {
                                startActivity(
                                    MainActivity.createReadBookIntent(
                                        this@MainActivity,
                                        bookUrl = bookUrl.ifBlank { null },
                                    )
                                )
                            }
                        },
                    )
                    if (playerSource == PlaybackCapsuleSource.AudioBook &&
                        (audioPlayerVisible || morphPresent)
                    ) {
                        key(audioPlayerBookUrl, audioPlayerInBookshelf) {
                            AudioPlayerMorphOverlay(
                                bookUrl = audioPlayerBookUrl,
                                inBookshelf = audioPlayerInBookshelf,
                                morph = readAloudMorph,
                                visible = audioPlayerVisible,
                                awaitCapsuleAnchor = pageShellShowCapsule,
                                predictiveBackEnabled = configuration.appShell.effectivePredictiveBackEnabled(),
                                onDismiss = { audioPlayerVisible = false },
                            )
                        }
                    }
                }
                BackHandler(
                    enabled = shouldHandleActivityBack(
                        configuration.appShell.effectivePredictiveBackEnabled(),
                        playerVisible || morphPresent,
                    ) || (rootTabBackHandler != null && backStack.size <= 1)
                ) {
                    // SPEC-152: 主栈只剩根页面时，优先交给根标签页拦截器
                    // （如「我的」页返回 → 切回主页标签），拦截失败再走全局返回。
                    val intercepted = rootTabBackHandler
                        ?.takeIf { backStack.size <= 1 }
                        ?.invoke() == true
                    if (!intercepted) {
                        MainNavigator.navigateBack(this@MainActivity, backStack)
                    }
                }
            }
        }
        TextSheetHost()
    }

    @Composable
    private fun TextSheetHost() {
        val sheet by textSheetFlow.collectAsStateWithLifecycle()
        sheet?.let { data ->
            MarkdownSheet(
                show = true,
                title = data.title,
                content = data.content,
                onDismissRequest = {
                    textSheetFlow.value = null
                    data.onDismiss?.invoke()
                },
            )
        }
    }

    private fun checkStartupRoute(): Boolean {
        return when {
            LocalConfig.isFirstOpenApp -> {
                startActivity<WelcomeActivity>()
                finish()
                true
            }
            else -> false
        }
    }

    /**
     * 版本更新日志
     */
    private suspend fun upVersion() = suspendCoroutine<Unit?> { block ->
        if (LocalConfig.versionCode == appInfo.versionCode) {
            block.resume(null)
            return@suspendCoroutine
        }
        LocalConfig.versionCode = appInfo.versionCode
        if (!BuildConfig.DEBUG) {
            lifecycleScope.launch {
                try {
                    val info = AppUpdateGitHub.getReleaseByTag(BuildConfig.VERSION_NAME)
                    if (info != null) {
                        val dialog = UpdateDialog(info)
                        dialog.setOnDismissListener { block.resume(null) }
                        showDialogFragment(dialog)
                    } else {
                        block.resume(null)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    block.resume(null)
                }
            }
        } else {
            block.resume(null)
        }
    }

    /**
     * 备份同步
     */
    private fun backupSync() {
        if (!backupSettingsGateway.currentSettings.autoCheckNewBackup) {
            return
        }
        lifecycleScope.launch {
            val lastBackupFile = try {
                withContext(IO) { viewModel.getLatestWebDavBackup() }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@launch
            } ?: return@launch
            if (lastBackupFile.lastModify - LocalConfig.lastBackup > DateUtils.MINUTE_IN_MILLIS) {
                LocalConfig.lastBackup = lastBackupFile.lastModify
                alert(R.string.restore, R.string.webdav_after_local_restore_confirm) {
                    cancelButton()
                    okButton {
                        viewModel.restoreWebDav(lastBackupFile.name)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (otherSettingsGateway.currentSettings.autoRefresh) {
            outState.putBoolean("isAutoRefreshedBook", true)
        }
        // SPEC-190: 阅读器是独立 Activity，恢复由其自身的 saved state 负责，
        // MainActivity 不再保存阅读路由。
    }

    private fun Intent.hasExplicitStartRoute(): Boolean {
        return hasExtra(MainIntent.EXTRA_START_ROUTE)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isDown = event.action == KeyEvent.ACTION_DOWN
        if (keyCode == KeyEvent.KEYCODE_MENU && isDown) {
            activeReadBookInputHandler?.toggleMenu()
            if (activeReadBookInputHandler != null) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val controller = activeReadBookInputHandler ?: return super.onGenericMotionEvent(event)
        if (0 != (event.source and InputDevice.SOURCE_CLASS_POINTER) &&
            event.action == MotionEvent.ACTION_SCROLL
        ) {
            val axisValue = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            LogUtils.d("onGenericMotionEvent", "axisValue = $axisValue")
            controller.mouseWheelPage(
                if (axisValue < 0.0f) PageDirection.NEXT else PageDirection.PREV
            )
            return true
        }
        if (0 != (event.source and InputDevice.SOURCE_CLASS_JOYSTICK) &&
            event.action == MotionEvent.ACTION_MOVE
        ) {
            val yAxis = event.getAxisValue(MotionEvent.AXIS_Y)
            if (kotlin.math.abs(yAxis) > 0.5f) {
                controller.handleKeyPage(
                    if (yAxis > 0) PageDirection.NEXT else PageDirection.PREV
                )
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (activeMangaKeyHandler?.invoke(keyCode) == true) return true
        if (activeReadBookInputHandler?.onKeyDown(keyCode, event) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (activeReadBookInputHandler?.onKeyUp(keyCode, event) == true) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun setupSystemBar() {
        val host = activeReadBookInputHandler as? ReadBookRouteHost
        if (host != null) {
            host.upSystemUiVisibility()
        } else {
            super.setupSystemBar()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Coroutine.async {
            BookHelp.clearInvalidCache()
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

    // ===== AudioPlay.CallBack（有声书播放器路由注册，转发加载状态给当前播放器）=====

    override fun upLoading(loading: Boolean) {
        activeAudioPlayViewModel?.onLoadingChanged(loading)
    }

    override fun upLyric(lyric: String?) {
        activeAudioPlayViewModel?.onLyricChanged()
    }

    override fun upLyricP(position: Int) {
        // 歌词暂不在界面展示
    }

}

data class TextSheetData(
    val title: String,
    val content: String,
    val onDismiss: (() -> Unit)? = null,
)

class LauncherW : MainActivity()
class Launcher1 : MainActivity()
class Launcher2 : MainActivity()
class Launcher3 : MainActivity()
class Launcher4 : MainActivity()
class Launcher5 : MainActivity()
class Launcher6 : MainActivity()
class Launcher0 : MainActivity()
