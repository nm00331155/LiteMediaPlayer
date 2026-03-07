package com.example.litemediaplayer.comic

import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import java.io.ByteArrayInputStream
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
        val bytes = if (inputStream is ByteArrayInputStream) {
            val buffer = ByteArray(inputStream.available())
            val read = inputStream.read(buffer)
            if (read <= 0) {
                ByteArray(0)
            } else if (read == buffer.size) {
                buffer
            } else {
                buffer.copyOf(read)
            }
        } else {
            inputStream.readBytes()
        }

        return@withContext processBytes(bytes = bytes, settings = settings)
    }

    suspend fun process(
        bytes: ByteArray,
        settings: ComicReaderSettings
    ): List<ProcessedPage> = withContext(Dispatchers.Default) {
        processBytes(bytes = bytes, settings = settings)
    }

    private fun processBytes(
        bytes: ByteArray,
        settings: ComicReaderSettings
    ): List<ProcessedPage> {
        if (bytes.isEmpty()) {
            return emptyList()
        }

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val width = boundsOptions.outWidth
        val height = boundsOptions.outHeight
        if (width <= 0 || height <= 0) {
            return emptyList()
        }

        var trimRect = Rect(0, 0, width, height)
        if (settings.autoTrimEnabled) {
            val maxDim = maxOf(width, height)
            val sampleSize = when {
                maxDim > 6_000 -> 8
                maxDim > 3_000 -> 4
                maxDim > 1_500 -> 2
                else -> 1
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            if (bitmap != null) {
                val threshold = settings.trimSensitivity.threshold
                val bounds = trimmer.detectBounds(
                    bitmap = bitmap,
                    tolerance = settings.trimTolerance,
                    contentThreshold = threshold,
                    safetyMargin = settings.trimSafetyMargin
                )
                trimRect = Rect(
                    (bounds.left * sampleSize).coerceIn(0, width),
                    (bounds.top * sampleSize).coerceIn(0, height),
                    (bounds.right * sampleSize).coerceIn(0, width),
                    (bounds.bottom * sampleSize).coerceIn(0, height)
                )
                bitmap.recycle()
            }
        }

        @Suppress("DEPRECATION")
        val decoder = runCatching {
            BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
        }.getOrElse {
            return listOf(ProcessedPage(rect = trimRect, wasSplit = false))
        }

        return try {
            val trimmedWidth = trimRect.width()
            val trimmedHeight = trimRect.height()
            val shouldSplit = settings.mode == ReaderMode.PAGE &&
                settings.autoSplitEnabled &&
                splitter.analyze(trimmedWidth, trimmedHeight, settings.splitThreshold)

            if (!shouldSplit) {
                return listOf(
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
