package io.legado.app.ui.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.ContentTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import io.legado.app.R
import io.legado.app.browser.engine.BrowserFormFactor
import io.legado.app.constant.BookType
import io.legado.app.core.ui.player.PlayerMorphAppearance
import io.legado.app.core.ui.player.PlayerMorphHost
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.domain.model.PlaybackCapsuleSource
import io.legado.app.domain.model.PlaybackCapsuleState
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.effectivePredictiveBackEnabled
import io.legado.app.feature.reader.platform.ReaderPerfTrace
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.preferMangaReader
import io.legado.app.help.book.isNotShelf
import io.legado.app.model.AudioPlay
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.SourceCallBack
import io.legado.app.service.AudioPlayService
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.about.AboutEffect
import io.legado.app.ui.about.AboutScreen
import io.legado.app.ui.about.AboutViewModel
import io.legado.app.ui.ai.chat.AiChatRouteScreen
import io.legado.app.ui.book.audio.AudioPlayEffect
import io.legado.app.ui.book.audio.AudioPlayIntent
import io.legado.app.ui.book.audio.AudioPlayScreenContent
import io.legado.app.ui.book.audio.AudioPlayViewModel
import io.legado.app.ui.book.cache.manage.BookCacheManageRouteScreen
import io.legado.app.ui.book.explore.ExploreShowIntent
import io.legado.app.ui.book.explore.ExploreShowRouteScreen
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.import.local.ImportBookRouteScreen
import io.legado.app.ui.book.import.remote.RemoteBookRouteScreen
import io.legado.app.ui.book.info.BookInfoRouteScreen
import io.legado.app.ui.book.info.BookInfoViewModel
import io.legado.app.ui.book.knowledge.BookCharacterDetailScreen
import io.legado.app.ui.book.knowledge.BookCharacterDetailViewModel
import io.legado.app.ui.book.knowledge.BookCharacterListScreen
import io.legado.app.ui.book.knowledge.BookCharacterListViewModel
import io.legado.app.ui.book.knowledge.BookCharacterNetworkScreen
import io.legado.app.ui.book.knowledge.BookCharacterNetworkViewModel
import io.legado.app.ui.book.knowledge.BookEventDetailScreen
import io.legado.app.ui.book.knowledge.BookEventDetailViewModel
import io.legado.app.ui.book.knowledge.BookEventListScreen
import io.legado.app.ui.book.knowledge.BookEventListViewModel
import io.legado.app.ui.book.knowledge.BookKnowledgeDetailScreen
import io.legado.app.ui.book.knowledge.BookKnowledgeDetailViewModel
import io.legado.app.ui.book.knowledge.BookKnowledgeListScreen
import io.legado.app.ui.book.knowledge.BookKnowledgeListViewModel
import io.legado.app.ui.book.knowledge.CharacterAvatarCropDialog
import io.legado.app.ui.book.knowledge.CharacterDetailIntent
import io.legado.app.ui.book.knowledge.deleteCharacterAvatar
import io.legado.app.ui.book.knowledge.saveCharacterAvatar
import io.legado.app.ui.book.manage.BookshelfManageRouteScreen
import io.legado.app.ui.book.manga.MangaReaderRouteScreen
import io.legado.app.ui.book.manga.MangaReaderViewModel
import io.legado.app.ui.book.read.ReadBookController
import io.legado.app.ui.book.read.ReadBookInitRequest
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookRouteScreen
import io.legado.app.ui.book.read.ReadBookViewModel
import io.legado.app.ui.book.read.ReaderSessionViewModel
import io.legado.app.ui.book.readRecord.ReadRecordOverviewRouteScreen
import io.legado.app.ui.book.readRecord.ReadRecordRouteScreen
import io.legado.app.ui.book.readaloud.ReadAloudPlayerOverlayBus
import io.legado.app.ui.book.readaloud.cache.TtsCacheRouteScreen
import io.legado.app.ui.book.readaloud.casting.BookVoiceCastingScreen
import io.legado.app.ui.book.readaloud.casting.BookVoiceCastingViewModel
import io.legado.app.ui.book.readaloud.cloudtts.CloudTtsEffect
import io.legado.app.ui.book.readaloud.cloudtts.CloudTtsIntent
import io.legado.app.ui.book.readaloud.cloudtts.CloudTtsScreen
import io.legado.app.ui.book.readaloud.cloudtts.CloudTtsTab
import io.legado.app.ui.book.readaloud.cloudtts.CloudTtsViewModel
import io.legado.app.ui.book.readaloud.morph.ReadAloudMorphState
import io.legado.app.ui.book.readaloud.player.rememberPlayerThemeOverride
import io.legado.app.ui.book.search.SearchIntent
import io.legado.app.ui.book.search.SearchRouteScreen
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.searchContent.SearchContentRouteScreen
import io.legado.app.ui.book.searchContent.SearchContentViewModel
import io.legado.app.ui.book.source.debug.BookSourceDebugRoute
import io.legado.app.ui.book.source.debug.BookSourceDebugViewModel
import io.legado.app.ui.book.source.edit.BookSourceEditRoute
import io.legado.app.ui.book.source.edit.BookSourceEditViewModel
import io.legado.app.ui.book.source.manage.BookSourceRouteScreen
import io.legado.app.ui.browser.WebViewModel
import io.legado.app.ui.browser.WebViewRouteScreen
import io.legado.app.ui.config.ConfigNavScreen
import io.legado.app.ui.config.TabletSettingsPane
import io.legado.app.ui.config.TabletSettingsScreen
import io.legado.app.ui.config.ai.AiConfigRouteScreen
import io.legado.app.ui.config.ai.AiModelEditRouteScreen
import io.legado.app.ui.config.ai.AiProviderEditRouteScreen
import io.legado.app.ui.config.ai.prompt.AiPromptConfigRouteScreen
import io.legado.app.ui.config.ai.summary.AiSummaryConfigRouteScreen
import io.legado.app.ui.config.backupConfig.BackupConfigRouteScreen
import io.legado.app.ui.config.coverConfig.CoverAlbumManageRouteScreen
import io.legado.app.ui.config.coverConfig.CoverConfigRouteScreen
import io.legado.app.ui.config.customTheme.CustomThemeRouteScreen
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfigRouteScreen
import io.legado.app.ui.config.otherConfig.OtherConfigRouteScreen
import io.legado.app.ui.config.privateConfig.PrivateConfigRouteScreen
import io.legado.app.ui.config.readConfig.ReadConfigRouteScreen
import io.legado.app.ui.config.themeConfig.ThemeConfigRouteScreen
import io.legado.app.ui.config.themeManage.ThemeManageRouteScreen
import io.legado.app.ui.config.translation.TranslationConfigRouteScreen
import io.legado.app.ui.highlightTagRule.HighlightTagRuleRouteScreen
import io.legado.app.ui.login.SourceLoginIntent
import io.legado.app.ui.login.SourceLoginRoute
import io.legado.app.ui.login.SourceLoginType
import io.legado.app.ui.login.SourceLoginViewModel
import io.legado.app.ui.rss.article.MainRouteRssSort
import io.legado.app.ui.rss.article.RssSortRouteScreen
import io.legado.app.ui.rss.favorites.RssFavoritesRouteScreen
import io.legado.app.ui.rss.read.MainRouteRssRead
import io.legado.app.ui.rss.read.RssReadRouteScreen
import io.legado.app.ui.rss.source.debug.RssSourceDebugRoute
import io.legado.app.ui.rss.source.debug.RssSourceDebugViewModel
import io.legado.app.ui.rss.source.edit.RssSourceEditRoute
import io.legado.app.ui.rss.source.edit.RssSourceEditViewModel
import io.legado.app.ui.rss.source.manage.RssSourceRouteScreen
import io.legado.app.ui.rss.subscription.RuleSubRouteScreen
import io.legado.app.ui.widget.components.changeSource.ChangeSourceSheet
import io.legado.app.ui.widget.components.privacy.PrivateReadGate
import io.legado.app.ui.widget.components.privacy.PrivateVerifyGate
import io.legado.app.utils.openUrl
import io.legado.app.utils.sendToClip
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.toggleSystemBar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * SPEC-101: Nav3 official overlay pattern (see animate-destinations):
 * - Push: new page slides in; underneath KeepUntilTransitionsFinished (do not drag Home/AwContents).
 * - Pop: Enter None; top page slides out to the right.
 * Still no fade/scale over AwContents / liquid glass.
 *
 * KeepUntilTransitionsFinished is only public on [AnimatedContentTransitionScope]
 * (internal on ExitTransition.Companion alone 鈥?do not call from top-level helpers).
 */
