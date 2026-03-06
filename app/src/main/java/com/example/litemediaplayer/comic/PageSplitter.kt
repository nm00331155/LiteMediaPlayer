package com.example.litemediaplayer.comic

import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Rect
import javax.inject.Inject
import kotlin.math.roundToInt

class PageSplitter @Inject constructor() {

    data class SplitResult(
        val pages: List<PageRegion>,
        val wasSplit: Boolean
    )

    data class PageRegion(
        val rect: Rect
    )

    fun analyze(
        imageWidth: Int,
        imageHeight: Int,
        splitThreshold: Float = 1.3f
    ): Boolean {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return false
        }
        return imageWidth.toFloat() / imageHeight > splitThreshold
    }

    fun findOptimalSplitLine(
        decoder: BitmapRegionDecoder,
        imageWidth: Int,
        imageHeight: Int,
        searchStartX: Int = (imageWidth * 0.45f).toInt(),
        searchEndX: Int = (imageWidth * 0.55f).toInt(),
        top: Int = 0,
        bottom: Int = imageHeight
    ): Int {
        val safeStart = searchStartX.coerceIn(0, imageWidth - 2)
        val safeEnd = searchEndX.coerceIn(safeStart + 1, imageWidth - 1)
        val safeTop = top.coerceIn(0, imageHeight - 1)
        val safeBottom = bottom.coerceIn(safeTop + 1, imageHeight)

        val sampleRect = Rect(safeStart, safeTop, safeEnd, safeBottom)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            inSampleSize = 8
        }

        val bitmap = runCatching {
            decoder.decodeRegion(sampleRect, options)
        }.getOrNull() ?: return imageWidth / 2

        var bestColumn = bitmap.width / 2
        var bestScore = Double.NEGATIVE_INFINITY
        val sampleStepY = (bitmap.height / 20).coerceAtLeast(1)

        for (x in 0 until bitmap.width) {
            var brightnessSum = 0.0
            var count = 0
            var y = 0
            while (y < bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                brightnessSum += (
                    Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)
                    ) / 3.0
                count += 1
                y += sampleStepY
            }

            if (count > 0) {
                val avgBrightness = brightnessSum / count
                if (avgBrightness > bestScore) {
                    bestScore = avgBrightness
                    bestColumn = x
                }
            }
        }

        bitmap.recycle()
        return safeStart + bestColumn
    }

    fun split(
        decoder: BitmapRegionDecoder,
        imageWidth: Int,
        imageHeight: Int,
        rtl: Boolean = true,
        useSmartSplit: Boolean = true,
        splitOffsetPercent: Float = 0f,
        top: Int = 0,
        bottom: Int = imageHeight,
        searchStartX: Int = (imageWidth * 0.45f).toInt(),
        searchEndX: Int = (imageWidth * 0.55f).toInt()
    ): SplitResult {
        val baseSplit = if (useSmartSplit) {
            findOptimalSplitLine(
                decoder = decoder,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                searchStartX = searchStartX,
                searchEndX = searchEndX,
                top = top,
                bottom = bottom
            )
        } else {
            imageWidth / 2
        }

        val offsetPx = (imageWidth * splitOffsetPercent.coerceIn(-0.05f, 0.05f)).roundToInt()
        val splitX = (baseSplit + offsetPx).coerceIn(1, imageWidth - 1)

        val leftRect = Rect(0, top, splitX, bottom)
        val rightRect = Rect(splitX, top, imageWidth, bottom)

        val pages = if (rtl) {
            listOf(PageRegion(rightRect), PageRegion(leftRect))
        } else {
            listOf(PageRegion(leftRect), PageRegion(rightRect))
        }

        return SplitResult(
            pages = pages,
            wasSplit = true
        )
    }
}
