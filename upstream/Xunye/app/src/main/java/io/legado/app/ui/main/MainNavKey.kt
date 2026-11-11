package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import io.legado.app.ui.login.SourceLoginType
import kotlinx.serialization.Serializable

@Serializable
sealed interface MainRoute : NavKey

@Serializable
data object MainRouteHome : MainRoute

/** 只兼容旧版本保存的导航栈，新入口使用 Activity 播放浮层。 */
@Serializable
data object MainRouteReadAloudPlayer : MainRoute

@Serializable
data class MainRouteSourceLogin(
    val type: SourceLoginType,
    val sourceKey: String? = null,
    val bookUrl: String? = null,
) : MainRoute

@Serializable
data class MainRouteWebView(
    val title: String? = null,
    val url: String,
    val sourceOrigin: String? = null,
    val sourceName: String? = null,
    val sourceType: Int? = null,
    val sourceVerificationEnable: Boolean = false,
    val refetchAfterSuccess: Boolean = true,
    val html: String? = null,
) : MainRoute

@Serializable
data class MainRouteBookSourceManage(
    val importUrl: String? = null,
) : MainRoute

@Serializable
data class MainRouteBookSourceEdit(val sourceUrl: String? = null) : MainRoute

@Serializable
data object MainRouteRssSourceManage : MainRoute

@Serializable
data class MainRouteRssSourceEdit(val sourceUrl: String? = null) : MainRoute

@Serializable
data class MainRouteBookSourceDebug(val sourceUrl: String? = null) : MainRoute

@Serializable
data class MainRouteRssSourceDebug(val sourceUrl: String? = null) : MainRoute

@Serializable
data object MainRouteSettings : MainRoute

@Serializable
data object MainRouteSettingsBrowser : MainRoute

@Serializable
data object MainRouteBrowserBookmarks : MainRoute

@Serializable
data object MainRouteBrowserDownloads : MainRoute

@Serializable
data object MainRouteBrowserHistory : MainRoute

/** SPEC-082: clear browsing data (from history). */
@Serializable
data object MainRouteBrowserClearData : MainRoute

/** SPEC-120: browser privacy / security settings page. */
@Serializable
data object MainRouteBrowserPrivacy : MainRoute

/** SPEC-134: pick-to-block rules management page. */
@Serializable
data object MainRouteBrowserPickBlock : MainRoute

@Serializable
data object MainRouteBrowserTabs : MainRoute

@Serializable
data object MainRouteSettingsOther : MainRoute

@Serializable
data object MainRouteSettingsRead : MainRoute

@Serializable
data object MainRouteSettingsCover : MainRoute

@Serializable
data object MainRouteSettingsCoverAlbums : MainRoute

@Serializable
data object MainRouteSettingsTheme : MainRoute

@Serializable
data object MainRouteSettingsBackup : MainRoute

@Serializable
data object MainRouteSettingsAi : MainRoute

@Serializable
data object MainRouteAiChat : MainRoute

@Serializable
data class MainRouteSettingsAiProviderEdit(
    val providerId: String? = null
) : MainRoute

@Serializable
data class MainRouteSettingsAiModelEdit(
    val providerId: String? = null,
    val modelProfileId: String? = null
) : MainRoute

@Serializable
data object MainRouteSettingsAiSummary : MainRoute

@Serializable
data object MainRouteSettingsAiPrompt : MainRoute

/** SPEC-262 */
@Serializable
data object MainRouteSettingsAiUsage : MainRoute

@Serializable
data object MainRouteSettingsCustomTheme : MainRoute

@Serializable
data object MainRouteSettingsThemeManage : MainRoute

@Serializable
data object MainRouteSettingsPrivate : MainRoute

@Serializable
data object MainRouteSettingsDownloadCache : MainRoute

@Serializable
data object MainRouteSettingsTranslation : MainRoute

/** [autoPick]: null 仅打开导入页；`folder` / `books` 进入后自动弹出对应系统选择器（SPEC-205）。 */
@Serializable
data class MainRouteImportLocal(val autoPick: String? = null) : MainRoute {
    companion object {
        const val AUTO_PICK_FOLDER = "folder"
        const val AUTO_PICK_BOOKS = "books"
    }
}

@Serializable
data object MainRouteImportRemote : MainRoute

@Serializable
data object MainRouteReadRecord : MainRoute

@Serializable
data object MainRouteReadRecordOverview : MainRoute

@Serializable
data class MainRouteCache(val groupId: Long) : MainRoute

@Serializable
data object MainRouteBookCacheManage : MainRoute

@Serializable
data class MainRouteAudioPlay(
    val bookUrl: String? = null,
    val inBookshelf: Boolean = true,
    val sharedCoverKey: String? = null,
) : MainRoute

@Serializable
data class MainRouteSearch(
    val key: String?,
    val scopeRaw: String? = null
) : MainRoute

@Serializable
data class MainRouteBookCharacterDetail(
    val bookUrl: String,
    val characterId: String? = null,
) : MainRoute

@Serializable
data class MainRouteBookCharacterNetwork(
    val bookUrl: String,
) : MainRoute

@Serializable
data class MainRouteBookCharacterList(
    val bookUrl: String,
) : MainRoute

@Serializable
data class MainRouteBookVoiceCasting(
    val bookUrl: String,
) : MainRoute

@Serializable
data class MainRouteCloudTtsEngines(val bookUrl: String? = null) : MainRoute

@Serializable
data object MainRouteTtsCache : MainRoute

@Serializable
data class MainRouteBookKnowledgeList(
    val bookUrl: String,
) : MainRoute

