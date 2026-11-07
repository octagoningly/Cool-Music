package moe.ouom.neriplayer.core.player.policy.skip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSkipDebouncePolicyTest {
    @Test
    fun firstClickIsNotMerged() {
        assertFalse(
            PlaybackSkipDebouncePolicy.shouldMergeWithPending(
                lastClickAtMs = 0L,
                nowMs = 10_000L
            )
        )
    }

    @Test
    fun clickWithinWindowIsMerged() {
        assertTrue(
            PlaybackSkipDebouncePolicy.shouldMergeWithPending(
                lastClickAtMs = 10_000L,
                nowMs = 10_000L + PlaybackSkipDebouncePolicy.MERGE_WINDOW_MS - 1
            )
        )
    }

    @Test
    fun clickAfterWindowIsNotMerged() {
        assertFalse(
            PlaybackSkipDebouncePolicy.shouldMergeWithPending(
                lastClickAtMs = 10_000L,
                nowMs = 10_000L + PlaybackSkipDebouncePolicy.MERGE_WINDOW_MS
            )
        )
    }

    @Test
    fun backwardsClockIsNotMerged() {
        assertFalse(
            PlaybackSkipDebouncePolicy.shouldMergeWithPending(
                lastClickAtMs = 20_000L,
                nowMs = 10_000L
            )
        )
    }

    @Test
    fun accumulateStepsStartsAtOneAndCaps() {
        assertEquals(1, PlaybackSkipDebouncePolicy.accumulateSteps(0))
        assertEquals(3, PlaybackSkipDebouncePolicy.accumulateSteps(2))
        assertEquals(
            PlaybackSkipDebouncePolicy.MAX_MERGED_STEPS,
            PlaybackSkipDebouncePolicy.accumulateSteps(
                PlaybackSkipDebouncePolicy.MAX_MERGED_STEPS
            )
        )
    }

    @Test
    fun settleDelayIsWellBelowSpinnerThreshold() {
        // 合并等待本身必须短于 UI 的 1 秒加载圈阈值。
        assertTrue(PlaybackSkipDebouncePolicy.SETTLE_DELAY_MS < 1_000L)
    }

    @Test
    fun mergeWindowIsShortEnoughToSpareNormalSingleClicks() {
        // 停手 1 秒后的普通单击不应再被合并、平白多等 SETTLE_DELAY_MS。
        assertTrue(PlaybackSkipDebouncePolicy.MERGE_WINDOW_MS <= 1_000L)
        assertFalse(
            PlaybackSkipDebouncePolicy.shouldMergeWithPending(
                lastClickAtMs = 10_000L,
                nowMs = 11_000L
            )
        )
    }
}
