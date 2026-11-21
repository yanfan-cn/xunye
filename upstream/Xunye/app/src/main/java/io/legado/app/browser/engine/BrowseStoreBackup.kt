package io.legado.app.browser.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * SPEC-271: 浏览器「收藏（网页书签）/收藏文件夹/浏览历史」的备份文件读写与恢复合并。
 *
 * 这三类数据原先只存在 SharedPreferences `browsesth_browse_store`，备份包（Room JSON +
 * config.xml）完全没有它们，换机即丢。现在作为一个独立 JSON 进入备份 zip。
 *
 * 恢复策略是**合并**而不是覆盖：
 * - 已存在的 URL 一律保留本地版本（不覆盖用户新增/改名）；
 * - 备份里缺失的条目补进来；
 * - 文件夹 id 冲突时分配新 id 并重映射收藏归属，保证层级不丢。
 *
 * 所有 merge 逻辑为纯函数，便于 JVM 单测（见 `BrowseStoreBackupTest`）。
 */
object BrowseStoreBackup {

    const val FILE_NAME = "browserBrowse.json"
    private const val VERSION = 1

    const val MAX_HISTORY = 100
    const val MAX_BOOKMARKS = 1000

    data class Snapshot(
        val history: List<BrowseStore.Entry> = emptyList(),
        val bookmarks: List<BrowseStore.Entry> = emptyList(),
        val folders: List<BrowseStore.Folder> = emptyList(),
    )

    data class MergeResult(
        val snapshot: Snapshot,
        val addedHistory: Int = 0,
        val addedBookmarks: Int = 0,
        val addedFolders: Int = 0,
        val truncated: Boolean = false,
    )

    // ---- 编解码 ----

    fun encode(snapshot: Snapshot): String = JSONObject()
        .put("version", VERSION)
        .put("history", entriesToJson(snapshot.history))
        .put("bookmarks", entriesToJson(snapshot.bookmarks))
        .put("folders", foldersToJson(snapshot.folders))
        .toString()

    fun decode(raw: String): Snapshot? = runCatching {
        val root = JSONObject(raw)
        Snapshot(
            history = entriesFromJson(root.optJSONArray("history")),
            bookmarks = entriesFromJson(root.optJSONArray("bookmarks")),
            folders = foldersFromJson(root.optJSONArray("folders")),
        )
    }.getOrNull()

    fun write(file: File, snapshot: Snapshot) {
        file.parentFile?.mkdirs()
        file.writeText(encode(snapshot))
    }

    fun read(file: File): Snapshot? = runCatching {
        if (!file.isFile) null else decode(file.readText())
    }.getOrNull()

    // ---- 合并 ----

