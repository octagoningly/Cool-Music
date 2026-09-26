package moe.ouom.neriplayer.core.player.prefetch

/**
 * 播放预缓存策略：统一各场景的预加载字节预算与连点检测。
 * 纯逻辑，便于单测；不直接依赖 PlayerManager。
 */
object PlaybackPrecachePolicy {
    /** 正常“下一首”：预取前 3~5 秒，取 5 秒作为目标。 */
    const val NEXT_TRACK_PREFIX_MS = 5_000L

    /** 连点下一首时，扩大曲目窗口但只取前 1.3 秒。 */
    const val RAPID_SKIP_PREFIX_MS = 1_300L

    /** 列表类场景（歌单 / 首页 / 搜索 / 最近列表）默认只取前 1.3 秒。 */
    const val LIST_PREFIX_MS = 1_300L

    /** 开屏预加载：点击迷你栏即播，目标前 5 秒。 */
    const val APP_LAUNCH_PREFIX_MS = 5_000L

    /** 连点判定窗口。 */
    const val RAPID_SKIP_WINDOW_MS = 2_500L

    /** 窗口内连续点击下一首达到该次数视为疯狂连点。 */
    const val RAPID_SKIP_THRESHOLD = 2

    /** 列表场景单次最多预取曲目数。 */
    const val LIST_MAX_SONGS = 5

    /** 连点时最多向前预取的曲目数（含紧邻下一首）。 */
    const val RAPID_SKIP_MAX_SONGS = 8

    /** 开屏 / 最近列表：预取当前及后续少量曲目。 */
    const val RECENT_LIST_MAX_SONGS = 4

    private const val MIN_PREFIX_BYTES = 24L * 1024L
    private const val MAX_PREFIX_BYTES = 512L * 1024L
    private const val DEFAULT_BITRATE_KBPS = 320

    fun isScenarioEnabled(masterEnabled: Boolean, scenarioEnabled: Boolean): Boolean {
        return masterEnabled && scenarioEnabled
    }

    /**
     * 按目标秒数估算需预取的字节数。
     * 优先用 contentLength / duration 推算码率，否则按 320kbps 估算。
     */
    fun prefixBytes(
        prefixMs: Long,
        contentLength: Long?,
        durationMs: Long?,
        bitrateKbps: Int? = null
    ): Long {
        val safePrefixMs = prefixMs.coerceAtLeast(0L)
        if (safePrefixMs == 0L) return 0L
        if (contentLength != null && contentLength > 0L && durationMs != null && durationMs > 0L) {
            val proportional = (contentLength * safePrefixMs) / durationMs
            val upper = minOf(MAX_PREFIX_BYTES, contentLength)
            return proportional.coerceIn(minOf(MIN_PREFIX_BYTES, upper), upper)
        }
        val kbps = (bitrateKbps ?: DEFAULT_BITRATE_KBPS).coerceIn(32, 2_000)
        val bytes = (kbps * 1000L / 8L) * safePrefixMs / 1000L
        return bytes.coerceIn(MIN_PREFIX_BYTES, MAX_PREFIX_BYTES)
    }

    /**
     * 记录一次“下一首”点击，返回窗口内点击次数。
     * [timestamps] 会原地保留仍处于窗口内的记录。
     */
    fun noteSkipClick(
        timestamps: MutableList<Long>,
        nowMs: Long,
        windowMs: Long = RAPID_SKIP_WINDOW_MS
    ): Int {
        timestamps += nowMs
        pruneSkipTimestamps(timestamps, nowMs, windowMs)
        return timestamps.size
    }

    fun pruneSkipTimestamps(
        timestamps: MutableList<Long>,
        nowMs: Long,
        windowMs: Long = RAPID_SKIP_WINDOW_MS
    ) {
        val cutoff = nowMs - windowMs
        timestamps.removeAll { it < cutoff }
    }

    fun isRapidSkipping(
        timestamps: List<Long>,
        nowMs: Long,
        windowMs: Long = RAPID_SKIP_WINDOW_MS,
        threshold: Int = RAPID_SKIP_THRESHOLD
    ): Boolean {
        val cutoff = nowMs - windowMs
        return timestamps.count { it >= cutoff } >= threshold
    }

    /** 连点时优先保证“当前下一首”，其后曲目只取更短前缀。 */
    fun prefixMsForQueueIndex(indexOffsetFromNext: Int): Long {
        return if (indexOffsetFromNext <= 0) {
            NEXT_TRACK_PREFIX_MS
        } else {
            RAPID_SKIP_PREFIX_MS
        }
    }

    fun selectPrefetchWindow(
        queueSize: Int,
        startIndex: Int,
        rapidSkip: Boolean,
        repeatAll: Boolean
    ): List<Int> {
        if (queueSize <= 0 || startIndex !in 0 until queueSize) return emptyList()
        val maxCount = if (rapidSkip) RAPID_SKIP_MAX_SONGS else 2
        val result = ArrayList<Int>(maxCount)
        var cursor = startIndex
        while (result.size < maxCount) {
            if (cursor in 0 until queueSize) {
                result += cursor
                cursor++
            } else if (repeatAll && queueSize > 1) {
                cursor = 0
            } else {
                break
            }
            if (cursor == startIndex && result.size >= queueSize) break
        }
        return result
    }
}
