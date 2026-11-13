package io.legado.app.browser.engine

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tablet chrome / UA / wallpaper prefs (SPEC-018). */
class BrowserChromePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        // Keep reactive snapshot in sync for Compose collectors (SPEC-208).
        HideBrowserChromeInReaderState.publish(prefs.getBoolean(KEY_HIDE_CHROME_IN_READER, false))
    }

    /** null = follow form-factor default. */
    var uaMode: BrowserUa.Mode?
        get() = BrowserUa.Mode.fromStorage(prefs.getString(KEY_UA, null))
        set(value) {
            prefs.edit().putString(KEY_UA, value?.storage).apply()
        }

    fun uaModeOrDefault(isTablet: Boolean): BrowserUa.Mode =
        uaMode ?: if (isTablet) BrowserUa.Mode.Desktop else BrowserUa.Mode.Mobile

    var autoHideTopBar: Boolean
        get() = prefs.getBoolean(KEY_AUTO_HIDE, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_HIDE, value).apply()

    /** SPEC-223: default off for new installs. */
    var dailyWallpaperEnabled: Boolean
        get() = prefs.getBoolean(KEY_WALLPAPER, false)
        set(value) = prefs.edit().putBoolean(KEY_WALLPAPER, value).apply()

    var wallpaperUrl: String
        get() = prefs.getString(KEY_WALLPAPER_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_WALLPAPER_URL, value).apply()

    var wallpaperDate: String
        get() = prefs.getString(KEY_WALLPAPER_DATE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_WALLPAPER_DATE, value).apply()

    /**
     * SPEC-043: webpage Force Dark — off | auto | on
     */
    var webContentDarkMode: String
        get() = prefs.getString(KEY_WEB_DARK, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_WEB_DARK, value).apply()

    /**
     * SPEC-107: webpage text zoom percent (Chrome/Edge accessibility-style).
     * 100 = default; range enforced by [AwContentsHostView.applyTextZoom].
     */
    var textZoomPercent: Int
        get() = prefs.getInt(KEY_TEXT_ZOOM, 100).coerceIn(TEXT_ZOOM_MIN, TEXT_ZOOM_MAX)
        set(value) = prefs.edit()
            .putInt(KEY_TEXT_ZOOM, value.coerceIn(TEXT_ZOOM_MIN, TEXT_ZOOM_MAX))
            .apply()

    /** SPEC-119: custom download directory absolute path; blank = default. */
    var downloadDirPath: String
        get() = prefs.getString(KEY_DOWNLOAD_DIR, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DOWNLOAD_DIR, value).apply()

    /** SPEC-119: ask user before each download starts. */
    var askBeforeDownload: Boolean
        get() = prefs.getBoolean(KEY_ASK_DOWNLOAD, false)
        set(value) = prefs.edit().putBoolean(KEY_ASK_DOWNLOAD, value).apply()

    /**
     * SPEC-178: when true and the system 「所有文件访问」 grant is present,
     * player videos write to public Movies/XunYeBrowser. Default false → MediaStore.
     */
    var preferAllFilesVideoDownload: Boolean
        get() = prefs.getBoolean(KEY_PREFER_ALL_FILES_VIDEO, false)
        set(value) = prefs.edit().putBoolean(KEY_PREFER_ALL_FILES_VIDEO, value).apply()

    /** SPEC-120: Chrome-like force enable zoom; default ON. */
    var forcePageZoom: Boolean
        get() = prefs.getBoolean(KEY_FORCE_ZOOM, true)
        set(value) = prefs.edit().putBoolean(KEY_FORCE_ZOOM, value).apply()

    /** SPEC-120: accept cookies. */
    var acceptCookies: Boolean
        get() = prefs.getBoolean(KEY_ACCEPT_COOKIES, true)
        set(value) = prefs.edit().putBoolean(KEY_ACCEPT_COOKIES, value).apply()

    /** SPEC-120: third-party cookies. */
    var acceptThirdPartyCookies: Boolean
        get() = prefs.getBoolean(KEY_ACCEPT_3P_COOKIES, true)
        set(value) = prefs.edit().putBoolean(KEY_ACCEPT_3P_COOKIES, value).apply()

    /** SPEC-120: Safe Browsing when supported. */
    var safeBrowsingEnabled: Boolean
        get() = prefs.getBoolean(KEY_SAFE_BROWSING, true)
        set(value) = prefs.edit().putBoolean(KEY_SAFE_BROWSING, value).apply()

    /** SPEC-122: restore open tabs on launch; default ON. */
    var restoreSessionOnLaunch: Boolean
        get() = prefs.getBoolean(KEY_RESTORE_SESSION, true)
        set(value) = prefs.edit().putBoolean(KEY_RESTORE_SESSION, value).apply()

    /**
     * SPEC-208: tablet shell embedded reader — hide browser tab+omnibox chrome.
     * Default OFF. Phone standalone reader ignores this.
     */
    var hideBrowserChromeInReader: Boolean
        get() = prefs.getBoolean(KEY_HIDE_CHROME_IN_READER, false)
        set(value) {
            prefs.edit().putBoolean(KEY_HIDE_CHROME_IN_READER, value).apply()
            HideBrowserChromeInReaderState.publish(value)
        }

    /**
     * SPEC-132: show bottom-chrome prompt 「用播放器打开」 when a playable stream
     * is detected. Default ON.
     */
    var videoPlayerPromptEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIDEO_PLAYER_PROMPT, true)
        set(value) = prefs.edit().putBoolean(KEY_VIDEO_PLAYER_PROMPT, value).apply()

    /**
     * SPEC-224: true = 有视频时直接显示净视悬浮球；false = 先完整胶囊再按时收球。
     * 默认 true。
     */
    var videoFloatingBallPreferred: Boolean
        get() = prefs.getBoolean(KEY_VIDEO_FLOATING_BALL, true)
        set(value) = prefs.edit().putBoolean(KEY_VIDEO_FLOATING_BALL, value).apply()

    /** SPEC-158: selected homepage background. Default is the light-blue gradient. */
    var ntpWallpaperId: String
        get() = prefs.getString(KEY_NTP_WALLPAPER_ID, NtpWallpaperIds.DEFAULT)
            ?.takeIf { it.isNotBlank() }
            ?: NtpWallpaperIds.DEFAULT
        set(value) = prefs.edit().putString(KEY_NTP_WALLPAPER_ID, value).apply()

    /** SPEC-266: true only after an id was explicitly written (seed or user pick). */
    val hasNtpWallpaperId: Boolean
        get() = prefs.contains(KEY_NTP_WALLPAPER_ID)

    /** SPEC-158: user-imported wallpaper ids, one per line. */
    var ntpUserWallpaperIds: List<String>
        get() = prefs.getString(KEY_NTP_USER_WALLS, "").orEmpty()
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        set(value) = prefs.edit()
            .putString(KEY_NTP_USER_WALLS, value.joinToString("\n"))
            .apply()

    companion object {
        private const val PREFS = "browsesth_browser_chrome"
        private const val KEY_UA = "ua_mode"
        private const val KEY_AUTO_HIDE = "auto_hide_top_bar"
        private const val KEY_WALLPAPER = "daily_wallpaper"
        private const val KEY_WALLPAPER_URL = "wallpaper_url"
        private const val KEY_WALLPAPER_DATE = "wallpaper_date"
        private const val KEY_WEB_DARK = "web_content_dark_mode"
        private const val KEY_TEXT_ZOOM = "text_zoom_percent"
        private const val KEY_DOWNLOAD_DIR = "download_dir_path"
        private const val KEY_ASK_DOWNLOAD = "ask_before_download"
        private const val KEY_PREFER_ALL_FILES_VIDEO = "prefer_all_files_video_download"
        private const val KEY_FORCE_ZOOM = "force_page_zoom"
        private const val KEY_ACCEPT_COOKIES = "accept_cookies"
        private const val KEY_ACCEPT_3P_COOKIES = "accept_third_party_cookies"
        private const val KEY_SAFE_BROWSING = "safe_browsing_enabled"
        private const val KEY_RESTORE_SESSION = "restore_session_on_launch"
        private const val KEY_HIDE_CHROME_IN_READER = "hide_browser_chrome_in_reader"
        private const val KEY_VIDEO_PLAYER_PROMPT = "video_player_prompt_enabled"
        private const val KEY_VIDEO_FLOATING_BALL = "video_floating_ball_preferred"
        private const val KEY_NTP_WALLPAPER_ID = "ntp_wallpaper_id"
        private const val KEY_NTP_USER_WALLS = "ntp_user_wallpapers"

        const val WEB_DARK_OFF = "off"
        const val WEB_DARK_AUTO = "auto"
        const val WEB_DARK_ON = "on"

        const val TEXT_ZOOM_MIN = 50
        const val TEXT_ZOOM_MAX = 200
        const val TEXT_ZOOM_STEP = 10

        /** SPEC-259: system night for resolving「跟随系统」→ Force Dark ON/OFF. */
        fun isSystemNightMode(context: Context): Boolean {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return night == Configuration.UI_MODE_NIGHT_YES
        }

        /**
         * SPEC-259: map toolbox pref to effective off/on.
         * AUTO follows [nightMode] instead of Chromium FORCE_DARK_AUTO (parent-view dependent).
         */
        fun resolveWebContentDarkMode(mode: String, nightMode: Boolean): String =
            when (mode) {
                WEB_DARK_OFF -> WEB_DARK_OFF
                WEB_DARK_ON -> WEB_DARK_ON
                else -> if (nightMode) WEB_DARK_ON else WEB_DARK_OFF
            }
    }
}
