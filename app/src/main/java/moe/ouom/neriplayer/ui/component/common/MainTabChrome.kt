package moe.ouom.neriplayer.ui.component.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 主 Tab 顶栏/搜索/Tab 条等 chrome 的挂载槽。
 *
 * 这些控件必须画在 content 捕获层**之外**（与 MiniPlayer 同级）：
 * - 列表可滚到 chrome 底下并被模糊采样
 * - 标题文字不进采样层，不会被一起糊掉
 */
@Stable
class MainTabChromeSlot {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
}

val LocalMainTabChromeSlot = staticCompositionLocalOf { MainTabChromeSlot() }

/**
 * 在页面内声明 chrome（顶栏等），实际绘制发生在 [LocalMainTabChromeSlot] 宿主（捕获层外）。
 */
@Composable
fun MainTabChrome(content: @Composable () -> Unit) {
    val slot = LocalMainTabChromeSlot.current
    val latest by rememberUpdatedState(content)
    DisposableEffect(slot) {
        slot.content = { latest() }
        onDispose {
            if (slot.content != null) {
                slot.content = null
            }
        }
    }
}