private const val BrowserSlideMs = 280

private fun AnimatedContentTransitionScope<*>.browserOverlayPush(): ContentTransform =
    slideInHorizontally(
        animationSpec = tween(durationMillis = BrowserSlideMs, easing = FastOutSlowInEasing),
        initialOffsetX = { fullWidth -> fullWidth },
    ) togetherWith ExitTransition.KeepUntilTransitionsFinished

private fun AnimatedContentTransitionScope<*>.browserOverlayPop(): ContentTransform =
    EnterTransition.None togetherWith slideOutHorizontally(
        animationSpec = tween(durationMillis = BrowserSlideMs, easing = FastOutSlowInEasing),
        targetOffsetX = { fullWidth -> fullWidth },
    )

private fun webViewEntryMetadata(@Suppress("UNUSED_PARAMETER") predictiveBackEnabled: Boolean) =
    metadata {
        put(NavDisplay.TransitionKey) { browserOverlayPush() }
        put(NavDisplay.PopTransitionKey) { browserOverlayPop() }
        put(NavDisplay.PredictivePopTransitionKey) { _ -> browserOverlayPop() }
    }

/** SPEC-139/142: self-sliding overlay whose underlay (Home) stays composed. */
private fun browserSlideOverlayMetadata(onBack: () -> Unit): Map<String, Any> =
    SlideOverlaySceneStrategy.slideOverlay(onBack) + metadata {
        put(NavDisplay.TransitionKey) { EnterTransition.None togetherWith ExitTransition.None }
        put(NavDisplay.PopTransitionKey) { EnterTransition.None togetherWith ExitTransition.None }
        put(NavDisplay.PredictivePopTransitionKey) { _ ->
            EnterTransition.None togetherWith ExitTransition.None
        }
    }

/**
 * SPEC-162: settings use Material Forward/Backward (Shared Axis X), matching upstream
 * Legado NavDisplay defaults 鈥?not SlideOverlay (which blanked/reblocked re-entry).
 */
private fun AnimatedContentTransitionScope<*>.settingsSharedAxisPush(): ContentTransform =
    (slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(durationMillis = NavMotion.SettingsAxisSlideMs, easing = FastOutSlowInEasing),
        initialOffset = { fullWidth -> fullWidth },
    ) + fadeIn(
        animationSpec = tween(durationMillis = NavMotion.SettingsAxisFadeMs, easing = LinearOutSlowInEasing),
    )) togetherWith (slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(durationMillis = NavMotion.SettingsAxisSlideMs, easing = FastOutSlowInEasing),
        targetOffset = { fullWidth -> fullWidth / 4 },
    ) + fadeOut(
        animationSpec = tween(durationMillis = NavMotion.SettingsAxisFadeMs, easing = LinearOutSlowInEasing),
    ))

private fun AnimatedContentTransitionScope<*>.settingsSharedAxisPop(): ContentTransform =
    (slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(durationMillis = NavMotion.SettingsAxisSlideMs, easing = FastOutSlowInEasing),
        initialOffset = { fullWidth -> -fullWidth / 4 },
    ) + fadeIn(
        animationSpec = tween(durationMillis = NavMotion.SettingsAxisFadeMs, easing = LinearOutSlowInEasing),
    )) togetherWith (scaleOut(
        targetScale = 0.8f,
        animationSpec = tween(durationMillis = NavMotion.SettingsAxisSlideMs, easing = FastOutSlowInEasing),
    ) + fadeOut(animationSpec = tween(durationMillis = NavMotion.SettingsAxisFadeMs)))

private fun settingsSharedAxisMetadata(): Map<String, Any> = metadata {
    put(NavDisplay.TransitionKey) { settingsSharedAxisPush() }
    put(NavDisplay.PopTransitionKey) { settingsSharedAxisPop() }
    put(NavDisplay.PredictivePopTransitionKey) { _ -> settingsSharedAxisPop() }
}

/**
 * 浠ュ簳閮ㄥ脊灞傚憟鐜扮殑鐩殑鍦帮紙鍚功鎾斁椤点€佹湁澹颁功鎾斁椤碉級銆?
 *
 * 涓婁竴绔欎繚鎸佺粍鍚堝湪寮瑰眰涔嬩笅锛堢湡瀹炶儗鏅彲瑙侊級锛岀洰鐨勫湴鑷韩涓嶅弬涓庤浆鍦猴細
 * 杩涘嚭鍔ㄧ敾瀹屽叏鐢?`AppModalBottomSheet` 璐熻矗锛屽鑸眰鍙礋璐ｆ爤銆?
 */
