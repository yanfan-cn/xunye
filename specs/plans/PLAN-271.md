# PLAN-271：浏览器收藏/文件夹/历史纳入备份与恢复

## 1. 基本信息

| 项目 | 内容 |
| --- | --- |
| Plan ID | PLAN-271 |
| 对应 Spec | [`../features/SPEC-271.md`](../features/SPEC-271.md) |
| 状态 | Ready |
| 执行人 | AO |
| 对应 Log | [`../logs/LOG-271.md`](../logs/LOG-271.md) |
| 最后更新 | 2026-11-21 |

## 2. 现状与证据

- `BrowseStore`（`browser/engine/BrowseStore.kt`）用 SP `browsesth_browse_store` 存 `history` / `bookmarks` / `folders`（JSON 字符串），上限 `MAX_HISTORY=100`、`MAX_BOOKMARKS=1000`。
- `Backup.backupFileNames` 不含该 SP；`Restore` 也不读；WebDAV 只搬运同一 zip。
- `BackupConfig` 的 `ignoreKeys`/`backupIgnoreKeys` 只覆盖 config.xml 的偏好项与 Room 数据项。

## 3. 方案

### 3.1 新增 `BrowseStoreBackup`（`browser/engine/`）

- 常量 `FILE_NAME = "browserBrowse.json"`。
- `Snapshot(history, bookmarks, folders)`，JSON 编解码（`org.json`，与 BrowseStore 同风格）。
- `merge(local, remote, limits)` 纯函数：
  1. 文件夹：按父在前顺序，id 相同则复用；否则同名同父复用；否则新建（新 id，order 追加），记录 id 映射。
  2. 收藏：URL 已存在则跳过；否则按映射后的 folderId 追加，直至上限。
  3. 历史：URL 已存在保留本地（时间取较大）；否则追加；按 `at` 倒序截断到上限。
- 返回 `MergeResult(addedHistory, addedBookmarks, addedFolders, truncated)`。

### 3.2 `BrowseStore` 增加

- `snapshot(): BrowseStoreBackup.Snapshot`
- `applySnapshot(snapshot)`（整表写回，仅供恢复合并结果使用）
- 把 `MAX_HISTORY` / `MAX_BOOKMARKS` 暴露为 `internal const`（同包可见）。

### 3.3 `Backup`

- 在 `config.xml` 之后：`if (!BackupConfig.backupIgnoreBrowserBrowse) BrowseStoreBackup.write(File(backupPath, FILE_NAME), BrowseStore(appCtx).snapshot())`。
- `backupFileNames` 加入 `BrowseStoreBackup.FILE_NAME`。

### 3.4 `Restore`

- 在 `config.xml` 恢复之后：`if (!BackupConfig.ignoreBrowserBrowse)` 读文件 → `BrowseStoreBackup.read` → `BrowseStore(appCtx).applySnapshot(BrowseStoreBackup.merge(...))`，异常写入 AppLog。

### 3.5 `BackupConfig` + 设置页

- 新增 `browserBrowseKey = "browserBrowse"`，追加到 `ignoreKeys`/`ignoreTitle` 与 `backupIgnoreKeys`/`backupIgnoreTitle` 末尾（下标对齐）。
- 新增 `ignoreBrowserBrowse` / `backupIgnoreBrowserBrowse`。
- 新增字符串 `browser_browse_backup`（en/zh-rCN/zh-rHK/zh-rTW）。

## 4. 数据合同

`browserBrowse.json`：

```json
{
  "version": 1,
  "history": [{"url":"…","title":"…","at":0,"lastOpened":0,"folderId":"","order":0}],
  "bookmarks": [ ... 同结构 ... ],
  "folders": [{"id":"…","name":"…","order":0,"parentId":""}]
}
```

- 未知字段忽略；缺失字段取默认值（与 `BrowseStore.readEntries` 一致）。
- `version` 仅用于将来迁移；当前不校验。

## 5. 测试

| TC | AC | 方法 |
| --- | --- | --- |
| TC-01 | AC-01/06 | `BrowseStoreBackupTest`：编解码往返 |
| TC-02 | AC-03/06 | `BrowseStoreBackupTest`：重复 merge 幂等 |
| TC-03 | AC-04/06 | `BrowseStoreBackupTest`：本地已有收藏不被覆盖 |
| TC-04 | AC-06 | `BrowseStoreBackupTest`：文件夹 id 冲突重映射、父级链路正确 |
| TC-05 | AC-06 | `BrowseStoreBackupTest`：上限截断 |
| TC-06 | AC-02/05 | 真机：备份→改动→恢复 |

## 6. 验证步骤

1. `$env:GRADLE_USER_HOME="D:\cursor\.gradle-home"`
2. `.\gradlew.bat :app:testAlohaDebugUnitTest --tests "io.legado.app.browser.engine.*" --no-configuration-cache`
3. `.\gradlew.bat assembleAlohaDebug --no-configuration-cache`
4. 真机备份后解压 zip 检查 `browserBrowse.json`；删除一条收藏后恢复，验证回来。

## 7. 回退

删除 `browserBrowse.json` 相关写入/读取与忽略项即可；旧备份无该文件时恢复跳过，向前兼容。

## 8. 偏差

（实施后填）
