package moe.ouom.neriplayer.ui.component.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassRole
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassSurface

private val NeriTabTopBarGlassShape = RoundedCornerShape(20.dp)

/**
 * 首页 Tab 通用紧凑大标题顶栏。
 * 比 Material LargeTopAppBar 更靠上，减少状态栏下方的空白。
 * 外层玻璃：标题/操作区透明模糊（设置 → 动效 → 高级模糊）。
 */
@Composable
fun NeriTabLargeTitleTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AdvancedGlassSurface(
            role = AdvancedGlassRole.ScreenTopTab,
            shape = NeriTabTopBarGlassShape,
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
                .clip(NeriTabTopBarGlassShape)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    title()
                }
                actions()
            }
        }
    }
}

@Composable
fun NeriTabLargeTitleTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    NeriTabLargeTitleTopBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier,
        actions = actions,
        windowInsets = windowInsets
    )
}

// 保持透明容器语义，避免宿主 Scaffold 给顶栏上色
internal val NeriTabTopBarContainerColor: Color = Color.Transparent
