package moe.ouom.neriplayer.ui.component.playback

/*
 * Apple Music 风格播放控件图标：
 * 实心、圆润、对称、无按钮底衬，直接叠在正在播放背景上。
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
import kotlin.math.hypot
import kotlin.math.min

private fun Offset.distanceTo(other: Offset): Float =
    hypot(this.x - other.x, this.y - other.y)

private fun Offset.pointTowards(other: Offset, distance: Float): Offset {
    val len = distanceTo(other)
    if (len <= 0f) return this
    val t = (distance / len).coerceIn(0f, 1f)
    return Offset(x + (other.x - x) * t, y + (other.y - y) * t)
}

/**
 * 对称圆角三角形：三个顶点用同一半径倒圆，保证不歪斜。
 * 顶点顺序：backTop → tip → backBottom（朝右时 tip 在右）。
 */
private fun DrawScope.drawSymmetricRoundedTriangle(
    color: Color,
    backTop: Offset,
    tip: Offset,
    backBottom: Offset,
    cornerRadius: Float
) {
    // 边长，取三边最小值限制圆角，避免过切导致变形
    val e1 = backTop.distanceTo(tip)
    val e2 = tip.distanceTo(backBottom)
    val e3 = backBottom.distanceTo(backTop)
    val r = cornerRadius
        .coerceAtMost(min(e1, min(e2, e3)) * 0.45f)
        .coerceAtLeast(0f)
    if (r <= 0.5f) {
        val path = Path().apply {
            moveTo(backTop.x, backTop.y)
            lineTo(tip.x, tip.y)
            lineTo(backBottom.x, backBottom.y)
            close()
        }
        drawPath(path, color, style = Fill)
        return
    }

    // 每条边从两端向内缩 r，得到切点
    val backTopToTip = backTop.pointTowards(tip, r)
    val tipToBackTop = tip.pointTowards(backTop, r)
    val tipToBackBottom = tip.pointTowards(backBottom, r)
    val backBottomToTip = backBottom.pointTowards(tip, r)
    val backBottomToBackTop = backBottom.pointTowards(backTop, r)
    val backTopToBackBottom = backTop.pointTowards(backBottom, r)

    val path = Path().apply {
        moveTo(backTopToTip.x, backTopToTip.y)
        lineTo(tipToBackTop.x, tipToBackTop.y)
        quadraticTo(tip.x, tip.y, tipToBackBottom.x, tipToBackBottom.y)
        lineTo(backBottomToTip.x, backBottomToTip.y)
        quadraticTo(
            backBottom.x,
            backBottom.y,
            backBottomToBackTop.x,
            backBottomToBackTop.y
        )
        lineTo(backTopToBackBottom.x, backTopToBackBottom.y)
        quadraticTo(backTop.x, backTop.y, backTopToTip.x, backTopToTip.y)
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

/**
 * 单个朝右/朝左的厚实圆润三角（用于播放键）。
 * direction > 0 朝右，< 0 朝左。
 */
private fun DrawScope.drawPlayTriangle(
    color: Color,
    direction: Int,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    cornerRadius: Float
) {
    val centerY = (top + bottom) / 2f
    val tip = Offset(if (direction > 0) right else left, centerY)
    val backTop = Offset(if (direction > 0) left else right, top)
    val backBottom = Offset(if (direction > 0) left else right, bottom)
    drawSymmetricRoundedTriangle(
        color = color,
        backTop = backTop,
        tip = tip,
        backBottom = backBottom,
        cornerRadius = cornerRadius
    )
}

/** 播放：实心对称圆润三角（朝右） */
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
        // 比例贴近 Apple Music：三角略偏高、厚实
        val shapeW = w * 0.72f
        val shapeH = h * 0.86f
        val left = (w - shapeW) / 2f
        val top = (h - shapeH) / 2f
        val right = left + shapeW
        val bottom = top + shapeH
        drawPlayTriangle(
            color = tint,
            direction = 1,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            cornerRadius = min(shapeW, shapeH) * 0.28f
        )
    }
}

/** 暂停：两根对称实心圆角竖条 */
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
        val barWidth = w * 0.26f
        val barHeight = h * 0.78f
        val gap = w * 0.16f
        val top = (h - barHeight) / 2f
        val total = barWidth * 2f + gap
        val leftStart = (w - total) / 2f
        val radius = barWidth * 0.45f
        drawRoundedBar(tint, leftStart, top, barWidth, barHeight, radius)
        drawRoundedBar(tint, leftStart + barWidth + gap, top, barWidth, barHeight, radius)
    }
}

/** 上一首：两个对称圆润实心三角，朝左 */
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
        val shapeH = h * 0.72f
        val shapeW = w * 0.40f
        val top = (h - shapeH) / 2f
        val bottom = top + shapeH
        val radius = min(shapeW, shapeH) * 0.30f

        // 右侧（靠后）略小的三角
        val backRight = w - w * 0.04f
        val backLeft = backRight - shapeW * 0.88f
        drawPlayTriangle(
            color = tint,
            direction = -1,
            left = backLeft,
            top = top + h * 0.02f,
            right = backRight,
            bottom = bottom - h * 0.02f,
            cornerRadius = radius * 0.9f
        )

        // 左侧（靠前）完整三角
        val frontLeft = w * 0.04f
        val frontRight = frontLeft + shapeW
        drawPlayTriangle(
            color = tint,
            direction = -1,
            left = frontLeft,
            top = top,
            right = frontRight,
            bottom = bottom,
            cornerRadius = radius
        )
    }
}

/** 下一首：两个对称圆润实心三角，朝右 */
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
        val shapeH = h * 0.72f
        val shapeW = w * 0.40f
        val top = (h - shapeH) / 2f
        val bottom = top + shapeH
        val radius = min(shapeW, shapeH) * 0.30f

        // 左侧（靠后）略小的三角
        val backLeft = w * 0.04f
        val backRight = backLeft + shapeW * 0.88f
        drawPlayTriangle(
            color = tint,
            direction = 1,
            left = backLeft,
            top = top + h * 0.02f,
            right = backRight,
            bottom = bottom - h * 0.02f,
            cornerRadius = radius * 0.9f
        )

        // 右侧（靠前）完整三角
        val frontRight = w - w * 0.04f
        val frontLeft = frontRight - shapeW
        drawPlayTriangle(
            color = tint,
            direction = 1,
            left = frontLeft,
            top = top,
            right = frontRight,
            bottom = bottom,
            cornerRadius = radius
        )
    }
}