    /**
     * @param local 当前设备已有数据（保留优先）
     * @param remote 备份中的数据
     */
    fun merge(
        local: Snapshot,
        remote: Snapshot,
        maxHistory: Int = MAX_HISTORY,
        maxBookmarks: Int = MAX_BOOKMARKS,
    ): MergeResult {
        var truncated = false

        // 1) 文件夹：按「父在前」顺序处理，保证 parentId 映射可用。
        val folders = ArrayList(local.folders)
        val folderIdMap = HashMap<String, String>()
        var addedFolders = 0
        val pending = remote.folders.toMutableList()
        val byId = local.folders.associateBy { it.id }
        while (pending.isNotEmpty()) {
            val progressed = pending.toList()
            for (folder in progressed) {
                val mappedParent = when {
                    folder.parentId.isBlank() -> ""
                    folder.id in folderIdMap -> folderIdMap.getValue(folder.id)
                    folder.parentId.isEmpty() -> ""
                    folder.parentId in folderIdMap -> folderIdMap.getValue(folder.parentId)
                    byId.containsKey(folder.parentId) -> folder.parentId
                    else -> null
                }
                if (mappedParent == null) continue // 父级还没就绪，下一轮
                pending.remove(folder)
                val localSameId = folders.firstOrNull { it.id == folder.id }
                when {
                    // 同 id 且同名同父：确定是同一文件夹，沿用本地。
                    localSameId != null &&
                        localSameId.name == folder.name &&
                        localSameId.parentId == mappedParent -> {
                        folderIdMap[folder.id] = localSameId.id
                    }

                    mappedParent == folder.parentId &&
                        folders.any { it.parentId == mappedParent && it.name == folder.name } -> {
                        // 同一父级下已有同名文件夹 → 复用
                        folderIdMap[folder.id] =
                            folders.first { it.parentId == mappedParent && it.name == folder.name }.id
                    }

                    else -> {
                        // id 冲突但内容不同（或本地无此 id）→ 新建并重映射，绝不覆盖本地。
                        val newId = if (folders.none { it.id == folder.id }) {
                            folder.id
                        } else {
                            java.util.UUID.randomUUID().toString()
                        }
                        folders += folder.copy(
                            id = newId,
                            parentId = mappedParent,
                            order = (folders.maxOfOrNull { it.order } ?: -1) + 1,
                        )
                        folderIdMap[folder.id] = newId
                        addedFolders++
                    }
                }
            }
            if (pending.size == progressed.size) {
                // 存在无法解析的父链（环或缺失）：挂到根，避免丢条目。
                pending.forEach { folder ->
                    val newId = java.util.UUID.randomUUID().toString()
                    folders += folder.copy(id = newId, parentId = "", order = (folders.maxOfOrNull { it.order } ?: -1) + 1)
                    folderIdMap[folder.id] = newId
                    addedFolders++
                }
                pending.clear()
            }
        }

        // 2) 收藏：URL 已存在跳过；否则补入并映射文件夹。
        val bookmarks = ArrayList(local.bookmarks)
        val knownBookmarkUrls = local.bookmarks.mapTo(HashSet()) { it.url }
        var addedBookmarks = 0
        for (entry in remote.bookmarks) {
            if (entry.url in knownBookmarkUrls) continue
            if (bookmarks.size >= maxBookmarks) {
                truncated = true
                break
            }
            val mappedFolder = folderIdMap[entry.folderId] ?: entry.folderId
            val exists = folders.any { it.id == mappedFolder }
            bookmarks += entry.copy(
                folderId = if (exists) mappedFolder else "",
                order = bookmarks.maxOfOrNull { it.order }?.plus(1) ?: 0,
            )
            knownBookmarkUrls += entry.url
            addedBookmarks++
        }

        // 3) 历史：URL 已存在保留本地（时间取较新）；否则补入，按时间倒序截断。
        val history = ArrayList(local.history)
        val knownHistoryUrls = local.history.mapTo(HashSet()) { it.url }
        var addedHistory = 0
        for (entry in remote.history) {
            if (entry.url in knownHistoryUrls) {
                val index = history.indexOfFirst { it.url == entry.url }
                if (index >= 0 && entry.at > history[index].at) {
                    history[index] = history[index].copy(
                        at = entry.at,
                        lastOpened = maxOf(history[index].lastOpened, entry.lastOpened),
                    )
                }
                continue
            }
            history += entry
            knownHistoryUrls += entry.url
            addedHistory++
        }
        val sortedHistory = history.sortedByDescending { it.at }
        if (sortedHistory.size > maxHistory) truncated = true

        return MergeResult(
            snapshot = Snapshot(
                history = sortedHistory.take(maxHistory),
                bookmarks = bookmarks.take(maxBookmarks),
                folders = folders,
            ),
            addedHistory = addedHistory,
            addedBookmarks = addedBookmarks,
            addedFolders = addedFolders,
            truncated = truncated,
        )
    }

    // ---- JSON 细节 ----

    private fun entriesToJson(items: List<BrowseStore.Entry>): JSONArray {
        val arr = JSONArray()
        items.forEach { e ->
            arr.put(
                JSONObject()
                    .put("url", e.url)
                    .put("title", e.title)
                    .put("at", e.at)
                    .put("lastOpened", e.lastOpened)
                    .put("folderId", e.folderId)
                    .put("order", e.order),
            )
        }
        return arr
    }

    private fun entriesFromJson(arr: JSONArray?): List<BrowseStore.Entry> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("url", "")
                if (url.isBlank()) continue
                add(
                    BrowseStore.Entry(
                        url = url,
                        title = o.optString("title", url),
                        at = o.optLong("at", 0L),
                        lastOpened = o.optLong("lastOpened", 0L),
                        folderId = o.optString("folderId", ""),
                        order = o.optInt("order", i),
                    ),
                )
            }
        }
    }

    private fun foldersToJson(items: List<BrowseStore.Folder>): JSONArray {
        val arr = JSONArray()
        items.forEach { f ->
            arr.put(
                JSONObject()
                    .put("id", f.id)
                    .put("name", f.name)
                    .put("order", f.order)
                    .put("parentId", f.parentId),
            )
        }
        return arr
    }

    private fun foldersFromJson(arr: JSONArray?): List<BrowseStore.Folder> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id", "")
                if (id.isBlank()) continue
                add(
                    BrowseStore.Folder(
                        id = id,
                        name = o.optString("name", "未命名文件夹"),
                        order = o.optInt("order", i),
                        parentId = o.optString("parentId", ""),
                    ),
                )
            }
        }
    }
}
