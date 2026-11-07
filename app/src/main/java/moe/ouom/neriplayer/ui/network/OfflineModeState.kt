package moe.ouom.neriplayer.ui.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import moe.ouom.neriplayer.data.traffic.hasLikelyInternetAccess

private val SETTLE_RECHECK_MS = OfflineModePolicy.SETTLE_RECHECK_MS
/** 网络丢失后先等一小段时间再判定离线，避免系统切换默认网络瞬间的 activeNetwork=null 误判。 */
private val LOSS_CONFIRM_MS = OfflineModePolicy.LOSS_CONFIRM_MS

@Composable
fun rememberOfflineModeState(): State<Boolean> {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val offlineState = remember(appContext) {
        mutableStateOf(!appContext.hasLikelyInternetAccess())
    }

    DisposableEffect(appContext, lifecycleOwner) {
        val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)

        // 同步读取一次真实网络状态。回调可能滞后或不触发（息屏/Doze/网络切换后尤其明显），
        // 所以每次回到前台都必须主动重查，不能只依赖 ConnectivityManager 的推送。
        fun refreshNow() {
            offlineState.value = !appContext.hasLikelyInternetAccess()
        }

        var disposed = false
        val pendingChecks = mutableSetOf<Runnable>()

        fun postCheck(delayMs: Long) {
            lateinit var runnable: Runnable
            runnable = Runnable {
                pendingChecks.remove(runnable)
                if (!disposed) refreshNow()
            }
            pendingChecks.add(runnable)
            mainHandler.postDelayed(runnable, delayMs)
        }

        val callback = if (connectivityManager != null) {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (disposed) return
                    refreshNow()
                    postCheck(SETTLE_RECHECK_MS)
                }

                override fun onLost(network: Network) {
                    if (disposed) return
                    // 不立即判定离线：默认网络切换时 activeNetwork 可能短暂为 null。
                    // 延迟后再看真实状态，若网络已恢复则保持在线。
                    postCheck(LOSS_CONFIRM_MS)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    if (disposed) return
                    refreshNow()
                    postCheck(SETTLE_RECHECK_MS)
                }
            }
        } else {
            null
        }

        // 生命周期自愈：息屏/切后台/打开播放页再返回，都会走到 ON_RESUME，
        // 此时无条件重查一次真实网络，纠正任何残留的错误离线态。
        val lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                if (!disposed) refreshNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        refreshNow()
        if (callback != null) {
            runCatching { connectivityManager?.registerDefaultNetworkCallback(callback) }
        }

        onDispose {
            disposed = true
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            pendingChecks.forEach { mainHandler.removeCallbacks(it) }
            pendingChecks.clear()
            if (callback != null) {
                runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
            }
        }
    }

    return offlineState
}
