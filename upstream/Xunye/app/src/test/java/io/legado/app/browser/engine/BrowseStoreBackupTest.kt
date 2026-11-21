package io.legado.app.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application

/**
 * SPEC-271：浏览器收藏/文件夹/历史的备份编解码与恢复合并。
 *
 * 需要真实 `org.json` 实现，故走 Robolectric（与 `BrowseStoreImportSinkTest` 同口径）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BrowseStoreBackupTest {

    private fun entry(
        url: String,
        title: String = url,
        at: Long = 1L,
        folderId: String = "",
        order: Int = 0,
    ) = BrowseStore.Entry(
        url = url,
        title = title,
        at = at,
        lastOpened = at,
        folderId = folderId,
        order = order,
    )

    private fun folder(id: String, name: String, parentId: String = "", order: Int = 0) =
        BrowseStore.Folder(id = id, name = name, order = order, parentId = parentId)

    @Test
    fun `编解码往返保留三类数据`() {
        val snapshot = BrowseStoreBackup.Snapshot(
            history = listOf(entry("https://h.example/", "H", at = 100L)),
            bookmarks = listOf(entry("https://b.example/", "B", folderId = "f1")),
            folders = listOf(folder("f1", "技术"), folder("f2", "前端", parentId = "f1")),
        )
        val decoded = BrowseStoreBackup.decode(BrowseStoreBackup.encode(snapshot))
        assertNotNull(decoded)
        assertEquals(1, decoded!!.history.size)
        assertEquals("https://h.example/", decoded.history.first().url)
        assertEquals(100L, decoded.history.first().at)
        assertEquals(1, decoded.bookmarks.size)
        assertEquals("f1", decoded.bookmarks.first().folderId)
        assertEquals(2, decoded.folders.size)
        assertEquals("f1", decoded.folders.single { it.id == "f2" }.parentId)
    }

    @Test
    fun `损坏 JSON 返回 null 不抛异常`() {
        assertEquals(null, BrowseStoreBackup.decode("{not json"))
    }

    @Test
    fun `合并补入备份条目并保留本地`() {
        val local = BrowseStoreBackup.Snapshot(
            history = listOf(entry("https://local.h/", at = 200L)),
            bookmarks = listOf(entry("https://local.b/", title = "本地标题")),
        )
        val remote = BrowseStoreBackup.Snapshot(
            history = listOf(entry("https://remote.h/", at = 300L)),
            bookmarks = listOf(entry("https://remote.b/")),
        )
        val merged = BrowseStoreBackup.merge(local, remote).snapshot
        assertEquals(setOf("https://local.h/", "https://remote.h/"), merged.history.map { it.url }.toSet())
        assertEquals(
            setOf("https://local.b/", "https://remote.b/"),
            merged.bookmarks.map { it.url }.toSet(),
        )
        assertEquals("本地标题", merged.bookmarks.single { it.url == "https://local.b/" }.title)
    }

    @Test
    fun `重复恢复幂等`() {
        val local = BrowseStoreBackup.Snapshot()
        val remote = BrowseStoreBackup.Snapshot(
            history = listOf(entry("https://a.h/", at = 10L), entry("https://b.h/", at = 20L)),
            bookmarks = listOf(entry("https://a.b/")),
            folders = listOf(folder("fa", "A")),
        )
        val once = BrowseStoreBackup.merge(local, remote).snapshot
        val twice = BrowseStoreBackup.merge(once, remote).snapshot
        assertEquals(once.history.size, twice.history.size)
        assertEquals(once.bookmarks.size, twice.bookmarks.size)
        assertEquals(once.folders.size, twice.folders.size)
    }

    @Test
    fun `本地已有收藏不被备份覆盖`() {
        val local = BrowseStoreBackup.Snapshot(
            bookmarks = listOf(entry("https://same.b/", title = "我改过的名字")),
        )
        val remote = BrowseStoreBackup.Snapshot(
            bookmarks = listOf(entry("https://same.b/", title = "备份里的旧名字")),
        )
        val merged = BrowseStoreBackup.merge(local, remote)
        assertEquals(0, merged.addedBookmarks)
        assertEquals("我改过的名字", merged.snapshot.bookmarks.single().title)
    }

    @Test
    fun `文件夹 id 冲突时重映射且层级正确`() {
        // 本地已有 id=f1（不同名字），备份里 f1 → f2 是父子链，必须整体重映射。
        val local = BrowseStoreBackup.Snapshot(
            bookmarks = listOf(entry("https://keep.b/", folderId = "f1")),
            folders = listOf(folder("f1", "本地独占")),
        )
        val remote = BrowseStoreBackup.Snapshot(
            bookmarks = listOf(entry("https://new.b/", folderId = "f2")),
            folders = listOf(
                BrowseStore.Folder(id = "f1", name = "备份父级", order = 0),
                BrowseStore.Folder(id = "f2", name = "备份子级", order = 1, parentId = "f1"),
            ),
        )
        val merged = BrowseStoreBackup.merge(local, remote).snapshot
        // 本地 f1 保持原名，新条目落在映射后的 f2 上。
        assertEquals("本地独占", merged.folders.single { it.id == "f1" }.name)
        val parent = merged.folders.single { it.name == "备份父级" }
        val child = merged.folders.single { it.name == "备份子级" }
        assertEquals(parent.id, child.parentId)
        assertTrue(parent.id != "f1")
        assertEquals(child.id, merged.bookmarks.single { it.url == "https://new.b/" }.folderId)
        // 本地收藏归属不变
        assertEquals("f1", merged.bookmarks.single { it.url == "https://keep.b/" }.folderId)
    }

    @Test
    fun `同名同父文件夹复用而不重复创建`() {
        val local = BrowseStoreBackup.Snapshot(folders = listOf(folder("l1", "AI")))
        val remote = BrowseStoreBackup.Snapshot(
            bookmarks = listOf(entry("https://ai.b/", folderId = "r1")),
            folders = listOf(folder("r1", "AI")),
        )
        val merged = BrowseStoreBackup.merge(local, remote)
        assertEquals(1, merged.snapshot.folders.size)
        assertEquals(0, merged.addedFolders)
        assertEquals("l1", merged.snapshot.bookmarks.single().folderId)
    }

    @Test
    fun `孤儿父链回落根目录不丢条目`() {
        val remote = BrowseStoreBackup.Snapshot(
            folders = listOf(folder("x", "孤儿", parentId = "missing")),
        )
        val merged = BrowseStoreBackup.merge(BrowseStoreBackup.Snapshot(), remote)
        assertEquals(1, merged.snapshot.folders.size)
        assertEquals("", merged.snapshot.folders.single().parentId)
    }

    @Test
    fun `历史按上限截断且保留最新`() {
        val local = (1..5).map { entry("https://l$it.h/", at = it.toLong()) }
        val remote = (1..5).map { entry("https://r$it.h/", at = (100 + it).toLong()) }
        val merged = BrowseStoreBackup.merge(
            local = BrowseStoreBackup.Snapshot(history = local),
            remote = BrowseStoreBackup.Snapshot(history = remote),
            maxHistory = 4,
        )
        assertEquals(4, merged.snapshot.history.size)
        assertTrue(merged.truncated)
        // 备份中的较新条目优先保留
        assertEquals("https://r5.h/", merged.snapshot.history.first().url)
    }

    @Test
    fun `收藏超过上限截断并标记`() {
        val remote = (1..5).map { entry("https://$it.b/") }
        val merged = BrowseStoreBackup.merge(
            local = BrowseStoreBackup.Snapshot(),
            remote = BrowseStoreBackup.Snapshot(bookmarks = remote),
            maxBookmarks = 2,
        )
        assertEquals(2, merged.snapshot.bookmarks.size)
        assertTrue(merged.truncated)
    }
}
