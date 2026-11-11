package io.legado.app.ui.main

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import androidx.navigation3.runtime.NavKey
import io.legado.app.feature.reader.platform.ReaderPerfTrace
import io.legado.app.model.ReadBook
import io.legado.app.ui.rss.article.MainRouteRssSort
import io.legado.app.ui.rss.read.MainRouteRssRead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object MainNavigator {

    var backNavigationInProgress = false
        private set
    private val navigationScope by lazy(LazyThreadSafetyMode.NONE) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    private var backNavigationResetJob: Job? = null

    fun navigateToRoute(
        backStack: MutableList<NavKey>,
        route: NavKey,
        resetToHome: Boolean = false,
    ) {
        navigateToRoute(backStack, route, null, resetToHome)
    }

    /**
     * [tracker] 非空时在同一调用里记录栈顶路由：Activity 级叠层（全局朗读胶囊）据此
     * 在导航发生的那一刻就重算显隐，而不是等 back stack 快照回灌后才被动刷新。
     */
    fun navigateToRoute(
        backStack: MutableList<NavKey>,
        route: NavKey,
        tracker: MainNavRouteTracker?,
        resetToHome: Boolean = false,
    ) {
        navigateToRouteInternal(backStack, route, tracker, resetToHome)
    }

    private fun navigateToRouteInternal(
        backStack: MutableList<NavKey>,
        route: NavKey,
        tracker: MainNavRouteTracker?,
        resetToHome: Boolean,
    ) {
        if (resetToHome) {
            backStack.clear()
            backStack.add(MainRouteHome)
        }
        val currentRoute = backStack.lastOrNull()
        // SPEC-156: Settings 允许「已在栈顶」时走重挂分支，不能在此提前 return。
        if (currentRoute == route) return

        when (route) {
            is MainRouteSourceLogin -> {
                backStack.add(route)
            }

            is MainRouteWebView -> backStack.add(route)

            is MainRouteBookSourceManage,
            is MainRouteBookSourceEdit,
            MainRouteRssSourceManage,
            is MainRouteRssSourceEdit,
            is MainRouteBookSourceDebug,
            is MainRouteRssSourceDebug -> backStack.add(route)

            MainRouteReadAloudPlayer -> backStack.add(route)

            MainRouteHome -> {
                backStack.clear()
                backStack.add(MainRouteHome)
            }

            MainRouteAiChat -> {
                // SPEC-192: 设置族已搬入 SettingsActivity；AI 聊天从设置语境打开时
                // 以独立 start route 落在 Main，不再构造设置返回语境。
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteImportLocal,
            MainRouteImportRemote,
            is MainRouteCache,
            MainRouteBookCacheManage,
            MainRouteBrowserBookmarks,
            MainRouteBrowserDownloads,
            MainRouteBrowserHistory,
            MainRouteBrowserTabs -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute == MainRouteBrowserBookmarks ||
                    currentRoute == MainRouteBrowserDownloads ||
                    currentRoute == MainRouteBrowserHistory ||
                    currentRoute == MainRouteBrowserTabs
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteAudioPlay -> {
                // 播放器为单例语义：已在栈上（如通知栏再次进入）则替换，避免叠加多个播放界面
                val existingAudioIndex = backStack.indexOfLast { it is MainRouteAudioPlay }
                if (existingAudioIndex >= 0) {
                    while (backStack.lastIndex > existingAudioIndex) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                    backStack[existingAudioIndex] = route
                } else if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteSearchContent -> {
                backStack.add(route)
            }

            is MainRouteSearch -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteExploreShow ||
                    currentRoute is MainRouteBookSourceManage ||
                    currentRoute is MainRouteSearch
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteBookCharacterDetail,
            is MainRouteBookCharacterNetwork,
            is MainRouteBookCharacterList,
            is MainRouteBookVoiceCasting,
            is MainRouteCloudTtsEngines,
            MainRouteTtsCache,
            is MainRouteBookKnowledgeList,
            is MainRouteBookKnowledgeDetail,
            is MainRouteBookEventList,
            is MainRouteBookEventDetail -> {
                if (
                    currentRoute is MainRouteBookCharacterDetail ||
                    currentRoute is MainRouteBookCharacterNetwork ||
                    currentRoute is MainRouteBookCharacterList ||
                    currentRoute is MainRouteBookVoiceCasting ||
                    currentRoute is MainRouteCloudTtsEngines ||
                    currentRoute == MainRouteTtsCache ||
                    currentRoute is MainRouteBookKnowledgeList ||
                    currentRoute is MainRouteBookKnowledgeDetail ||
                    currentRoute is MainRouteBookEventList ||
                    currentRoute is MainRouteBookEventDetail
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteExploreShow -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteSearch ||
                    currentRoute is MainRouteExploreShow
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteRssSort -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteRssSort ||
                    currentRoute is MainRouteRssRead
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteRssRead -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteRssSort ||
                    currentRoute is MainRouteRssRead
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteRssFavorites,
            MainRouteRuleSub -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteHighlightTagRule,
            MainRouteReadRecord -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteAbout -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteReadRecordOverview -> {
                if (currentRoute == MainRouteHome || currentRoute == MainRouteReadRecord) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }
        }
        // 同步栈快照：Activity 级叠层（全局朗读胶囊）据此立刻重算显隐，不必等 back stack 回灌
        Log.i("Diag145", "push ${route::class.java.simpleName} -> ${diagStackSnapshot(backStack)}")
        tracker?.onBackStackChanged(backStack)
    }

    /** SPEC-144 TC-5 diag：栈大小 + 栈顶 3 个路由类名，用于定位页面错乱/闪退。 */
    private fun diagStackSnapshot(backStack: List<NavKey>): String {
        val top = backStack.asReversed().take(3)
            .joinToString("|") { it::class.java.simpleName }
        return "size=${backStack.size} top3=$top"
    }

    fun navigateBack(activity: Activity, backStack: MutableList<NavKey>) {
        navigateBack(activity, backStack, null)
    }

    /** [tracker] 非空时同步栈顶快照，供 Activity 级叠层立即重算显隐。 */
    fun navigateBack(
        activity: Activity,
        backStack: MutableList<NavKey>,
        tracker: MainNavRouteTracker?,
    ) {
        if (backNavigationInProgress) {
            return
        }
        if (backStack.size > 1) {
            // SPEC-124 diag: back press → next rendered frame latency.
            val t0 = SystemClock.uptimeMillis()
            Log.i("Diag124", "back pressed (stack=${backStack.size})")
            Choreographer.getInstance().postFrameCallback {
                Log.i("Diag124", "first frame after back: +${SystemClock.uptimeMillis() - t0}ms")
            }
            backNavigationInProgress = true
            backStack.removeLastOrNull()
            Log.i("Diag145", "pop -> ${diagStackSnapshot(backStack)}")
            tracker?.onBackStackChanged(backStack)
        } else {
            activity.finish()
        }
    }

    fun onBackStackChanged() {
        backNavigationResetJob?.cancel()
        backNavigationResetJob = navigationScope.launch {
            // SPEC-147 R10: 500ms 曾吞掉快速连续的返回手势（页面已被手势滑出屏幕
            // 却未出栈，需再滑一次）。收紧到 300ms——恰好覆盖滑入过渡时长；
            // 预测性手势 commit 路径另有 resetBackNavigationGuard() 旁路。
            delay(300)
            backNavigationInProgress = false
        }
    }

    /** SPEC-147 R10: 预测性返回手势 commit 是明确的用户意图，pop 前复位防抖，
     * 避免上一页导航遗留的防抖窗口吞掉本次 commit（页面已滑出屏幕却不 pop）。 */
    fun resetBackNavigationGuard() {
        backNavigationResetJob?.cancel()
        backNavigationResetJob = null
        backNavigationInProgress = false
    }

    fun resolveStartRoute(intent: Intent?): NavKey {
        val route = intent?.getStringExtra(MainIntent.EXTRA_START_ROUTE)
        resolveRssStartRoute(route, intent)?.let { return it }
        return resolveStartRoute(route, intent)
    }

    private fun resolveRssStartRoute(route: String?, intent: Intent?): NavKey? {
        return when (route) {
            MainRouteConst.ROUTE_RSS_SORT -> {
                val sourceUrl = intent?.getStringExtra(MainIntent.EXTRA_RSS_SOURCE_URL)
                if (sourceUrl.isNullOrBlank()) {
                    null
                } else {
                    MainRouteRssSort(
                        sourceUrl = sourceUrl,
                        sortUrl = intent.getStringExtra(MainIntent.EXTRA_RSS_SORT_URL),
                        key = intent.getStringExtra(MainIntent.EXTRA_RSS_KEY)
                    )
                }
            }

            MainRouteConst.ROUTE_RSS_READ -> {
                val origin = intent?.getStringExtra(MainIntent.EXTRA_RSS_READ_ORIGIN)
                if (origin.isNullOrBlank()) {
                    null
                } else {
                    MainRouteRssRead(
                        title = intent.getStringExtra(MainIntent.EXTRA_RSS_READ_TITLE),
                        origin = origin,
                        link = intent.getStringExtra(MainIntent.EXTRA_RSS_READ_LINK),
                        openUrl = intent.getStringExtra(MainIntent.EXTRA_RSS_READ_OPEN_URL)
                    )
                }
            }

            MainRouteConst.ROUTE_RSS_FAVORITES -> MainRouteRssFavorites

            MainRouteConst.ROUTE_RULE_SUB -> MainRouteRuleSub

            else -> null
        }
    }

    private fun resolveStartRoute(route: String?, intent: Intent?): MainRoute {
        return when (route) {
            MainRouteConst.ROUTE_MAIN -> MainRouteHome
            MainRouteConst.ROUTE_SOURCE_LOGIN -> MainRouteSourceLogin(
                type = intent?.getStringExtra(MainIntent.EXTRA_SOURCE_LOGIN_TYPE)
                    ?.let { runCatching { io.legado.app.ui.login.SourceLoginType.valueOf(it) }.getOrNull() }
                    ?: io.legado.app.ui.login.SourceLoginType.BookSource,
                sourceKey = intent?.getStringExtra(MainIntent.EXTRA_SOURCE_LOGIN_KEY),
                bookUrl = intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL),
            )

            MainRouteConst.ROUTE_WEB_VIEW -> intent?.getStringExtra(MainIntent.EXTRA_WEB_VIEW_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { url ->
                    MainRouteWebView(
                        title = intent.getStringExtra(MainIntent.EXTRA_WEB_VIEW_TITLE),
                        url = url,
                        sourceOrigin = intent.getStringExtra(MainIntent.EXTRA_WEB_VIEW_SOURCE_ORIGIN),
                        sourceName = intent.getStringExtra(MainIntent.EXTRA_WEB_VIEW_SOURCE_NAME),
                        sourceType = if (intent.hasExtra(MainIntent.EXTRA_WEB_VIEW_SOURCE_TYPE)) {
                            intent.getIntExtra(MainIntent.EXTRA_WEB_VIEW_SOURCE_TYPE, 0)
                        } else null,
                        sourceVerificationEnable = intent.getBooleanExtra(
                            MainIntent.EXTRA_WEB_VIEW_VERIFICATION, false
                        ),
                        refetchAfterSuccess = intent.getBooleanExtra(
                            MainIntent.EXTRA_WEB_VIEW_REFETCH, true
                        ),
                        html = intent.getStringExtra(MainIntent.EXTRA_WEB_VIEW_HTML),
                    )
                } ?: MainRouteHome

            MainRouteConst.ROUTE_BOOK_SOURCE_MANAGE -> MainRouteBookSourceManage(
                intent?.getStringExtra(MainIntent.EXTRA_BOOK_SOURCE_IMPORT)
            )
            MainRouteConst.ROUTE_BOOK_SOURCE_EDIT -> MainRouteBookSourceEdit(
                intent?.getStringExtra(MainIntent.EXTRA_SOURCE_URL)
            )

            MainRouteConst.ROUTE_RSS_SOURCE_MANAGE -> MainRouteRssSourceManage
            MainRouteConst.ROUTE_RSS_SOURCE_EDIT -> MainRouteRssSourceEdit(
                intent?.getStringExtra(MainIntent.EXTRA_SOURCE_URL)
            )

            MainRouteConst.ROUTE_BOOK_SOURCE_DEBUG -> MainRouteBookSourceDebug(
                intent?.getStringExtra(MainIntent.EXTRA_SOURCE_URL)
            )

            MainRouteConst.ROUTE_RSS_SOURCE_DEBUG -> MainRouteRssSourceDebug(
                intent?.getStringExtra(MainIntent.EXTRA_SOURCE_URL)
            )
            MainRouteConst.ROUTE_SETTINGS -> MainRouteSettings
            MainRouteConst.ROUTE_SETTINGS_OTHER -> MainRouteSettingsOther
            MainRouteConst.ROUTE_SETTINGS_READ -> MainRouteSettingsRead
            MainRouteConst.ROUTE_SETTINGS_COVER -> MainRouteSettingsCover
            MainRouteConst.ROUTE_SETTINGS_THEME -> MainRouteSettingsTheme
            MainRouteConst.ROUTE_SETTINGS_BACKUP -> MainRouteSettingsBackup
            MainRouteConst.ROUTE_SETTINGS_AI -> MainRouteSettingsAi
            MainRouteConst.ROUTE_SETTINGS_AI_SUMMARY -> MainRouteSettingsAiSummary
            MainRouteConst.ROUTE_SETTINGS_AI_PROMPT -> MainRouteSettingsAiPrompt
            MainRouteConst.ROUTE_SETTINGS_AI_USAGE -> MainRouteSettingsAiUsage
            MainRouteConst.ROUTE_AI_CHAT -> MainRouteAiChat
            MainRouteConst.ROUTE_SETTINGS_CUSTOM_THEME -> MainRouteSettingsCustomTheme
            MainRouteConst.ROUTE_SETTINGS_PRIVATE -> MainRouteSettingsPrivate
            MainRouteConst.ROUTE_SETTINGS_BROWSER -> MainRouteSettingsBrowser
            MainRouteConst.ROUTE_SETTINGS_THEME_MANAGE -> MainRouteSettingsThemeManage
            MainRouteConst.ROUTE_BROWSER_PRIVACY -> MainRouteBrowserPrivacy
            MainRouteConst.ROUTE_BROWSER_PICK_BLOCK -> MainRouteBrowserPickBlock
            MainRouteConst.ROUTE_BROWSER_CLEAR_DATA -> MainRouteBrowserClearData
            MainRouteConst.ROUTE_SETTINGS_DOWNLOAD_CACHE -> MainRouteSettingsDownloadCache
            MainRouteConst.ROUTE_SETTINGS_TRANSLATION -> MainRouteSettingsTranslation
            MainRouteConst.ROUTE_IMPORT_LOCAL -> MainRouteImportLocal()
            MainRouteConst.ROUTE_IMPORT_REMOTE -> MainRouteImportRemote
            MainRouteConst.ROUTE_CACHE -> MainRouteCache(
                intent?.getLongExtra(
                    MainIntent.EXTRA_CACHE_GROUP_ID,
                    -1L
                ) ?: -1L
            )

            MainRouteConst.ROUTE_BOOK_CACHE_MANAGE -> MainRouteBookCacheManage
            // SPEC-190: 阅读器不再作为 MainActivity 路由；其二级目的地经独立 MainActivity
            // 实例的 start route 打开。
            MainRouteConst.ROUTE_SEARCH_CONTENT -> MainRouteSearchContent(
                bookUrl = intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL).orEmpty(),
                searchWord = intent?.getStringExtra(MainIntent.EXTRA_SEARCH_KEY),
                searchResultIndex = intent?.getIntExtra(MainIntent.EXTRA_SEARCH_RESULT_INDEX, 0) ?: 0,
                autoFocus = intent?.getBooleanExtra(MainIntent.EXTRA_SEARCH_AUTO_FOCUS, true) != false,
            )
            MainRouteConst.ROUTE_BOOK_VOICE_CASTING -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { MainRouteBookVoiceCasting(bookUrl = it) }
                ?: MainRouteHome
            MainRouteConst.ROUTE_CLOUD_TTS_ENGINES -> MainRouteCloudTtsEngines(
                intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
            )
            MainRouteConst.ROUTE_TTS_CACHE -> MainRouteTtsCache
            MainRouteConst.ROUTE_AUDIO_PLAY -> MainRouteAudioPlay(
                bookUrl = intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL),
                inBookshelf = intent?.getBooleanExtra(MainIntent.EXTRA_IN_BOOKSHELF, true) != false,
            )
            MainRouteConst.ROUTE_SEARCH -> MainRouteSearch(
                key = intent?.getStringExtra(MainIntent.EXTRA_SEARCH_KEY),
                scopeRaw = intent?.getStringExtra(MainIntent.EXTRA_SEARCH_SCOPE)
            )

            // SPEC-193: ROUTE_BOOK_INFO 移除——详情经 BookInfoActivity 承载，
            // 不再走 Main 的 start-route 解析（旧外链回落主页）。

            MainRouteConst.ROUTE_BOOK_CHARACTER_DETAIL -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookCharacterDetail(
                        bookUrl = bookUrl,
                        characterId = intent.getStringExtra(MainIntent.EXTRA_CHARACTER_ID),
                    )
                } ?: MainRouteHome

            MainRouteConst.ROUTE_BOOK_CHARACTER_NETWORK -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookCharacterNetwork(bookUrl = bookUrl)
                } ?: MainRouteHome

            MainRouteConst.ROUTE_BOOK_CHARACTER_LIST -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookCharacterList(bookUrl = bookUrl)
                } ?: MainRouteHome

            MainRouteConst.ROUTE_BOOK_KNOWLEDGE_LIST -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookKnowledgeList(bookUrl = bookUrl)
                } ?: MainRouteHome

            MainRouteConst.ROUTE_BOOK_KNOWLEDGE_DETAIL -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookKnowledgeDetail(
                        bookUrl = bookUrl,
                        entryId = intent.getStringExtra(MainIntent.EXTRA_ENTRY_ID),
                    )
                } ?: MainRouteHome

            MainRouteConst.ROUTE_BOOK_EVENT_LIST -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookEventList(bookUrl = bookUrl)
                } ?: MainRouteHome

            MainRouteConst.ROUTE_BOOK_EVENT_DETAIL -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookEventDetail(
                        bookUrl = bookUrl,
                        eventId = intent.getStringExtra(MainIntent.EXTRA_EVENT_ID),
                    )
                } ?: MainRouteHome

            MainRouteConst.ROUTE_EXPLORE_SHOW -> intent?.getStringExtra(MainIntent.EXTRA_SOURCE_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { sourceUrl ->
                    MainRouteExploreShow(
                        title = intent.getStringExtra(MainIntent.EXTRA_EXPLORE_NAME),
                        sourceUrl = sourceUrl,
                        exploreUrl = intent.getStringExtra(MainIntent.EXTRA_EXPLORE_URL),
                    )
                } ?: MainRouteHome

            MainRouteConst.ROUTE_ABOUT -> MainRouteAbout

            MainRouteConst.ROUTE_BROWSER_DOWNLOADS -> MainRouteBrowserDownloads
            MainRouteConst.ROUTE_BROWSER_BOOKMARKS -> MainRouteBrowserBookmarks

            else -> MainRouteHome
        }
    }
}
