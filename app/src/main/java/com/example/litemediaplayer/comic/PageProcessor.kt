package com.example.litemediaplayer.comic

import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PageProcessor @Inject constructor(
    private val splitter: PageSplitter,
    private val trimmer: MarginTrimmer
) {
    data class ProcessedPage(
        val rect: Rect,
        val wasSplit: Boolean
    )

    suspend fun process(
        inputStream: InputStream,
        settings: ComicReaderSettings
    ): List<ProcessedPage> = withContext(Dispatchers.Default) {
        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) {
            return@withContext emptyList()
        }

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val width = boundsOptions.outWidth
        val height = boundsOptions.outHeight
        if (width <= 0 || height <= 0) {
            return@withContext emptyList()
        }

        var trimRect = Rect(0, 0, width, height)
        if (settings.autoTrimEnabled) {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                val threshold = settings.trimSensitivity.threshold
                val bounds = trimmer.detectBounds(
                    bitmap = bitmap,
                    tolerance = settings.trimTolerance,
                    contentThreshold = threshold,
                    safetyMargin = settings.trimSafetyMargin
                )
                trimRect = bounds.toRect()
                bitmap.recycle()
            }
        }

        @Suppress("DEPRECATION")
        val decoder = BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
            ?: return@withContext emptyList()

        try {
            val trimmedWidth = trimRect.width()
            val trimmedHeight = trimRect.height()
            val shouldSplit = settings.mode == ReaderMode.PAGE &&
                settings.autoSplitEnabled &&
                splitter.analyze(trimmedWidth, trimmedHeight, settings.splitThreshold)

            if (!shouldSplit) {
                return@withContext listOf(
                    ProcessedPage(rect = trimRect, wasSplit = false)
                )
            }

            val searchStart = trimRect.left + (trimmedWidth * 0.45f).toInt()
            val searchEnd = trimRect.left + (trimmedWidth * 0.55f).toInt()

            val baseSplitX = if (settings.smartSplitEnabled) {
                splitter.findOptimalSplitLine(
                    decoder = decoder,
                    imageWidth = width,
                    imageHeight = height,
                    searchStartX = searchStart,
                    searchEndX = searchEnd,
                    top = trimRect.top,
                    bottom = trimRect.bottom
                )
            } else {
                trimRect.left + trimmedWidth / 2
            }

            val offsetPx = (trimmedWidth * settings.splitOffsetPercent.coerceIn(-0.05f, 0.05f)).toInt()
            val splitX = (baseSplitX + offsetPx).coerceIn(trimRect.left + 1, trimRect.right - 1)

            val leftRect = Rect(trimRect.left, trimRect.top, splitX, trimRect.bottom)
            val rightRect = Rect(splitX, trimRect.top, trimRect.right, trimRect.bottom)
            val pages = if (settings.readingDirection == ReadingDirection.RTL) {
                listOf(rightRect, leftRect)
            } else {
                listOf(leftRect, rightRect)
            }

            pages.map { rect ->
                ProcessedPage(rect = rect, wasSplit = true)
            }
        } finally {
            decoder.recycle()
        }
    }
}
