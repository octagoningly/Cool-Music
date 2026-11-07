package moe.ouom.neriplayer.ui.network

/**
 * 离线状态判定的时序策略（纯逻辑，便于单测）。
 *
 * 背景：息屏/Doze/默认网络切换时 ConnectivityManager 会短暂投递 onLost，
 * 此时 activeNetwork 可能为 null，若立刻判定离线就会把整个首页/探索页切成空白，
 * 而且系统不一定会为同一个已存在网络重新投递 onAvailable，导致状态长期卡在离线。
 */
object OfflineModePolicy {
    const val SETTLE_RECHECK_MS = 300L
    const val LOSS_CONFIRM_MS = 800L

    /** 丢失确认必须比抖动重查更久，给网络切换留出恢复窗口。 */
    fun lossConfirmDelayIsLongerThanSettle(): Boolean =
        LOSS_CONFIRM_MS > SETTLE_RECHECK_MS

    /**
     * 回到前台时是否必须主动重查（不依赖系统回调）。
     * 恒为 true：这是纠正残留错误离线态的唯一可靠时机。
     */
    fun shouldRefreshOnResume(): Boolean = true

    /** 延迟确认窗口内即便收到 onLost，也不立刻提交离线态。 */
    fun shouldCommitOfflineImmediatelyOnLoss(): Boolean = false
}
