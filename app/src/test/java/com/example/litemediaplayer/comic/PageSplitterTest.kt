package com.example.litemediaplayer.comic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSplitterTest {

    private val pageSplitter = PageSplitter()

    @Test
    fun wideImage_analyze_returnsTrue() {
        val result = pageSplitter.analyze(
            imageWidth = 1800,
            imageHeight = 1200,
            splitThreshold = 1.3f
        )

        assertTrue(result)
    }

    @Test
    fun portraitImage_analyze_returnsFalse() {
        val result = pageSplitter.analyze(
            imageWidth = 1200,
            imageHeight = 1800,
            splitThreshold = 1.3f
        )

        assertFalse(result)
    }

    @Test
    fun invalidSize_analyze_returnsFalse() {
        val result = pageSplitter.analyze(
            imageWidth = 0,
            imageHeight = 1200,
            splitThreshold = 1.3f
        )

        assertFalse(result)
    }
}
