package com.example.litemediaplayer.comic

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

class MarginTrimmer @Inject constructor() {

    data class TrimBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        fun toRect(): Rect = Rect(left, top, right, bottom)

        fun isValid(imageWidth: Int, imageHeight: Int): Boolean {
            val trimmedArea = (right - left).coerceAtLeast(0) * (bottom - top).coerceAtLeast(0)
            val totalArea = imageWidth * imageHeight
            return trimmedArea > totalArea * 0.2
        }
    }

    fun detectBounds(
        bitmap: Bitmap,
        tolerance: Int = 30,
        contentThreshold: Float = 0.02f,
        safetyMargin: Int = 2,
        sampleScale: Int = 4
    ): TrimBounds {
        val sampleWidth = (bitmap.width / sampleScale).coerceAtLeast(1)
        val sampleHeight = (bitmap.height / sampleScale).coerceAtLeast(1)
        val sampledBitmap = Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, true)

        val bgColor = detectBackgroundColor(sampledBitmap)
        val topSample = scanFromTop(sampledBitmap, bgColor, tolerance, contentThreshold)
        val bottomSample = scanFromBottom(sampledBitmap, bgColor, tolerance, contentThreshold)
        val leftSample = scanFromLeft(sampledBitmap, bgColor, tolerance, contentThreshold)
        val rightSample = scanFromRight(sampledBitmap, bgColor, tolerance, contentThreshold)

        sampledBitmap.recycle()

        val scaleX = bitmap.width.toFloat() / sampleWidth
        val scaleY = bitmap.height.toFloat() / sampleHeight

        val rawLeft = (leftSample * scaleX).roundToInt()
        val rawTop = (topSample * scaleY).roundToInt()
        val rawRight = (rightSample * scaleX).roundToInt()
        val rawBottom = (bottomSample * scaleY).roundToInt()

        val bounds = TrimBounds(
            left = (rawLeft - safetyMargin).coerceAtLeast(0),
            top = (rawTop - safetyMargin).coerceAtLeast(0),
            right = (rawRight + safetyMargin).coerceAtMost(bitmap.width),
            bottom = (rawBottom + safetyMargin).coerceAtMost(bitmap.height)
        )

        return if (bounds.isValid(bitmap.width, bitmap.height)) {
            bounds
        } else {
            TrimBounds(0, 0, bitmap.width, bitmap.height)
        }
    }

    private fun detectBackgroundColor(bitmap: Bitmap): Int {
        val corners = listOf(
            bitmap.getPixel(0, 0),
            bitmap.getPixel(bitmap.width - 1, 0),
            bitmap.getPixel(0, bitmap.height - 1),
            bitmap.getPixel(bitmap.width - 1, bitmap.height - 1)
        )
        return corners.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: Color.WHITE
    }

    private fun isBackgroundPixel(pixel: Int, bgColor: Int, tolerance: Int): Boolean {
        return abs(Color.red(pixel) - Color.red(bgColor)) <= tolerance &&
            abs(Color.green(pixel) - Color.green(bgColor)) <= tolerance &&
            abs(Color.blue(pixel) - Color.blue(bgColor)) <= tolerance
    }

    private fun scanFromTop(
        bitmap: Bitmap,
        bgColor: Int,
        tolerance: Int,
        contentThreshold: Float
    ): Int {
        for (y in 0 until bitmap.height) {
            if (foregroundRatioForRow(bitmap, y, bgColor, tolerance) > contentThreshold) {
                return y
            }
        }
        return 0
    }

    private fun scanFromBottom(
        bitmap: Bitmap,
        bgColor: Int,
        tolerance: Int,
        contentThreshold: Float
    ): Int {
        for (y in bitmap.height - 1 downTo 0) {
            if (foregroundRatioForRow(bitmap, y, bgColor, tolerance) > contentThreshold) {
                return y
            }
        }
        return bitmap.height
    }

    private fun scanFromLeft(
        bitmap: Bitmap,
        bgColor: Int,
        tolerance: Int,
        contentThreshold: Float
    ): Int {
        for (x in 0 until bitmap.width) {
            if (foregroundRatioForColumn(bitmap, x, bgColor, tolerance) > contentThreshold) {
                return x
            }
        }
        return 0
    }

    private fun scanFromRight(
        bitmap: Bitmap,
        bgColor: Int,
        tolerance: Int,
        contentThreshold: Float
    ): Int {
        for (x in bitmap.width - 1 downTo 0) {
            if (foregroundRatioForColumn(bitmap, x, bgColor, tolerance) > contentThreshold) {
                return x
            }
        }
        return bitmap.width
    }

    private fun foregroundRatioForRow(
        bitmap: Bitmap,
        y: Int,
        bgColor: Int,
        tolerance: Int
    ): Float {
        var fgCount = 0
        val step = (bitmap.width / 100).coerceAtLeast(1)
        var total = 0
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (!isBackgroundPixel(pixel, bgColor, tolerance)) {
                fgCount += 1
            }
            total += 1
            x += step
        }
        return if (total == 0) 0f else fgCount.toFloat() / total.toFloat()
    }

    private fun foregroundRatioForColumn(
        bitmap: Bitmap,
        x: Int,
        bgColor: Int,
        tolerance: Int
    ): Float {
        var fgCount = 0
        val step = (bitmap.height / 100).coerceAtLeast(1)
        var total = 0
        var y = 0
        while (y < bitmap.height) {
            val pixel = bitmap.getPixel(x, y)
            if (!isBackgroundPixel(pixel, bgColor, tolerance)) {
                fgCount += 1
            }
            total += 1
            y += step
        }
        return if (total == 0) 0f else fgCount.toFloat() / total.toFloat()
    }
}
