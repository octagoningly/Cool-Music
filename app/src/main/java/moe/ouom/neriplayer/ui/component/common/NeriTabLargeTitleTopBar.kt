package moe.ouom.neriplayer.ui.component.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassRole
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassSurface

/**
 * 首页 Tab 通用紧凑大标题顶栏。
 * 玻璃画在底层只做模糊/tint，标题与操作钮画在上层（不进玻璃 content），
 * 保证文字始终清晰；关模糊时用实底挡住滚过内容。
 */
@Composable
fun NeriTabLargeTitleTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        // 底层：只负责磨砂/实底，不包含文字
        AdvancedGlassSurface(
            role = AdvancedGlassRole.ScreenTopTab,
            shape = RectangleShape,
            fallbackColor = MaterialTheme.colorScheme.background,
            tintColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.matchParentSize()
        ) {
            Spacer(modifier = Modifier.fillMaxSize())
        }
        // 上层：标题与按钮，始终清晰
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .padding(start = 20.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
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
