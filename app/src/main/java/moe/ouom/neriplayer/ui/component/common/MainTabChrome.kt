package moe.ouom.neriplayer.ui.component.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 主 Tab 顶栏/搜索/Tab 条等 chrome 的挂载槽（按 route 隔离）。
 *
 * 必须画在 content 捕获层之外（与 MiniPlayer 同级）：
 * - 列表可滚到 chrome 底下并被模糊采样
 * - 标题文字不进采样层，不会被一起糊掉
 * - 切换 Tab 时只显示当前 route 的 chrome
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
        val content = entries[route] ?: return
        content()
    }
}

val LocalMainTabChromeSlot = staticCompositionLocalOf { MainTabChromeSlot() }

/**
 * 在页面内声明 chrome（顶栏等），实际绘制发生在 [LocalMainTabChromeSlot] 宿主（捕获层外）。
 *
 * 每次组合都用 [SideEffect] 刷新 content，保证「新发现」等条件分支切换时顶栏跟着变。
 */
@Composable
fun MainTabChrome(
    route: String,
    content: @Composable () -> Unit
) {
    val slot = LocalMainTabChromeSlot.current
    SideEffect {
        slot.register(route, content)
    }
    DisposableEffect(slot, route) {
        onDispose { slot.unregister(route) }
    }
}
