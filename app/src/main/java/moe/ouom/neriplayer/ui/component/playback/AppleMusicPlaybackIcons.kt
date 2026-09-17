package moe.ouom.neriplayer.ui.component.playback

/*
 * Apple Music 风格播放控件图标：
 * 实心、圆润、无按钮底衬，直接叠在正在播放背景上。
 */

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * 画一个带圆角的三角形（实心，圆润顶点）。
 * [direction] = -1 表示朝左，+1 表示朝右。
 */
private fun DrawScope.drawRoundedTriangle(
    color: Color,
    direction: Int,
    boundsLeft: Float,
    boundsTop: Float,
    boundsRight: Float,
    boundsBottom: Float,
    cornerRadius: Float
) {
    val w = boundsRight - boundsLeft
    val h = boundsBottom - boundsTop
    if (w <= 0f || h <= 0f) return
    val tipX = if (direction > 0) boundsRight else boundsLeft
    val backX = if (direction > 0) boundsLeft else boundsRight
    val tipY = (boundsTop + boundsBottom) / 2f
    val topY = boundsTop
    val bottomY = boundsBottom
    val r = cornerRadius.coerceAtMost(minOf(w, h) * 0.45f)

    // 顶点
    val tip = Offset(tipX, tipY)
    val backTop = Offset(backX, topY)
    val backBottom = Offset(backX, bottomY)

    // 每条边向内缩 r 的辅助点，保证倒圆后仍是厚实三角
    fun lerp(a: Offset, b: Offset, t: Float) = Offset(
        a.x + (b.x - a.x) * t,
        a.y + (b.y - a.y) * t
    )
    val tipToTop = lerp(tip, backTop, 0.28f)
    val topToBack = lerp(backTop, backBottom, 0.22f)
    val backToBottom = lerp(backBottom, tip, 0.22f)
    val bottomToTip = lerp(tip, backBottom, 0.28f)

    val path = Path().apply {
        moveTo(topToBack.x, topToBack.y)
        // 背脊上 → 背脊下
        lineTo(lerp(backTop, backBottom, 0.78f).x, lerp(backTop, backBottom, 0.78f).y)
        // 背脊下圆角
        quadraticTo(backBottom.x, backBottom.y, backToBottom.x, backToBottom.y)
        // 下斜边 → 尖端
        lineTo(bottomToTip.x, bottomToTip.y)
        quadraticTo(tip.x, tip.y, tipToTop.x, tipToTop.y)
        // 上斜边 → 背脊上
        lineTo(topToBack.x, topToBack.y)
        close()
    }
    drawPath(path = path, color = color, style = Fill)
}

private fun DrawScope.drawRoundedBar(
    color: Color,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    cornerRadius: Float
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
    )
}

/** 播放：实心圆润三角（朝右） */
@Composable
fun AppleMusicPlayIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null
) {
    val describedModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }
    Canvas(modifier = describedModifier) {
        val w = size.width
        val h = size.height
        // 略内缩，保证实心形状完整
        val padX = w * 0.08f
        val padY = h * 0.08f
        drawRoundedTriangle(
            color = tint,
            direction = 1,
            boundsLeft = padX,
            boundsTop = padY,
            boundsRight = w - padX * 0.35f,
            boundsBottom = h - padY,
            cornerRadius = minOf(w, h) * 0.22f
        )
    }
}

/** 暂停：两根实心圆角竖条 */
@Composable
fun AppleMusicPauseIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null
) {
    val describedModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }
    Canvas(modifier = describedModifier) {
        val w = size.width
        val h = size.height
        val barWidth = w * 0.28f
        val barHeight = h * 0.72f
        val gap = w * 0.14f
        val top = (h - barHeight) / 2f
        val total = barWidth * 2 + gap
        val leftStart = (w - total) / 2f
        val radius = barWidth * 0.42f
        drawRoundedBar(tint, leftStart, top, barWidth, barHeight, radius)
        drawRoundedBar(tint, leftStart + barWidth + gap, top, barWidth, barHeight, radius)
    }
}

/** 上一首：两个实心圆润三角，朝左 */
@Composable
fun AppleMusicSkipPreviousIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null
) {
    val describedModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }
    Canvas(modifier = describedModifier) {
        val w = size.width
        val h = size.height
        val pad = minOf(w, h) * 0.06f
        val shapeW = w * 0.42f
        val shapeH = h * 0.78f
        val top = (h - shapeH) / 2f
        val radius = shapeH * 0.28f
        // 右侧三角（靠后）
        drawRoundedTriangle(
            color = tint,
            direction = -1,
            boundsLeft = w * 0.42f,
            boundsTop = top + h * 0.02f,
            boundsRight = w * 0.42f + shapeW * 0.92f,
            boundsBottom = top + shapeH - h * 0.02f,
            cornerRadius = radius * 0.9f
        )
        // 左侧三角（靠前，略大）
        drawRoundedTriangle(
            color = tint,
            direction = -1,
            boundsLeft = pad,
            boundsTop = top,
            boundsRight = pad + shapeW,
            boundsBottom = top + shapeH,
            cornerRadius = radius
        )
    }
}

/** 下一首：两个实心圆润三角，朝右 */
@Composable
fun AppleMusicSkipNextIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null
) {
    val describedModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }
    Canvas(modifier = describedModifier) {
        val w = size.width
        val h = size.height
        val pad = minOf(w, h) * 0.06f
        val shapeW = w * 0.42f
        val shapeH = h * 0.78f
        val top = (h - shapeH) / 2f
        val radius = shapeH * 0.28f
        // 左侧三角（靠后）
        drawRoundedTriangle(
            color = tint,
            direction = 1,
            boundsLeft = w * 0.58f - shapeW * 0.92f,
            boundsTop = top + h * 0.02f,
            boundsRight = w * 0.58f,
            boundsBottom = top + shapeH - h * 0.02f,
            cornerRadius = radius * 0.9f
        )
        // 右侧三角（靠前，略大）
        drawRoundedTriangle(
            color = tint,
            direction = 1,
            boundsLeft = w - pad - shapeW,
            boundsTop = top,
            boundsRight = w - pad,
            boundsBottom = top + shapeH,
            cornerRadius = radius
        )
    }
}
