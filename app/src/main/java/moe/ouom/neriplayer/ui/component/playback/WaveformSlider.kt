package moe.ouom.neriplayer.ui.component.playback

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * File: moe.ouom.neriplayer.ui.component/WaveformSlider
 * Created: 2025/8/11
 */

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val TRACK_HEIGHT_DP = 4.5f
private const val TRACK_HEIGHT_DRAGGED_DP = 7f

@Composable
fun WaveformSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onValueChangeStarted: (Float) -> Unit = {},
    onValueChangeCanceled: () -> Unit = {},
    enabled: Boolean = true,
    isPlaybackWaiting: Boolean = false,
    isProgressStalled: Boolean = false,
    isProgressPreviewing: Boolean = false,
    activeTint: Color = MaterialTheme.colorScheme.primary,
    durationMs: Long = 0L,
    playbackSpeed: Float = 1f,
    playbackSessionKey: String? = null
) {
    val clampedValue = normalizeWaveProgress(value)
    // 未播放部分更浅，已播放部分更深 —— 用颜色深浅表示进度
    val activeColor = activeTint.copy(alpha = if (enabled) 1f else 0.45f)
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.22f else 0.12f)

    var isDragging by remember { mutableStateOf(false) }
    val latestOnValueChangeCanceled by rememberUpdatedState(onValueChangeCanceled)
    LaunchedEffect(enabled, isDragging) {
        if (!enabled && isDragging) {
            isDragging = false
            latestOnValueChangeCanceled()
        }
    }

    val trackHeight by animateFloatAsState(
        targetValue = if (isDragging) TRACK_HEIGHT_DRAGGED_DP else TRACK_HEIGHT_DP,
        animationSpec = tween(durationMillis = 160, easing = LinearEasing),
        label = "track_height_animation"
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    val isProgressPredicting = shouldPredictWaveProgress(
        isWaveAnimating = animationsEnabled && enabled && isPlaying && !isDragging,
        isProgressStalled = isProgressStalled,
        isProgressPreviewing = isProgressPreviewing
    )
    val waveProgress = remember(playbackSessionKey) { WaveProgressPredictor(clampedValue) }
    LaunchedEffect(
        playbackSessionKey,
        clampedValue,
        durationMs,
        playbackSpeed,
        isProgressPredicting
    ) {
        waveProgress.updateTarget(
            targetValue = clampedValue,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            animate = isProgressPredicting
        )
    }
    LaunchedEffect(lifecycleOwner, waveProgress, isProgressPredicting) {
        if (!isProgressPredicting) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            waveProgress.resetFrameAnchor()
            while (isActive) {
                val frameNs = withFrameNanos { it }
                waveProgress.onFrame(frameNs)
            }
        }
    }

    val dragModifier = if (enabled) {
        Modifier.pointerInput(
            onValueChange,
            onValueChangeFinished,
            onValueChangeStarted,
            onValueChangeCanceled
        ) {
            detectDragGestures(
                onDragStart = { offset ->
                    isDragging = true
                    val width = size.width.toFloat()
                    if (width > 0f) {
                        val startValue = (offset.x / width).coerceIn(0f, 1f)
                        onValueChangeStarted(startValue)
                    }
                },
                onDragEnd = {
                    isDragging = false
                    onValueChangeFinished()
                },
                onDragCancel = {
                    isDragging = false
                    onValueChangeCanceled()
                },
                onDrag = { change, _ ->
                    val width = size.width.toFloat()
                    if (width > 0f) {
                        val newValue = (change.position.x / width).coerceIn(0f, 1f)
                        onValueChange(newValue)
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .then(dragModifier)
    ) {
        val centerY = size.height / 2
        val progressValue = if (isProgressPredicting) {
            waveProgress.currentValue
        } else {
            clampedValue
        }
        val progressPx = (progressValue * size.width).coerceIn(0f, size.width)
        val barHeight = trackHeight.dp.toPx()

        drawLine(
            color = inactiveColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = barHeight,
            cap = StrokeCap.Round
        )

        if (progressPx > 0f) {
            drawLine(
                color = activeColor,
                start = Offset(0f, centerY),
                end = Offset(progressPx, centerY),
                strokeWidth = barHeight,
                cap = StrokeCap.Round
            )
        }
    }
}

internal fun resolveWaveProgress(
    anchorValue: Float,
    durationMs: Long,
    playbackSpeed: Float,
    elapsedNs: Long
): Float {
    val anchor = normalizeWaveProgress(anchorValue)
    if (durationMs <= 0L) return anchor
    val speed = normalizeWavePlaybackSpeed(playbackSpeed)
    if (speed <= 0f) return anchor
    val elapsedMs = elapsedNs.coerceAtLeast(0L).toDouble() / NANOS_PER_MILLISECOND
    val progressAdvance = elapsedMs / durationMs.toDouble() * speed
    return (anchor + progressAdvance.toFloat()).coerceIn(0f, 1f)
}

internal fun shouldPredictWaveProgress(
    isWaveAnimating: Boolean,
    isProgressStalled: Boolean,
    isProgressPreviewing: Boolean
): Boolean {
    return isWaveAnimating && !isProgressStalled && !isProgressPreviewing
}

private fun normalizeWaveProgress(value: Float): Float {
    return value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
}

private fun normalizeWavePlaybackSpeed(value: Float): Float {
    return value.takeIf { it.isFinite() && it > 0f } ?: 0f
}

internal class WaveProgressPredictor(initialValue: Float) {
    private var anchorValue = normalizeWaveProgress(initialValue)
    private var targetValue = anchorValue
    private var durationMs = 0L
    private var playbackSpeed = 0f
    private var anchorFrameNs = 0L
    private var hasPendingAnchor = true
    private var wasAnimating = false

    var currentValue = anchorValue
        private set

    fun updateTarget(
        targetValue: Float,
        durationMs: Long,
        playbackSpeed: Float,
        animate: Boolean
    ) {
        val normalizedTarget = normalizeWaveProgress(targetValue)
        val normalizedDurationMs = durationMs.coerceAtLeast(0L)
        val normalizedPlaybackSpeed = normalizeWavePlaybackSpeed(playbackSpeed)
        val inputChanged = normalizedTarget != this.targetValue ||
            normalizedDurationMs != this.durationMs ||
            normalizedPlaybackSpeed != this.playbackSpeed
        if (inputChanged || !animate || !wasAnimating) {
            anchorValue = normalizedTarget
            currentValue = normalizedTarget
            hasPendingAnchor = true
        }
        this.targetValue = normalizedTarget
        this.durationMs = normalizedDurationMs
        this.playbackSpeed = normalizedPlaybackSpeed
        wasAnimating = animate
    }

    fun onFrame(frameNs: Long) {
        if (frameNs <= 0L) return
        if (hasPendingAnchor || anchorFrameNs == 0L || frameNs < anchorFrameNs) {
            anchorFrameNs = frameNs
            currentValue = anchorValue
            hasPendingAnchor = false
            return
        }
        currentValue = resolveWaveProgress(
            anchorValue = anchorValue,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            elapsedNs = frameNs - anchorFrameNs
        )
    }

    fun resetFrameAnchor() {
        anchorValue = currentValue
        anchorFrameNs = 0L
        hasPendingAnchor = true
    }
}