private fun sheetEntryMetadata(): Map<String, Any> =
    ModalOverlaySceneStrategy.modalOverlay() + metadata {
        put(NavDisplay.TransitionKey) { EnterTransition.None togetherWith ExitTransition.None }
        put(NavDisplay.PopTransitionKey) { EnterTransition.None togetherWith ExitTransition.None }
        put(NavDisplay.PredictivePopTransitionKey) { _ ->
            EnterTransition.None togetherWith ExitTransition.None
        }
    }

/**
 * 鍚功鎾斁寮瑰眰銆岀粡鍏告帶鍒躲€嶇殑鐩爣鍒ゅ畾锛氭爤椤舵槸闃呰鐣岄潰鏃舵妸璇锋眰浜ょ粰瀹冿紝
 * 鍚﹀垯鏂板紑闃呰鐣岄潰銆傛娊鎴愮函鍑芥暟浠ヤ究鍗曟祴瑕嗙洊杩欐潯鍒嗘敮銆?
 *
 * 鎾斁寮瑰眰涓嶅湪瀵艰埅鏍堜笂锛屾墍浠ュ垽鎹槸銆屽綋鍓嶉《灞傘€嶈€屼笉鏄€屼笂涓€绔欍€嶃€?
 */
/**
 * SPEC-190: 阅读器已搬入独立 Activity，「栈顶是阅读界面」的判据改为
 * 前台是否存在阅读器 Activity（ReadBookActivity / ReadMangaActivity 维护）。
 */
object ReaderForegroundTracker {
    var readerActivityInForeground: Boolean = false
}

internal fun isReaderOnTop(backStack: List<NavKey>): Boolean =
    ReaderForegroundTracker.readerActivityInForeground

