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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

enum class GestureMode {
    Seek,
    Volume,
    Brightness
}

data class GestureZoneConfig(
    val brightnessZoneEnd: Float = 0.3f,
    val volumeZoneStart: Float = 0.7f
)

@Composable
fun GestureOverlay(
    seekIntervalSeconds: Int,
    onSeekCommit: (Long) -> Unit,
    onVolumeDelta: (Float) -> Int,
    onBrightnessDelta: (Float) -> Float,
    onTogglePlayPause: () -> Unit,
    onSingleTap: () -> Unit,
    zoneConfig: GestureZoneConfig = GestureZoneConfig(),
    enableSeek: Boolean = true,
    enableVolume: Boolean = true,
    enableBrightness: Boolean = true,
    enableDoubleTapPlayPause: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var overlayText by remember { mutableStateOf<String?>(null) }
    var gestureMode by remember { mutableStateOf<GestureMode?>(null) }
    var seekDistanceX by remember { mutableFloatStateOf(0f) }

    val currentSeekCommit by rememberUpdatedState(onSeekCommit)
    val currentVolumeDelta by rememberUpdatedState(onVolumeDelta)
    val currentBrightnessDelta by rememberUpdatedState(onBrightnessDelta)
    val currentTogglePlayPause by rememberUpdatedState(onTogglePlayPause)
    val currentSingleTap by rememberUpdatedState(onSingleTap)
    val currentSeekInterval by rememberUpdatedState(seekIntervalSeconds)
    val currentZoneConfig by rememberUpdatedState(zoneConfig)
    val currentEnableSeek by rememberUpdatedState(enableSeek)
    val currentEnableVolume by rememberUpdatedState(enableVolume)
    val currentEnableBrightness by rememberUpdatedState(enableBrightness)
    val currentEnableDoubleTap by rememberUpdatedState(enableDoubleTapPlayPause)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentSingleTap() },
                    onDoubleTap = {
                        if (currentEnableDoubleTap) {
                            currentTogglePlayPause()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val xFraction = offset.x / size.width.toFloat()
                        gestureMode = when {
                            xFraction <= currentZoneConfig.brightnessZoneEnd && currentEnableBrightness -> GestureMode.Brightness
                            xFraction >= currentZoneConfig.volumeZoneStart && currentEnableVolume -> GestureMode.Volume
                            currentEnableSeek -> GestureMode.Seek
                            else -> null
                        }
                        seekDistanceX = 0f
                    },
                    onDragEnd = {
                        if (gestureMode == GestureMode.Seek) {
                            val deltaMs = calculateSeekDeltaMs(
                                distanceX = seekDistanceX,
                                containerWidth = size.width.toFloat(),
                                seekIntervalSeconds = currentSeekInterval
                            )
                            if (deltaMs != 0L) {
                                currentSeekCommit(deltaMs)
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
                                    seekIntervalSeconds = currentSeekInterval
                                )
                                val sign = if (deltaMs >= 0) "+" else ""
                                overlayText = "シーク ${sign}${deltaMs / 1000}s"
                            }

                            GestureMode.Volume -> {
                                val currentVolumePercent =
                                    currentVolumeDelta(-dragAmount.y / size.height)
                                overlayText = "音量 ${currentVolumePercent}%"
                            }

                            GestureMode.Brightness -> {
                                val currentBrightness =
                                    currentBrightnessDelta(-dragAmount.y / size.height)
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
    if (containerWidth <= 0f) return 0L

    val halfWidth = containerWidth / 2f
    val ratio = (distanceX / halfWidth).coerceIn(-4f, 4f)
    val maxSeekMs = seekIntervalSeconds * 1000L
    return (ratio * maxSeekMs.toFloat()).roundToLong()
}
