package moe.ouom.neriplayer.ui.component.common

import androidx.compose.animation.core.CubicBezierEasing

/**
 * 全屏详情打开时，底层场景统一使用的景深参数。
 *
 * 歌单卡片与 MiniPlayer → NowPlaying 共用同一放大倍率，避免两套动效手感割裂。
 */
internal object SceneDepthMotion {
    const val ExpandedScale = 1.20f
    const val OpenDurationMillis = 560
    const val CloseDurationMillis = 440

    /** 快速建立方向、长尾柔和落位。 */
    val OpenEasing = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)

    /** 关闭前段克制、后段明确收回，但避免临近源卡片时突然加速。 */
    val CloseEasing = CubicBezierEasing(0.32f, 0f, 0.20f, 1f)
}