@OptIn(ExperimentalSharedTransitionApi::class)
fun MainActivity.mainEntryProvider(
    backStack: MutableList<NavKey>,
    configuration: AppUiConfiguration,
    showMangaUi: Boolean,
    useRail: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    onNavigateToRoute: (NavKey) -> Unit,
    onNavigateBack: () -> Unit,
    /**
     * 銆岃兌鍥?鈫?鍚功鎾斁椤点€嶇殑褰㈠彉鐘舵€併€?
     *
     * 涓荤晫闈㈣鎶婂惉涔﹁兌鍥婃帓鍦ㄦ偓娴簳鏍忓悓涓€琛岄噷锛岄渶瑕佹妸鑳跺泭鐨勯敋鐐逛笂鎶ョ粰鍚屼竴涓繘搴︽簮锛?
     * 鎵€浠ヨ繖閲岄€忎紶锛堝叾浣欑洰鐨勫湴涓嶇敤锛夈€?
     */
    readAloudMorph: ReadAloudMorphState?,
    homePlaybackCapsuleEnabled: Boolean,
    capsuleAnchorPreview: io.legado.app.domain.model.PlaybackCapsuleState?,
    /** SPEC-152: 鏍规爣绛鹃〉杩斿洖鎷︽埅鍣ㄦ敞鍐岄€氶亾锛圡ainScreen 鈫?MainActivity锛夈€?*/
    onRegisterRootTabBack: ((() -> Boolean)?) -> Unit,
) = entryProvider {
    entry<MainRouteWebView>(
        metadata = webViewEntryMetadata(configuration.appShell.effectivePredictiveBackEnabled())
    ) { route ->
        val viewModel = koinViewModel<WebViewModel>(
            key = "WebView:${route.url}:${route.sourceOrigin}:${route.sourceVerificationEnable}",
        )
        val browserIntent = remember(route) {
            Intent().apply {
                putExtra("title", route.title)
                putExtra("url", route.url)
                putExtra("sourceOrigin", route.sourceOrigin)
                putExtra("sourceName", route.sourceName)
                route.sourceType?.let { putExtra("sourceType", it) }
                putExtra("sourceVerificationEnable", route.sourceVerificationEnable)
                putExtra("refetchAfterSuccess", route.refetchAfterSuccess)
                putExtra("html", route.html)
            }
        }
        WebViewRouteScreen(
            intent = browserIntent,
            viewModel = viewModel,
            onFinish = onNavigateBack,
            onImportBookSource = { importUrl ->
                onNavigateToRoute(MainRouteBookSourceManage(importUrl))
            },
            // SPEC-149: 涓庝富娴忚鍣ㄤ竴鑷寸殑闃呰鍏ュ彛锛堜功婧愰獙璇?娣遍摼浼氳瘽鍐咃級銆?
            onOpenReader = { bookUrl ->
                startActivity(
                    MainActivity.createReadBookIntent(this@mainEntryProvider, bookUrl = bookUrl)
                )
            },
            onOpenMangaReader = { bookUrl ->
                startActivity(
                    MainActivity.createReadMangaIntent(this@mainEntryProvider, bookUrl = bookUrl)
                )
            },
        )
    }
    entry<MainRouteSourceLogin>(
        metadata = ModalOverlaySceneStrategy.modalOverlay(),
    ) { route ->
        DisposableEffect(route) {
            MainActivity.hasActiveSourceLoginRoute = true
            onDispose {
                MainActivity.hasActiveSourceLoginRoute = false
            }
        }
        val viewModel = koinViewModel<SourceLoginViewModel>(
            key = "SourceLogin:${route.type}:${route.sourceKey}:${route.bookUrl}",
        )
        SourceLoginRoute(
            request = SourceLoginIntent.Initialize(route.type, route.sourceKey, route.bookUrl),
            viewModel = viewModel,
            host = this@mainEntryProvider,
            onBack = onNavigateBack,
        )
    }
    entry<MainRouteBookSourceManage>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) { route ->
        BookSourceRouteScreen(
            initialImportUrl = route.importUrl,
            closeAfterImport = route.importUrl != null,
            onImportClosed = onNavigateBack,
            onBackClick = onNavigateBack,
            onAddSource = { onNavigateToRoute(MainRouteBookSourceEdit()) },
            onEditSource = { onNavigateToRoute(MainRouteBookSourceEdit(it)) },
            onLoginSource = {
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.BookSource, it))
            },
            onSearchSource = { sourceName, sourceUrl ->
                val scopeRaw = BookSearchScope.encodeSource(sourceName, sourceUrl)
                onNavigateToRoute(MainRouteSearch(null, scopeRaw))
            },
            onDebugSource = { sourceUrl ->
                onNavigateToRoute(MainRouteBookSourceDebug(sourceUrl))
            },
        )
    }
    entry<MainRouteBookSourceEdit>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) { route ->
        val viewModel = koinViewModel<BookSourceEditViewModel>(
            key = "BookSourceEdit:${route.sourceUrl.orEmpty()}",
        )
        BookSourceEditRoute(
            sourceUrl = route.sourceUrl,
            viewModel = viewModel,
            onBack = { savedSourceUrl ->
                if (backStack.size == 1) {
                    savedSourceUrl?.let {
                        this@mainEntryProvider.setResult(
                            android.app.Activity.RESULT_OK,
                            Intent().putExtra("origin", it),
                        )
                    }
                    this@mainEntryProvider.finish()
                } else onNavigateBack()
            },
            onLogin = {
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.BookSource, it))
            },
            onDebug = { onNavigateToRoute(MainRouteBookSourceDebug(it)) },
            onSearch = { onNavigateToRoute(MainRouteSearch(null, it.toString())) },
        )
    }
    entry<MainRouteRssSourceManage>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) {
        RssSourceRouteScreen(
            onBackClick = onNavigateBack,
            onEditSource = { onNavigateToRoute(MainRouteRssSourceEdit(it.sourceUrl)) },
            onAddSource = { onNavigateToRoute(MainRouteRssSourceEdit()) },
        )
    }
    entry<MainRouteRssSourceEdit>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) { route ->
        val viewModel = koinViewModel<RssSourceEditViewModel>(
            key = "RssSourceEdit:${route.sourceUrl.orEmpty()}",
        )
        RssSourceEditRoute(
            sourceUrl = route.sourceUrl,
            viewModel = viewModel,
            onBack = { savedSourceUrl ->
                if (backStack.size == 1) {
                    savedSourceUrl?.let {
                        this@mainEntryProvider.setResult(
                            android.app.Activity.RESULT_OK,
                            Intent().putExtra("origin", it),
                        )
                    }
                    this@mainEntryProvider.finish()
                } else onNavigateBack()
            },
            onLogin = {
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.RssSource, it))
            },
            onDebug = { onNavigateToRoute(MainRouteRssSourceDebug(it)) },
        )
    }
    entry<MainRouteBookSourceDebug>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) { route ->
        val viewModel = koinViewModel<BookSourceDebugViewModel>(
            key = "BookSourceDebug:${route.sourceUrl.orEmpty()}",
        )
        BookSourceDebugRoute(route.sourceUrl, viewModel, onNavigateBack)
    }
    entry<MainRouteRssSourceDebug>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) { route ->
        val viewModel = koinViewModel<RssSourceDebugViewModel>(
            key = "RssSourceDebug:${route.sourceUrl.orEmpty()}",
        )
        RssSourceDebugRoute(route.sourceUrl, viewModel, onNavigateBack)
    }
    entry<MainRouteHome> {
        val mainViewModel = koinViewModel<MainViewModel>()
        val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()
        val isTablet = BrowserFormFactor.isTablet(LocalConfiguration.current)
        MainScreen(
            mainUiState = mainUiState,
            onIntent = mainViewModel::onIntent,
            effects = mainViewModel.effects,
            useRail = useRail,
            readAloudMorph = readAloudMorph,
            homePlaybackCapsuleEnabled = homePlaybackCapsuleEnabled,
            capsuleAnchorPreview = capsuleAnchorPreview,
            backStack = backStack,
            onRegisterRootTabBack = onRegisterRootTabBack,
            onOpenSettings = {
                // SPEC-192: 设置族在独立 SettingsActivity。
                startActivity(
                    MainIntent.createSettingsIntent(this@mainEntryProvider)
                )
            },
            onNavigateToBrowserSettings = {
                // SPEC-060: tablet opens dual-pane settings (default Browser).
                startActivity(
                    MainIntent.createSettingsIntent(
                        this@mainEntryProvider,
                        if (isTablet) {
                            MainRouteConst.ROUTE_SETTINGS
                        } else {
                            MainRouteConst.ROUTE_SETTINGS_BROWSER
                        },
                    )
                )
            },
            onNavigateToBrowserBookmarks = {
                onNavigateToRoute(MainRouteBrowserBookmarks)
            },
            onNavigateToBrowserDownloads = {
                onNavigateToRoute(MainRouteBrowserDownloads)
            },
            onNavigateToBrowserHistory = {
                onNavigateToRoute(MainRouteBrowserHistory)
            },
            onNavigateToBrowserClearData = {
                startActivity(
                    MainIntent.createSettingsIntent(
                        this@mainEntryProvider,
                        MainRouteConst.ROUTE_BROWSER_CLEAR_DATA,
                    )
                )
            },
            onNavigateToBrowserTabs = {
                // SPEC-078: same as 9.1 鈥?navigate directly (no pause/suppress).
                onNavigateToRoute(MainRouteBrowserTabs)
            },
            onNavigateToBrowserReader = { bookUrl ->
                // SPEC-119: keep web reading books on shelf (宸茶); do not delete on exit.
                startActivity(
                    MainActivity.createReadBookIntent(this@mainEntryProvider, bookUrl = bookUrl)
                )
            },
            onNavigateToBrowserManga = { bookUrl ->
                startActivity(
                    MainActivity.createReadMangaIntent(this@mainEntryProvider, bookUrl = bookUrl)
                )
            },
            onNavigateToChat = {
                onNavigateToRoute(MainRouteAiChat)
            },
            onNavigateToSearch = { key ->
                onNavigateToRoute(
                    MainRouteSearch(
                        key = key?.trim()?.takeIf { it.isNotEmpty() }
                    )
                )
            },
            onNavigateToScopedSearch = { scopeRaw ->
                onNavigateToRoute(MainRouteSearch(key = null, scopeRaw = scopeRaw))
            },
            onNavigateToRemoteImport = {
                onNavigateToRoute(MainRouteImportRemote)
            },
            onNavigateToLocalImport = { autoPick ->
                onNavigateToRoute(MainRouteImportLocal(autoPick = autoPick))
            },
            onNavigateToCache = { groupId ->
                onNavigateToRoute(MainRouteCache(groupId))
            },
            onNavigateToBookCacheManage = {
                onNavigateToRoute(MainRouteBookCacheManage)
            },
            onOpenBookshelfBook = { book, sharedCoverKey ->
                if (book.isAudio) {
                    onNavigateToRoute(
                        MainRouteAudioPlay(
                            bookUrl = book.bookUrl,
                            sharedCoverKey = sharedCoverKey
                        )
                    )
                } else if (book.preferMangaReader(showMangaUi)) {
                    startActivity(
                        MainActivity.createReadMangaIntent(this@mainEntryProvider, bookUrl = book.bookUrl)
                    )
                } else {
                    startActivity(
                        MainActivity.createReadBookIntent(this@mainEntryProvider, bookUrl = book.bookUrl)
                    )
                }
            },
            onNavigateToBackupSettings = {
                startActivity(
                    MainIntent.createSettingsIntent(
                        this@mainEntryProvider,
                        MainRouteConst.ROUTE_SETTINGS_BACKUP,
                    )
                )
            },
            onNavigateToBookInfo = { name, author, bookUrl, origin, coverPath, _ ->
                // SPEC-193: 详情走 BookInfoActivity（原版做法），主壳不再被导航场景拆建。
                startActivity(
                    MainActivity.createBookInfoIntent(
                        this@mainEntryProvider,
                        name = name,
                        author = author,
                        bookUrl = bookUrl,
                        origin = origin,
                        coverPath = coverPath,
                    )
                )
            },
            onNavigateToExploreShow = { title, sourceUrl, exploreUrl ->
                onNavigateToRoute(
                    MainRouteExploreShow(
                        title = title,
                        sourceUrl = sourceUrl,
                        exploreUrl = exploreUrl
                    )
                )
            },
            onNavigateToSourceLogin = { type, sourceUrl ->
                onNavigateToRoute(MainRouteSourceLogin(type, sourceUrl))
            },
            onNavigateToBookSourceManage = {
                onNavigateToRoute(MainRouteBookSourceManage())
            },
            onNavigateToBookSourceEdit = {
                onNavigateToRoute(MainRouteBookSourceEdit(it))
            },
            onNavigateToRssSourceManage = {
                onNavigateToRoute(MainRouteRssSourceManage)
            },
            onNavigateToRssSourceEdit = {
                onNavigateToRoute(MainRouteRssSourceEdit(it))
            },
            onNavigateToRssSort = { sourceUrl, sortUrl, key ->
                onNavigateToRoute(
                    MainRouteRssSort(
                        sourceUrl = sourceUrl,
                        sortUrl = sortUrl,
                        key = key
                    )
                )
            },
            onNavigateToRssRead = { title, origin, link, openUrl, startPage ->
                onNavigateToRoute(
                    MainRouteRssRead(
                        title = title,
                        origin = origin,
                        link = link,
                        openUrl = openUrl,
                        startPage = startPage
                    )
                )
            },
            onNavigateToRssFavorites = {
                onNavigateToRoute(MainRouteRssFavorites)
            },
            onNavigateToRuleSub = {
                onNavigateToRoute(MainRouteRuleSub)
            },
            onNavigateToReadRecord = {
                onNavigateToRoute(MainRouteReadRecord)
            },
            onNavigateToReadRecordOverview = {
                onNavigateToRoute(MainRouteReadRecordOverview)
            },
            onNavigateToHighlightTagRule = {
                onNavigateToRoute(MainRouteHighlightTagRule)
            },
            onNavigateToAbout = {
                onNavigateToRoute(MainRouteAbout)
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }

    entry<MainRouteBrowserBookmarks>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) {
        io.legado.app.ui.browser.BrowserBookmarksScreen(
            onBack = { onNavigateBack() },
            onOpenUrl = { url ->
                // SPEC-124: open synchronously so Home remounts straight into the
                // session 鈥?no NTP flash, no offer/consume hop (double compose).
                io.legado.app.browser.engine.BrowserTabManager.openUrlInActive(url)
                onNavigateBack()
            },
            onOpenBook = { book ->
                // SPEC-090: reading bookmarks open the matching reader.
                when {
                    book.isAudio -> onNavigateToRoute(
                        MainRouteAudioPlay(
                            bookUrl = book.bookUrl,
                        ),
                    )
                    book.preferMangaReader(showMangaUi) -> startActivity(
                        MainActivity.createReadMangaIntent(
                            this@mainEntryProvider,
                            bookUrl = book.bookUrl,
                            inBookshelf = !book.isNotShelf,
                        ),
                    )
                    else -> startActivity(
                        MainActivity.createReadBookIntent(
                            this@mainEntryProvider,
                            bookUrl = book.bookUrl,
                            inBookshelf = !book.isNotShelf,
                        ),
                    )
                }
            },
        )
    }

    entry<MainRouteBrowserDownloads>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) {
        io.legado.app.ui.browser.BrowserDownloadsScreen(
            onBack = { onNavigateBack() },
            onOpenPageArchive = { path, baseUrl, title ->
                io.legado.app.browser.engine.BrowserOpenBridge.offerLocalHtml(
                    path = path,
                    baseUrl = baseUrl,
                    title = title,
                )
                onNavigateBack()
            },
        )
    }

    entry<MainRouteBrowserHistory>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) {
        io.legado.app.ui.browser.BrowserHistoryScreen(
            onBack = { onNavigateBack() },
            onOpenUrl = { url ->
                // SPEC-124: open synchronously so Home remounts straight into the
                // session 鈥?no NTP flash, no offer/consume hop (double compose).
                io.legado.app.browser.engine.BrowserTabManager.openUrlInActive(url)
                onNavigateBack()
            },
            onOpenClearData = {
                startActivity(
                    MainIntent.createSettingsIntent(
                        this@mainEntryProvider,
                        MainRouteConst.ROUTE_BROWSER_CLEAR_DATA,
                    )
                )
            },
        )
    }

    entry<MainRouteBrowserTabs>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) {
        io.legado.app.ui.browser.BrowserTabsScreen(
            onBack = { onNavigateBack() },
        )
    }

    entry<MainRouteAiChat>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) {
        AiChatRouteScreen(
            onBackClick = { onNavigateBack() },
            onOpenBookInfo = { book ->
                startActivity(
                    MainActivity.createBookInfoIntent(
                        this@mainEntryProvider,
                        name = book.name,
                        author = book.author,
                        bookUrl = book.bookUrl,
                        origin = book.origin,
                        coverPath = book.coverPath,
                    )
                )
            },
            // SPEC-250: 未配置模型 → 独立 SettingsActivity 的 AI 设置页。
            onOpenAiSettings = {
                startActivity(
                    MainIntent.createSettingsIntent(
                        this@mainEntryProvider,
                        MainRouteConst.ROUTE_SETTINGS_AI,
                    )
                )
            },
        )
    }

    entry<MainRouteImportLocal>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) { route ->
        ImportBookRouteScreen(
            autoPick = route.autoPick,
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteImportRemote>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) {
        RemoteBookRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteCache>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) { route ->
        BookshelfManageRouteScreen(
            groupId = route.groupId,
            onBackClick = { onNavigateBack() },
            onOpenBookInfo = { name, author, bookUrl ->
                startActivity(
                    MainActivity.createBookInfoIntent(
                        this@mainEntryProvider,
                        name = name,
                        author = author,
                        bookUrl = bookUrl,
                    )
                )
            }
        )
    }

    entry<MainRouteBookCacheManage>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) {
        BookCacheManageRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteReadAloudPlayer>(metadata = sheetEntryMetadata()) {
        LaunchedEffect(Unit) {
            ReadAloudPlayerOverlayBus.request(PlaybackCapsuleState(source = PlaybackCapsuleSource.ReadAloud))
            if (backStack.size > 1) onNavigateBack() else backStack[0] = MainRouteHome
        }
    }

    // 鍏煎宸蹭繚瀛樼殑瀵艰埅鏍堛€傛柊鍏ュ彛鍦?MainActivity 鐩存帴鎵撳紑鍚岀獥鍙ｆ挱鏀炬诞灞傘€?
    entry<MainRouteAudioPlay>(metadata = sheetEntryMetadata()) { route ->
        LaunchedEffect(route) {
            ReadAloudPlayerOverlayBus.request(
                PlaybackCapsuleState(
                    source = PlaybackCapsuleSource.AudioBook,
                    bookUrl = route.bookUrl.orEmpty(),
                    inBookshelf = route.inBookshelf,
                )
            )
            if (backStack.size > 1) onNavigateBack() else backStack[0] = MainRouteHome
        }
    }

    entry<MainRouteSearchContent> { route ->
        val viewModel = koinViewModel<SearchContentViewModel>(
            key = "SearchContent:${route.bookUrl}",
            parameters = { parametersOf(route) }
        )
        SearchContentRouteScreen(
            viewModel = viewModel,
            autoFocus = route.autoFocus,
            onBack = { onNavigateBack() },
        )
    }

    entry<MainRouteSearch> { route ->
        val searchViewModel = koinViewModel<SearchViewModel>()

        LaunchedEffect(route.key, route.scopeRaw, searchViewModel) {
            searchViewModel.onIntent(
                SearchIntent.Initialize(
                    key = route.key,
                    scopeRaw = route.scopeRaw
                )
            )
        }

        SearchRouteScreen(
            viewModel = searchViewModel,
            onBack = {
                onNavigateBack()
            },
            onOpenBookInfo = { name, author, bookUrl, origin, coverPath, _ ->
                startActivity(
                    MainActivity.createBookInfoIntent(
                        this@mainEntryProvider,
                        name = name,
                        author = author,
                        bookUrl = bookUrl,
                        origin = origin,
                        coverPath = coverPath,
                    )
                )
            },
            onOpenSourceManage = {
                onNavigateToRoute(MainRouteBookSourceManage())
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }

    entry<MainRouteRssSort> { route ->
        RssSortRouteScreen(
            sourceUrl = route.sourceUrl,
            initialSortUrl = route.sortUrl,
            initialSearchKey = route.key,
            onBackClick = { onNavigateBack() },
            onSearch = { key ->
                onNavigateToRoute(
                    MainRouteRssSort(
                        sourceUrl = route.sourceUrl,
                        key = key
                    )
                )
            },
            onOpenRead = { title, origin, link, openUrl ->
                if (link?.contains("@js:") == true) {
                    onNavigateToRoute(
                        MainRouteRssSort(
                            sourceUrl = origin,
                            sortUrl = link
                        )
                    )
                } else {
                    onNavigateToRoute(
                        MainRouteRssRead(
                            title = title,
                            origin = origin,
                            link = link,
                            openUrl = openUrl
                        )
                    )
                }
            },
            onEditSource = { onNavigateToRoute(MainRouteRssSourceEdit(it)) },
            onLogin = {
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.RssSource, it))
            },
        )
    }

    entry<MainRouteRssRead>(
        metadata = webViewEntryMetadata(configuration.appShell.effectivePredictiveBackEnabled())
    ) { route ->
        RssReadRouteScreen(
            title = route.title,
            origin = route.origin,
            link = route.link,
            openUrl = route.openUrl,
            startPage = route.startPage,
            onBackClick = { onNavigateBack() },
            onOpenArticles = { sortUrl, targetOrigin ->
                onNavigateToRoute(
                    MainRouteRssSort(
                        sourceUrl = targetOrigin ?: route.origin,
                        sortUrl = sortUrl
                    )
                )
            }
        )
    }

    entry<MainRouteRssFavorites>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) {
        RssFavoritesRouteScreen(
            onBackClick = { onNavigateBack() },
            onOpenRead = { title, origin, link, openUrl ->
                onNavigateToRoute(
                    MainRouteRssRead(
                        title = title,
                        origin = origin,
                        link = link,
                        openUrl = openUrl
                    )
                )
            }
        )
    }

    entry<MainRouteRuleSub>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) {
        RuleSubRouteScreen(
            onBackClick = { onNavigateBack() },
            onImportBookSource = {
                onNavigateToRoute(MainRouteBookSourceManage(it))
            },
        )
    }

    entry<MainRouteReadRecord>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) {
        val context = LocalContext.current
        ReadRecordRouteScreen(
            onBackClick = { onNavigateBack() },
            onBookClick = { name, author ->
                // SPEC-119: open book/web directly; never fall through to search.
                lifecycleScope.launch {
                    val book = withContext(IO) {
                        io.legado.app.data.appDb.bookDao.getBook(name, author)
                    }
                    when {
                        book != null -> this@mainEntryProvider.startActivityForBook(book)
                        else -> context.toastOnUi("鎵句笉鍒板搴斾功鏈垨缃戦〉")
                    }
                }
            },
            onSummaryClick = {
                onNavigateToRoute(MainRouteReadRecordOverview)
            }
        )
    }

    entry<MainRouteReadRecordOverview>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) {
        val context = LocalContext.current
        ReadRecordOverviewRouteScreen(
            onBackClick = { onNavigateBack() },
            onBookClick = { name, author ->
                lifecycleScope.launch {
                    val book = withContext(IO) {
                        io.legado.app.data.appDb.bookDao.getBook(name, author)
                    }
                    when {
                        book != null -> this@mainEntryProvider.startActivityForBook(book)
                        else -> context.toastOnUi("鎵句笉鍒板搴斾功鏈垨缃戦〉")
                    }
                }
            }
        )
    }

    entry<MainRouteBookCharacterDetail>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) { route ->
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var pendingAvatarUri by rememberSaveable { mutableStateOf<String?>(null) }
        val viewModel = koinViewModel<BookCharacterDetailViewModel>(
            key = "BookCharacterDetail:${route.bookUrl}:${route.characterId.orEmpty()}",
            parameters = { parametersOf(route.bookUrl, route.characterId) }
        )
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        val imagePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            pendingAvatarUri = uri?.toString()
        }
        BookCharacterDetailScreen(
            state = state,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
            onPickAvatar = { imagePicker.launch(arrayOf("image/*")) },
        )
        CharacterAvatarCropDialog(
            sourceUri = pendingAvatarUri?.let(Uri::parse),
            onDismissRequest = { pendingAvatarUri = null },
            onConfirm = { crop ->
                val sourceUri =
                    pendingAvatarUri?.let(Uri::parse) ?: return@CharacterAvatarCropDialog
                pendingAvatarUri = null
                scope.launch {
                    runCatching {
                        withContext(IO) {
                            saveCharacterAvatar(context, sourceUri, crop)
                        }
                    }.onSuccess { avatarUri ->
                        deleteCharacterAvatar(context, state.avatarUri)
                        viewModel.onIntent(CharacterDetailIntent.SetAvatarUri(avatarUri))
                    }.onFailure {
                        context.toastOnUi(
                            it.localizedMessage ?: context.getString(R.string.save_failed)
                        )
                    }
                }
            },
        )
    }

    entry<MainRouteBookCharacterNetwork>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) { route ->
        val viewModel = koinViewModel<BookCharacterNetworkViewModel>(
            key = "BookCharacterNetwork:${route.bookUrl}",
            parameters = { parametersOf(route.bookUrl) }
        )
        BookCharacterNetworkScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
            onOpenCharacterDetail = { characterId ->
                onNavigateToRoute(MainRouteBookCharacterDetail(route.bookUrl, characterId))
            },
            onRefresh = viewModel::refresh,
        )
    }

    entry<MainRouteBookCharacterList>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) { route ->
        val viewModel = koinViewModel<BookCharacterListViewModel>(
            key = "CharacterList:${route.bookUrl}",
            parameters = { parametersOf(route.bookUrl) }
        )
        BookCharacterListScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
            onOpenDetail = { characterId ->
                onNavigateToRoute(MainRouteBookCharacterDetail(route.bookUrl, characterId))
            },
            onRefresh = viewModel::refresh,
        )
    }

    entry<MainRouteBookVoiceCasting> { route ->
        val viewModel = koinViewModel<BookVoiceCastingViewModel>(
            key = "BookVoiceCasting:${route.bookUrl}",
            parameters = { parametersOf(route.bookUrl) },
        )
        BookVoiceCastingScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
            onManageCloudTts = { onNavigateToRoute(MainRouteCloudTtsEngines(route.bookUrl)) },
        )
    }

    entry<MainRouteCloudTtsEngines> { route ->
        val viewModel = koinViewModel<CloudTtsViewModel>()
        LaunchedEffect(route.bookUrl) {
            viewModel.onIntent(CloudTtsIntent.SetBookContext(route.bookUrl))
            // SPEC-119: this route always lands on 寮曟搸閫夐」.
            viewModel.onIntent(CloudTtsIntent.SelectTab(CloudTtsTab.Engines))
        }
        val context = LocalContext.current
        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri -> uri?.let { viewModel.onIntent(CloudTtsIntent.ImportHttpTtsFileSelected(it)) } }
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri -> uri?.let { viewModel.onIntent(CloudTtsIntent.ExportHttpTtsFileSelected(it)) } }
        LaunchedEffect(viewModel) {
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    CloudTtsEffect.OpenHttpTtsImportPicker -> importLauncher.launch(
                        arrayOf("application/json", "text/plain")
                    )

                    CloudTtsEffect.OpenHttpTtsExportPicker -> exportLauncher.launch("httpTTS.json")
                    is CloudTtsEffect.OpenHttpTtsLogin -> onNavigateToRoute(
                        MainRouteSourceLogin(
                            type = SourceLoginType.HttpTts,
                            sourceKey = effect.engineId.toString(),
                        )
                    )

                    else -> Unit
                }
            }
        }
        CloudTtsScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
        )
    }

    entry<MainRouteTtsCache> {
        TtsCacheRouteScreen(
            onBackClick = { onNavigateBack() },
        )
    }

    entry<MainRouteBookKnowledgeList>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) { route ->
        val viewModel = koinViewModel<BookKnowledgeListViewModel>(
            key = "KnowledgeList:${route.bookUrl}",
            parameters = { parametersOf(route.bookUrl) }
        )
        BookKnowledgeListScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
            onOpenDetail = { entryId ->
                onNavigateToRoute(MainRouteBookKnowledgeDetail(route.bookUrl, entryId))
            },
            onRefresh = viewModel::refresh,
        )
    }

    entry<MainRouteBookKnowledgeDetail>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) { route ->
        val viewModel = koinViewModel<BookKnowledgeDetailViewModel>(
            key = "KnowledgeDetail:${route.bookUrl}:${route.entryId.orEmpty()}",
            parameters = { parametersOf(route.bookUrl, route.entryId) }
        )
        BookKnowledgeDetailScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
        )
    }

    entry<MainRouteBookEventList>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) { route ->
        val viewModel = koinViewModel<BookEventListViewModel>(
            key = "EventList:${route.bookUrl}",
            parameters = { parametersOf(route.bookUrl) }
        )
        BookEventListScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
            onOpenDetail = { eventId ->
                onNavigateToRoute(MainRouteBookEventDetail(route.bookUrl, eventId))
            },
            onRefresh = viewModel::refresh,
        )
    }

    entry<MainRouteBookEventDetail>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) { route ->
        val viewModel = koinViewModel<BookEventDetailViewModel>(
            key = "EventDetail:${route.bookUrl}:${route.eventId.orEmpty()}",
            parameters = { parametersOf(route.bookUrl, route.eventId) }
        )
        BookEventDetailScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
        )
    }

    entry<MainRouteExploreShow> { route ->
        val exploreViewModel = koinViewModel<ExploreShowViewModel>()

        LaunchedEffect(route.sourceUrl, route.exploreUrl, exploreViewModel) {
            exploreViewModel.onIntent(
                ExploreShowIntent.InitData(route.sourceUrl, route.exploreUrl)
            )
        }

        ExploreShowRouteScreen(
            viewModel = exploreViewModel,
            title = route.title ?: "鎺㈢储",
            onBack = { onNavigateBack() },
            onBookClick = { book, _ ->
                startActivity(
                    MainActivity.createBookInfoIntent(
                        this@mainEntryProvider,
                        name = book.name,
                        author = book.author,
                        bookUrl = book.bookUrl,
                        origin = book.origin,
                        coverPath = book.coverUrl,
                    )
                )
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }

    entry<MainRouteHighlightTagRule>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) {
        HighlightTagRuleRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteAbout>(
        metadata = browserSlideOverlayMetadata(onBack = { onNavigateBack() }),
    ) {
        val viewModel = koinViewModel<AboutViewModel>()
        val context = LocalContext.current
        LaunchedEffect(viewModel) {
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    // SPEC-124/221: open in active Xunye tab, go Home route, switch pager to 浏览器.
                    is AboutEffect.OpenUrl -> {
                        io.legado.app.browser.engine.BrowserTabManager.openUrlInActive(effect.url)
                        io.legado.app.ui.main.MainShortcutBus.offer(
                            io.legado.app.ui.main.MainShortcutBus.Event.BrowserHome,
                        )
                        onNavigateToRoute(MainRouteHome)
                    }
                    is AboutEffect.ShowToast -> context.toastOnUi(effect.message)
                }
            }
        }
        AboutScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            onBack = { onNavigateBack() },
        )
    }
}

