package io.legado.app.model.ai

import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfigStore

/** SPEC-262: local cumulative AI token usage via AppConfigStore. */
object AiUsageLedger {

    data class Snapshot(
        val promptTokens: Long,
        val completionTokens: Long,
        val totalTokens: Long,
        val requestCount: Long,
        val estimatedRequestCount: Long,
        val updatedAt: Long,
    )

    fun snapshot(): Snapshot {
        val prompt = AppConfigStore.getLong(PreferKey.aiUsagePromptTokens) ?: 0L
        val completion = AppConfigStore.getLong(PreferKey.aiUsageCompletionTokens) ?: 0L
        return Snapshot(
            promptTokens = prompt,
            completionTokens = completion,
            totalTokens = prompt + completion,
            requestCount = AppConfigStore.getLong(PreferKey.aiUsageRequestCount) ?: 0L,
            estimatedRequestCount = AppConfigStore.getLong(PreferKey.aiUsageEstimatedCount) ?: 0L,
            updatedAt = AppConfigStore.getLong(PreferKey.aiUsageUpdatedAt) ?: 0L,
        )
    }

    fun record(usage: AiTokenUsage) {
        val snap = snapshot()
        AppConfigStore.putAll(
            mapOf(
                PreferKey.aiUsagePromptTokens to snap.promptTokens + usage.promptTokens,
                PreferKey.aiUsageCompletionTokens to snap.completionTokens + usage.completionTokens,
                PreferKey.aiUsageRequestCount to snap.requestCount + 1,
                PreferKey.aiUsageEstimatedCount to
                    (snap.estimatedRequestCount + if (usage.estimated) 1 else 0),
                PreferKey.aiUsageUpdatedAt to System.currentTimeMillis(),
            ),
        )
    }

    fun restoreLastChatEnabled(): Boolean =
        AppConfigStore.getBoolean(PreferKey.aiRestoreLastChat) ?: false

    fun setRestoreLastChatEnabled(enabled: Boolean) {
        AppConfigStore.putBoolean(PreferKey.aiRestoreLastChat, enabled)
    }
}
