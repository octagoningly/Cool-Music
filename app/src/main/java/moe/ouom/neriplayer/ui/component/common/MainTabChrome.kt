package moe.ouom.neriplayer.ui.component.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 主 Tab 顶栏/搜索/Tab 条等 chrome 的挂载槽（按 route 隔离）。
 *
 * 必须画在 content 捕获层之外（与 MiniPlayer 同级）：
 * - 列表可滚到 chrome 底下并被模糊采样
 * - 标题文字不进采样层，不会被一起糊掉
 * - 切换 Tab 时只显示当前 route 的 chrome，避免残留「媒体库」标题
 */
@Stable
class MainTabChromeSlot {
    private val entries = mutableStateMapOf<String, @Composable () -> Unit>()

    fun register(route: String, content: @Composable () -> Unit) {
        entries[route] = content
    }

    fun unregister(route: String) {
        entries.remove(route)
    }

    @Composable
    fun ContentFor(route: String) {
        entries[route]?.invoke()
    }
}

val LocalMainTabChromeSlot = staticCompositionLocalOf { MainTabChromeSlot() }

/**
 * 在页面内声明 chrome（顶栏等），实际绘制发生在 [LocalMainTabChromeSlot] 宿主（捕获层外）。
 * [route] 用于多 Tab 并存时只显示当前页 chrome。
 */
@Composable
fun MainTabChrome(
    route: String,
    content: @Composable () -> Unit
) {
    val slot = LocalMainTabChromeSlot.current
    val latest by rememberUpdatedState(content)
    DisposableEffect(slot, route) {
        slot.register(route) { latest() }
        onDispose { slot.unregister(route) }
    }
}