@Serializable
data class MainRouteBookKnowledgeDetail(
    val bookUrl: String,
    val entryId: String? = null,
) : MainRoute

@Serializable
data class MainRouteBookEventList(
    val bookUrl: String,
) : MainRoute

@Serializable
data class MainRouteBookEventDetail(
    val bookUrl: String,
    val eventId: String? = null,
) : MainRoute

@Serializable
data object MainRouteRssFavorites : MainRoute

@Serializable
data object MainRouteRuleSub : MainRoute

@Serializable
data class MainRouteExploreShow(
    val title: String?,
    val sourceUrl: String,
    val exploreUrl: String?,
) : MainRoute

@Serializable
data class MainRouteSearchContent(
    val bookUrl: String,
    val searchWord: String? = null,
    val searchResultIndex: Int = 0,
    val autoFocus: Boolean = true,
) : MainRoute

@Serializable
data object MainRouteHighlightTagRule : MainRoute

@Serializable
data object MainRouteAbout : MainRoute

object MainRouteConst {
    const val ROUTE_MAIN = "main"
    const val ROUTE_SOURCE_LOGIN = "source/login"
    const val ROUTE_WEB_VIEW = "web/view"
    const val ROUTE_BOOK_SOURCE_MANAGE = "source/book/manage"
    const val ROUTE_BOOK_SOURCE_EDIT = "source/book/edit"
    const val ROUTE_RSS_SOURCE_MANAGE = "source/rss/manage"
    const val ROUTE_RSS_SOURCE_EDIT = "source/rss/edit"
    const val ROUTE_BOOK_SOURCE_DEBUG = "source/book/debug"
    const val ROUTE_RSS_SOURCE_DEBUG = "source/rss/debug"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_SETTINGS_OTHER = "settings/other"
    const val ROUTE_SETTINGS_READ = "settings/read"
    const val ROUTE_SETTINGS_COVER = "settings/cover"
    const val ROUTE_SETTINGS_COVER_ALBUMS = "settings/cover/albums"
    const val ROUTE_SETTINGS_THEME = "settings/theme"
    const val ROUTE_SETTINGS_BACKUP = "settings/backup"
    const val ROUTE_SETTINGS_AI = "settings/ai"
    const val ROUTE_SETTINGS_AI_SUMMARY = "settings/ai/summary"
    const val ROUTE_SETTINGS_AI_PROMPT = "settings/ai/prompt"
    const val ROUTE_SETTINGS_AI_USAGE = "settings/ai/usage"
    const val ROUTE_AI_CHAT = "ai/chat"
    const val ROUTE_SETTINGS_CUSTOM_THEME = "settings/custom_theme"
    const val ROUTE_SETTINGS_PRIVATE = "settings/private"
    const val ROUTE_SETTINGS_BROWSER = "settings/browser"
    const val ROUTE_SETTINGS_THEME_MANAGE = "settings/theme_manage"
    const val ROUTE_BROWSER_PRIVACY = "browser/privacy"
    const val ROUTE_BROWSER_PICK_BLOCK = "browser/pick_block"
    const val ROUTE_BROWSER_CLEAR_DATA = "browser/clear_data"
    const val ROUTE_SETTINGS_DOWNLOAD_CACHE = "settings/download_cache"
    const val ROUTE_SETTINGS_TRANSLATION = "settings/translation"
    const val ROUTE_IMPORT_LOCAL = "import/local"
    const val ROUTE_IMPORT_REMOTE = "import/remote"
    const val ROUTE_CACHE = "cache"
    const val ROUTE_BOOK_CACHE_MANAGE = "book/cache/manage"
    const val ROUTE_AUDIO_PLAY = "book/read/audio"
    /** SPEC-190: 阅读器 Activity 二级目的地的 start-route 标识。 */
    const val ROUTE_BOOK_VOICE_CASTING = "book/voice_casting"
    const val ROUTE_CLOUD_TTS_ENGINES = "cloud/tts/engines"
    const val ROUTE_SEARCH = "search"
    const val ROUTE_SEARCH_CONTENT = "book/searchContent"
    const val ROUTE_BOOK_CHARACTER_DETAIL = "book/character/detail"
    const val ROUTE_BOOK_CHARACTER_NETWORK = "book/character/network"
    const val ROUTE_BOOK_CHARACTER_LIST = "book/character/list"
    const val ROUTE_BOOK_KNOWLEDGE_LIST = "book/knowledge/list"
    const val ROUTE_BOOK_KNOWLEDGE_DETAIL = "book/knowledge/detail"
    const val ROUTE_BOOK_EVENT_LIST = "book/event/list"
    const val ROUTE_BOOK_EVENT_DETAIL = "book/event/detail"
    const val ROUTE_EXPLORE_SHOW = "explore/show"
    const val ROUTE_RSS_SORT = "rss/sort"
    const val ROUTE_RSS_READ = "rss/read"
    const val ROUTE_RSS_FAVORITES = "rss/favorites"
    const val ROUTE_RULE_SUB = "rss/rule_sub"
    const val ROUTE_READ_RECORD = "read_record"
    const val ROUTE_READ_RECORD_OVERVIEW = "read_record_overview"
    const val ROUTE_ABOUT = "about"
    const val ROUTE_TTS_CACHE = "tts_cache"
    /** SPEC-064 */
    const val ROUTE_BROWSER_DOWNLOADS = "browser/downloads"
    /** SPEC-164 */
    const val ROUTE_BROWSER_BOOKMARKS = "browser/bookmarks"
}
