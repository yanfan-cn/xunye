package io.legado.app.browser.engine

import android.content.Context

/** SPEC-070: persist reorderable toolbox grid item ids. */
class BrowserToolboxPrefs(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun orderedIds(): List<String> {
        val raw = prefs.getString(KEY_ORDER, null)?.trim().orEmpty()
        if (raw.isEmpty()) return DEFAULT_ORDER
        val saved = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val known = DEFAULT_ORDER.toSet()
        val kept = saved.filter { it in known }
        val missing = DEFAULT_ORDER.filter { it !in kept }
        return kept + missing
    }

    fun saveOrder(ids: List<String>) {
        val known = DEFAULT_ORDER.toSet()
        val cleaned = ids.filter { it in known }.distinct()
        val missing = DEFAULT_ORDER.filter { it !in cleaned }
        prefs.edit().putString(KEY_ORDER, (cleaned + missing).joinToString(",")).apply()
    }

    companion object {
        private const val PREFS = "browsesth_toolbox_prefs"
        private const val KEY_ORDER = "grid_order_v1"

        /** Flexible (non-pinned) items; customize action is always appended in UI. */
        val DEFAULT_ORDER = listOf(
            "new_incognito",
            "add_bookmark",
            // SPEC-251: in-page webpage translate.
            "translate",
            "save_page",
            "share",
            "copy_link",
            "pick_block",
            "video_capsule",
            "ad_block",
            "ua_toggle",
            "eye_protection",
            "text_zoom",
            "find_in_page",
            // SPEC-242: hide browser chrome; restore via CloseFullscreen FAB.
            "fullscreen",
        )
    }
}