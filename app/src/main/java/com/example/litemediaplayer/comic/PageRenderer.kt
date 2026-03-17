package com.example.litemediaplayer.comic

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage

@Composable
fun PageRenderer(
    model: Any,
    modifier: Modifier = Modifier,
    doubleTapZoomScale: Float = 2f,
    onZoomStateChange: (Boolean) -> Unit = {},
    doubleTapRequest: Offset? = null,
    onDoubleTapRequestConsumed: () -> Unit = {}
) {
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember(model) { mutableStateOf(IntSize.Zero) }
    var lastReportedZoomed by remember(model) { mutableStateOf(false) }

    fun reportZoomState(currentScale: Float) {
        val zoomed = currentScale > 1.01f
        if (zoomed != lastReportedZoomed) {
            lastReportedZoomed = zoomed
            onZoomStateChange(zoomed)
        }
    }

    fun clampOffset(candidate: Offset, currentScale: Float): Offset {
        if (currentScale <= 1f || viewportSize == IntSize.Zero) {
            return Offset.Zero
        }

        val maxX = (viewportSize.width * (currentScale - 1f)) / 2f
        val maxY = (viewportSize.height * (currentScale - 1f)) / 2f
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY)
        )
    }

    fun toggleZoomAt(tapOffset: Offset) {
        if (scale > 1f) {
            scale = 1f
            offset = Offset.Zero
        } else {
            val targetScale = doubleTapZoomScale.coerceAtLeast(1f)
            val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
            val targetOffset = (center - tapOffset) * (targetScale - 1f)

            scale = targetScale
            offset = clampOffset(targetOffset, targetScale)
        }
        reportZoomState(scale)
    }

    LaunchedEffect(model) {
        onZoomStateChange(false)
    }

    LaunchedEffect(doubleTapRequest, viewportSize) {
        val requestedOffset = doubleTapRequest ?: return@LaunchedEffect
        if (viewportSize == IntSize.Zero) {
            return@LaunchedEffect
        }

        toggleZoomAt(requestedOffset)
        onDoubleTapRequestConsumed()
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { viewportSize = it }
            .pointerInput(doubleTapZoomScale) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        toggleZoomAt(tapOffset)
                    }
                )
            }
            .pointerInput(scale, viewportSize) {
                detectDragGestures { change, dragAmount ->
                    if (scale <= 1f) {
                        return@detectDragGestures
                    }

                    change.consume()
                    offset = clampOffset(offset + dragAmount, scale)
                }
            }
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