/** 鏈夊０涔︾殑 Android 瀹夸富鍔ㄤ綔锛涙挱鏀鹃〉涓庡惉涔﹂〉鍏辩敤鍚岀獥鍙ｅ舰鍙樼粍浠躲€?*/
@Composable
internal fun MainActivity.AudioPlayerMorphOverlay(
    bookUrl: String,
    inBookshelf: Boolean,
    morph: ReadAloudMorphState,
    visible: Boolean,
    awaitCapsuleAnchor: Boolean,
    predictiveBackEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    // 娴眰鍏抽棴鍚庨噴鏀鹃〉闈?VM锛涙挱鏀炬湇鍔″拰浼氳瘽缁х画淇濈暀锛岄噸鏂版墦寮€浼氶噸鏂板悓姝ヤ功绫嶃€?
    val playerStoreOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(playerStoreOwner) {
        onDispose { playerStoreOwner.viewModelStore.clear() }
    }
    val audioPlayViewModel = koinViewModel<AudioPlayViewModel>(
        key = "AudioPlay:${bookUrl}",
        viewModelStoreOwner = playerStoreOwner,
    )
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by audioPlayViewModel.uiState.collectAsStateWithLifecycle()
    var showAudioChangeSource by remember { mutableStateOf(false) }
    val sourceEditResult = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            audioPlayViewModel.onIntent(AudioPlayIntent.SourceEdited)
        }
    }

    fun copyAudioPlayUrl() {
        AudioPlay.book?.let {
            SourceCallBack.callBackBtn(
                this@AudioPlayerMorphOverlay,
                SourceCallBack.CLICK_COPY_PLAY_URL,
                AudioPlay.bookSource,
                it,
                AudioPlay.durChapter,
                BookType.audio,
                result = AudioPlayService.url,
            ) {
                sendToClip(AudioPlayService.url)
            }
        }
    }

    fun finishAudioPlay() {
        if (AudioPlay.inBookshelf) {
            SourceCallBack.callBackBook(
                SourceCallBack.END_READ,
                AudioPlay.bookSource,
                AudioPlay.book,
                AudioPlay.durChapter
            )
        }
        onDismiss()
    }

    LaunchedEffect(bookUrl, inBookshelf, audioPlayViewModel) {
        audioPlayViewModel.onIntent(
            AudioPlayIntent.Init(bookUrl.orEmpty(), inBookshelf)
        )
    }
    DisposableEffect(audioPlayViewModel, lifecycleOwner) {
        MainActivity.hasActiveAudioPlayRoute = true
        this@AudioPlayerMorphOverlay.activeAudioPlayViewModel = audioPlayViewModel
        AudioPlay.register(this@AudioPlayerMorphOverlay)
        onDispose {
            AudioPlay.unregister(this@AudioPlayerMorphOverlay)
            if (this@AudioPlayerMorphOverlay.activeAudioPlayViewModel === audioPlayViewModel) {
                this@AudioPlayerMorphOverlay.activeAudioPlayViewModel = null
            }
            MainActivity.hasActiveAudioPlayRoute = false
        }
    }
    LaunchedEffect(audioPlayViewModel) {
        audioPlayViewModel.effects.collectLatest { effect ->
            when (effect) {
                is AudioPlayEffect.OpenChangeSource -> showAudioChangeSource = true

                is AudioPlayEffect.OpenLogin -> startActivity(
                    MainActivity.createSourceLoginIntent(
                        this@AudioPlayerMorphOverlay,
                        SourceLoginType.BookSource,
                        effect.sourceUrl
                    )
                )

                AudioPlayEffect.CopyPlayUrl -> copyAudioPlayUrl()
                is AudioPlayEffect.OpenEditSource -> sourceEditResult.launch(
                    MainActivity.createBookSourceEditIntent(
                        this@AudioPlayerMorphOverlay,
                        effect.sourceUrl
                    )
                )

                is AudioPlayEffect.ShowToast -> toastOnUi(effect.message)
                is AudioPlayEffect.OpenBookReader -> {
                    startActivity(
                        MainActivity.createReadBookIntent(
                            this@AudioPlayerMorphOverlay,
                            effect.bookUrl
                        )
                    )
                    onDismiss()
                }

                AudioPlayEffect.Finish -> {
                    morph.animateTo(0f)
                    finishAudioPlay()
                }
            }
        }
    }
    PlayerMorphHost(
        appearance = PlayerMorphAppearance(
            uiState.bookName, uiState.author, uiState.coverPath,
            uiState.sourceOrigin, uiState.bgMode,
        ),
        playerTheme = rememberPlayerThemeOverride(
            uiState.bookName, uiState.author, uiState.coverPath, uiState.sourceOrigin,
        ),
        morph = morph,
        visible = visible,
        awaitCapsuleAnchor = awaitCapsuleAnchor,
        predictiveBackEnabled = predictiveBackEnabled,
        backEnabled = uiState.activeSheet == null && !showAudioChangeSource,
        onDismiss = ::finishAudioPlay,
    ) { onCollapse ->
        AudioPlayScreenContent(
            state = uiState,
            onIntent = audioPlayViewModel::onIntent,
            onBack = onCollapse,
        )
    }
    val audioBook = AudioPlay.book
    if (showAudioChangeSource && audioBook != null) {
        ChangeSourceSheet(
            show = true,
            oldBook = audioBook,
            onDismissRequest = { showAudioChangeSource = false },
            onReplace = { source, book, toc, _ ->
                audioPlayViewModel.changeTo(source, book, toc)
            },
            onAddAsNew = { book, toc ->
                audioPlayViewModel.addToBookshelf(book, toc)
            },
        )
    }
}
