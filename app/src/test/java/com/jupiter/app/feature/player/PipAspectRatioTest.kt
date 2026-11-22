package com.jupiter.app.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PipAspectRatioTest {

    @Test
    fun `clampDimensions returns 16 to 9 fallback when width or height is zero or negative`() {
        assertEquals(16 to 9, PipAspectRatio.clampDimensions(0, 1080))
        assertEquals(16 to 9, PipAspectRatio.clampDimensions(1920, 0))
        assertEquals(16 to 9, PipAspectRatio.clampDimensions(0, 0))
        assertEquals(16 to 9, PipAspectRatio.clampDimensions(-1920, 1080))
        assertEquals(16 to 9, PipAspectRatio.clampDimensions(1920, -1080))
    }

    @Test
    fun `clampDimensions clamps ultra-wide ratios above 239 to 100`() {
        assertEquals(
            PipAspectRatio.MAX_NUMERATOR to PipAspectRatio.MAX_DENOMINATOR,
            PipAspectRatio.clampDimensions(3840, 1080),
        )
        assertEquals(
            PipAspectRatio.MAX_NUMERATOR to PipAspectRatio.MAX_DENOMINATOR,
            PipAspectRatio.clampDimensions(240, 100),
        )
        assertEquals(239 to 100, PipAspectRatio.clampDimensions(239, 100))
    }

    @Test
    fun `clampDimensions clamps ultra-tall ratios below 100 to 239`() {
        assertEquals(
            PipAspectRatio.MIN_NUMERATOR to PipAspectRatio.MIN_DENOMINATOR,
            PipAspectRatio.clampDimensions(1080, 3840),
        )
        assertEquals(
            PipAspectRatio.MIN_NUMERATOR to PipAspectRatio.MIN_DENOMINATOR,
            PipAspectRatio.clampDimensions(99, 239),
        )
        assertEquals(100 to 239, PipAspectRatio.clampDimensions(100, 239))
    }

    @Test
    fun `clampDimensions preserves standard 16 to 9 and 4 to 3 dimensions`() {
        assertEquals(1920 to 1080, PipAspectRatio.clampDimensions(1920, 1080))
        assertEquals(1440 to 1080, PipAspectRatio.clampDimensions(1440, 1080))
        assertEquals(1080 to 1920, PipAspectRatio.clampDimensions(1080, 1920))
    }
}
