package com.jupiter.app.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGestureMathTest {

    @Test
    fun `isInEdgeGuard detects all four edges and allows interior`() {
        val width = 1000f
        val height = 500f
        val margin = 48f

        assertTrue(isInEdgeGuard(10f, 250f, width, height, margin))
        assertTrue(isInEdgeGuard(960f, 250f, width, height, margin))
        assertTrue(isInEdgeGuard(500f, 20f, width, height, margin))
        assertTrue(isInEdgeGuard(500f, 470f, width, height, margin))

        assertFalse(isInEdgeGuard(500f, 250f, width, height, margin))
        assertFalse(isInEdgeGuard(48f, 48f, width, height, margin))
        assertFalse(isInEdgeGuard(952f, 452f, width, height, margin))

        assertTrue(isInEdgeGuard(10f, 10f, 0f, height, margin))
        assertFalse(isInEdgeGuard(0f, 0f, width, height, 0f))
    }

    @Test
    fun `classifyDoubleTapSide splits screen into left center and right thirds`() {
        val width = 900f

        assertEquals(DoubleTapSide.LEFT, classifyDoubleTapSide(150f, width))
        assertEquals(DoubleTapSide.LEFT, classifyDoubleTapSide(299f, width))
        assertNull(classifyDoubleTapSide(450f, width))
        assertEquals(DoubleTapSide.RIGHT, classifyDoubleTapSide(601f, width))
        assertEquals(DoubleTapSide.RIGHT, classifyDoubleTapSide(850f, width))
        assertNull(classifyDoubleTapSide(100f, 0f))
    }

    @Test
    fun `classifyDragAxis locks to undecided horizontal or left right vertical`() {
        val width = 1000f
        val slop = 16f

        assertEquals(
            GestureAxis.UNDECIDED,
            classifyDragAxis(startXPx = 200f, widthPx = width, totalDxPx = 10f, totalDyPx = -12f, touchSlopPx = slop),
        )
        assertEquals(
            GestureAxis.HORIZONTAL,
            classifyDragAxis(startXPx = 200f, widthPx = width, totalDxPx = 30f, totalDyPx = 12f, touchSlopPx = slop),
        )
        assertEquals(
            GestureAxis.VERTICAL_LEFT,
            classifyDragAxis(startXPx = 250f, widthPx = width, totalDxPx = 8f, totalDyPx = -40f, touchSlopPx = slop),
        )
        assertEquals(
            GestureAxis.VERTICAL_RIGHT,
            classifyDragAxis(startXPx = 750f, widthPx = width, totalDxPx = 8f, totalDyPx = 40f, touchSlopPx = slop),
        )
    }

    @Test
    fun `computeVerticalDeltaFraction maps upward drag to positive delta`() {
        assertEquals(0.625f, computeVerticalDeltaFraction(dyPx = -500f, heightPx = 1000f), 0.0001f)
        assertEquals(-0.625f, computeVerticalDeltaFraction(dyPx = 500f, heightPx = 1000f), 0.0001f)
        assertEquals(0f, computeVerticalDeltaFraction(dyPx = -100f, heightPx = 0f), 0.0001f)
    }

    @Test
    fun `computeHorizontalSeekDeltaMs scales proportionally across width`() {
        assertEquals(45_000L, computeHorizontalSeekDeltaMs(dxPx = 500f, widthPx = 1000f))
        assertEquals(-22_500L, computeHorizontalSeekDeltaMs(dxPx = -250f, widthPx = 1000f))
        assertEquals(0L, computeHorizontalSeekDeltaMs(dxPx = 100f, widthPx = 0f))
    }

    @Test
    fun `clampBrightness and clampZoom enforce bounds and handle non finite`() {
        assertEquals(0.01f, clampBrightness(-0.5f), 0.0001f)
        assertEquals(1.0f, clampBrightness(1.4f), 0.0001f)
        assertEquals(0.42f, clampBrightness(0.42f), 0.0001f)
        assertEquals(0.5f, clampBrightness(Float.NaN), 0.0001f)

        assertEquals(-0.5, clampZoom(-2.0), 0.0001)
        assertEquals(2.0, clampZoom(5.0), 0.0001)
        assertEquals(0.75, clampZoom(0.75), 0.0001)
        assertEquals(0.0, clampZoom(Double.NaN), 0.0001)
    }

    @Test
    fun `formatZoomLabel formats exponential scale as percentage`() {
        assertEquals("100%", formatZoomLabel(0.0))
        assertEquals("200%", formatZoomLabel(1.0))
        assertEquals("400%", formatZoomLabel(2.0))
        assertEquals("71%", formatZoomLabel(-0.5))
    }

    @Test
    fun `TimeFormatters formatDuration and formatSignedDelta handle negative sub hour and multi hour`() {
        assertEquals("00:00", TimeFormatters.formatDuration(-5_000L))
        assertEquals("00:42", TimeFormatters.formatDuration(42_000L))
        assertEquals("12:05", TimeFormatters.formatDuration((12 * 60 + 5) * 1000L))
        assertEquals("1:02:03", TimeFormatters.formatDuration((3600 + 2 * 60 + 3) * 1000L))

        assertEquals("+00:18", TimeFormatters.formatSignedDelta(18_000L))
        assertEquals("-00:10", TimeFormatters.formatSignedDelta(-10_000L))
        assertEquals("+00:00", TimeFormatters.formatSignedDelta(0L))
        assertEquals("-1:01:01", TimeFormatters.formatSignedDelta(-(3600 + 60 + 1) * 1000L))
    }
}
