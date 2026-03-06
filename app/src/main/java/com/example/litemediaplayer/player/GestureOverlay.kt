package com.example.litemediaplayer.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

private enum class GestureMode {
    Seek,
    Volume,
    Brightness
}

@Composable
fun GestureOverlay(
    seekIntervalSeconds: Int,
    onSeekCommit: (Long) -> Unit,
    onVolumeDelta: (Float) -> Int,
    onBrightnessDelta: (Float) -> Float,
    onTogglePlayPause: () -> Unit,
    onSingleTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var overlayText by remember { mutableStateOf<String?>(null) }
    var gestureMode by remember { mutableStateOf<GestureMode?>(null) }
    var seekDistanceX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .pointerInput(onTogglePlayPause, onSingleTap) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { onTogglePlayPause() }
                )
            }
            .pointerInput(seekIntervalSeconds) {
                detectDragGestures(
                    onDragStart = { offset ->
                        gestureMode = when {
                            offset.x <= size.width * 0.3f -> GestureMode.Brightness
                            offset.x >= size.width * 0.7f -> GestureMode.Volume
                            else -> GestureMode.Seek
                        }
                        seekDistanceX = 0f
                    },
                    onDragEnd = {
                        if (gestureMode == GestureMode.Seek) {
                            val deltaMs = calculateSeekDeltaMs(
                                distanceX = seekDistanceX,
                                containerWidth = size.width.toFloat(),
                                seekIntervalSeconds = seekIntervalSeconds
                            )
                            if (deltaMs != 0L) {
                                onSeekCommit(deltaMs)
                            }
                        }
                        gestureMode = null
                        overlayText = null
                    },
                    onDragCancel = {
                        gestureMode = null
                        overlayText = null
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        when (gestureMode) {
                            GestureMode.Seek -> {
                                seekDistanceX += dragAmount.x
                                val deltaMs = calculateSeekDeltaMs(
                                    distanceX = seekDistanceX,
                                    containerWidth = size.width.toFloat(),
                                    seekIntervalSeconds = seekIntervalSeconds
                                )
                                val sign = if (deltaMs >= 0) "+" else ""
                                overlayText = "シーク ${sign}${deltaMs / 1000}s"
                            }

                            GestureMode.Volume -> {
                                val currentVolumePercent = onVolumeDelta(-dragAmount.y / size.height)
                                overlayText = "音量 ${currentVolumePercent}%"
                            }

                            GestureMode.Brightness -> {
                                val currentBrightness = onBrightnessDelta(-dragAmount.y / size.height)
                                overlayText = "明るさ ${(currentBrightness * 100f).roundToLong()}%"
                            }

                            null -> Unit
                        }
                    }
                )
            }
    ) {
        content()

        val text = overlayText
        if (!text.isNullOrBlank()) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

private fun calculateSeekDeltaMs(
    distanceX: Float,
    containerWidth: Float,
    seekIntervalSeconds: Int
): Long {
    if (containerWidth <= 0f) {
        return 0L
    }

    val ratio = (distanceX / containerWidth).coerceIn(-1f, 1f)
    val maxSeekMs = seekIntervalSeconds * 1000f * 4f
    return (ratio * maxSeekMs).roundToLong()
}
