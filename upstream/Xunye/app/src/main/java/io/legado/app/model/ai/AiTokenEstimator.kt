package io.legado.app.model.ai

/**
 * SPEC-262: rough token estimate when API does not return usage.
 * CJK ≈ 1.5 chars/token; Latin ≈ 4 chars/token.
 */
object AiTokenEstimator {

    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        for (c in text) {
            if (c in '\u4e00'..'\u9fff' || c in '\u3400'..'\u4dbf') cjk++ else other++
        }
        return ((cjk / 1.5) + (other / 4.0)).toInt().coerceAtLeast(if (text.isBlank()) 0 else 1)
    }

    fun estimatePair(prompt: String, completion: String): AiTokenUsage {
        val p = estimate(prompt)
        val c = estimate(completion)
        return AiTokenUsage(
            promptTokens = p,
            completionTokens = c,
            totalTokens = p + c,
            estimated = true,
        )
    }
}

data class AiTokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val estimated: Boolean,
)
