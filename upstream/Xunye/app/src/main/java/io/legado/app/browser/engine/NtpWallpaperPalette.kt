package io.legado.app.browser.engine

/**
 * SPEC-270: 主页壁纸的**实际平均底色**，供搜索胶囊墨色决策使用。
 *
 * 原先胶囊内墨色靠「壁纸预设 Id 是否深色」猜，深色主题 + 赛博/霓虹必然选错。
 * 这里改用实测平均色（64×64 均值），再由 [NtpCapsuleInk] 按真实合成底色选墨。
 *
 * 相册图片与每日壁纸内容不可预知，且始终覆盖深色遮罩（≥0.32/0.35），
 * 取一个保守的深色代表值即可 —— 决策函数本身有对比度兜底。
 */
object NtpWallpaperPalette {

    /** 实测均值：赛博 #1C527F。 */
    private const val CYBER_AVG = 0xFF1C527F.toInt()

    /** 实测均值：霓虹 #31124F。 */
    private const val NEON_AVG = 0xFF31124F.toInt()

    /** 实测均值：薄雾 #C1C9D3。 */
    private const val MIST_AVG = 0xFFC1C9D3.toInt()

    /** 实测均值：晨纸 #F5E5D0。 */
    private const val MORNING_AVG = 0xFFF5E5D0.toInt()

    /** 实测均值：窗纱 #D8D2C0。 */
    private const val LINEN_AVG = 0xFFD8D2C0.toInt()

    /** 渐变预置（蓝天/绿茵/落樱）取各自顶部浅色；实际由渐变绘制，深色字可达标。 */
    private const val GRAD_BLUE_AVG = 0xFFB7D6F2.toInt()
    private const val GRAD_GREEN_AVG = 0xFFC9E6D2.toInt()
    private const val GRAD_PINK_AVG = 0xFFF6D5DE.toInt()

    /** 相册 / 每日壁纸：内容未知且带深色遮罩，取保守深值。 */
    private const val UNKNOWN_AVG = 0xFF101418.toInt()

    /** 无壁纸：直接跟页面底色。 */
    const val NO_WALLPAPER = 0x00000000

    fun averageArgb(wallpaperId: String): Int = when (wallpaperId) {
        NtpWallpaperIds.CYBER -> CYBER_AVG
        NtpWallpaperIds.NEON -> NEON_AVG
        NtpWallpaperIds.MIST -> MIST_AVG
        NtpWallpaperIds.MORNING -> MORNING_AVG
        NtpWallpaperIds.LINEN -> LINEN_AVG
        NtpWallpaperIds.GRAD_BLUE -> GRAD_BLUE_AVG
        NtpWallpaperIds.GRAD_GREEN -> GRAD_GREEN_AVG
        NtpWallpaperIds.GRAD_PINK -> GRAD_PINK_AVG
        NtpWallpaperIds.DEFAULT -> MIST_AVG
        else -> UNKNOWN_AVG
    }

    /**
     * 胶囊下方可见背景 = 壁纸平均色叠加 [scrimAlpha] 黑遮罩；[noWallpaper] 时直接返回 [pageBg]。
     */
    fun backgroundArgb(
        wallpaperId: String,
        scrimAlpha: Float,
        pageBg: Int,
        noWallpaper: Boolean = false,
    ): Int {
        if (noWallpaper) return pageBg
        return NtpCapsuleInk.composite(averageArgb(wallpaperId), 0xFF000000.toInt(), scrimAlpha)
    }
}
