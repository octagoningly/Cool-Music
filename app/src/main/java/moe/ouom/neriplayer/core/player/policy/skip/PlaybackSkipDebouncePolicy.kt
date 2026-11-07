package moe.ouom.neriplayer.core.player.policy.skip

/**
 * 切歌防抖策略：连点「下一首」时把多次点击合并成一次实际的媒体装载。
 *
 * 纯逻辑，便于单测；不直接依赖 PlayerManager。
 * UI 侧的索引/歌名仍逐次立即更新保证跟手，只有「取流 + setMediaItem + 持久化 + 预取」
 * 这些重活按最后一次点击的目标位置执行一次。
 */
object PlaybackSkipDebouncePolicy {
    /** 停止点击后多久执行合并后的切歌。 */
    const val SETTLE_DELAY_MS = 200L

    /** 判定连点的窗口；窗口内的点击会被合并。 */
    const val MERGE_WINDOW_MS = 2_500L

    /** 单次合并最多跳过的曲目数，避免异常连点把队列跳飞。 */
    const val MAX_MERGED_STEPS = 30

    /**
     * 是否处于「连点窗口」：距上次点击在 [windowMs] 内即认为后续点击应被合并。
     * 首次点击时 lastClickAtMs 为 0（无上次点击），返回 false。
     * [nowMs] / [lastClickAtMs] 均为 SystemClock.elapsedRealtime()。
     */
    fun shouldMergeWithPending(
        lastClickAtMs: Long,
        nowMs: Long,
        windowMs: Long = MERGE_WINDOW_MS
    ): Boolean {
        if (lastClickAtMs <= 0L) return false
        val delta = nowMs - lastClickAtMs
        return delta in 0 until windowMs
    }

    /** 累加待跳步数，并夹在 [1, MAX_MERGED_STEPS]。 */
    fun accumulateSteps(pendingSteps: Int, increment: Int = 1): Int {
        return (pendingSteps + increment).coerceIn(1, MAX_MERGED_STEPS)
    }
}
