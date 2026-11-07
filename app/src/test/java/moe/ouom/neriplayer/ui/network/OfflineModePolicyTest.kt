package moe.ouom.neriplayer.ui.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineModePolicyTest {
    @Test
    fun lossConfirmationWaitsLongerThanSettling() {
        // 必须给默认网络切换留出恢复窗口，否则一次瞬断就会把首页切成空白。
        assertTrue(OfflineModePolicy.lossConfirmDelayIsLongerThanSettle())
    }

    @Test
    fun onResumeAlwaysRefreshesRealNetworkState() {
        // 系统不一定为已存在的网络重新投递 onAvailable，回到前台必须主动重查。
        assertTrue(OfflineModePolicy.shouldRefreshOnResume())
    }

    @Test
    fun onLostDoesNotImmediatelyCommitOffline() {
        assertFalse(OfflineModePolicy.shouldCommitOfflineImmediatelyOnLoss())
    }
}
