package com.jupiter.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.jupiter.app.data.local.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class JupiterShapesTest {

    @Test
    fun `computeJupiterShapes maps 0 percent to minimum radii`() {
        val shapes = computeJupiterShapes(0)

        assertEquals(0, shapes.percent)
        assertEquals(RoundedCornerShape(4.dp), shapes.navBar)
        assertEquals(RoundedCornerShape(4.dp), shapes.surface)
        assertEquals(RoundedCornerShape(4.dp), shapes.control)
        assertEquals(RoundedCornerShape(4.dp), shapes.menu)
        assertEquals(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp), shapes.sheetTop)
    }

    @Test
    fun `computeJupiterShapes maps 50 percent to balanced default radii`() {
        val shapes = computeJupiterShapes(50)

        assertEquals(50, shapes.percent)
        assertEquals(RoundedCornerShape(16.dp), shapes.navBar)
        assertEquals(RoundedCornerShape(14.dp), shapes.surface)
        assertEquals(RoundedCornerShape(12.dp), shapes.control)
        assertEquals(RoundedCornerShape(11.dp), shapes.menu)
        assertEquals(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), shapes.sheetTop)
    }

    @Test
    fun `computeJupiterShapes maps 100 percent to maximum radii`() {
        val shapes = computeJupiterShapes(100)

        assertEquals(100, shapes.percent)
        assertEquals(RoundedCornerShape(28.dp), shapes.navBar)
        assertEquals(RoundedCornerShape(24.dp), shapes.surface)
        assertEquals(RoundedCornerShape(20.dp), shapes.control)
        assertEquals(RoundedCornerShape(18.dp), shapes.menu)
        assertEquals(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), shapes.sheetTop)
    }

    @Test
    fun `computeJupiterShapes clamps out-of-range percentages`() {
        val belowZero = computeJupiterShapes(-25)
        val aboveHundred = computeJupiterShapes(175)

        assertEquals(computeJupiterShapes(0), belowZero)
        assertEquals(computeJupiterShapes(100), aboveHundred)
    }

    @Test
    fun `UserPreferences defaults to obsidian background, monochrome accent, and frosted glass`() {
        val defaults = UserPreferences()
        assertEquals(0xFF090C12.toInt(), defaults.backgroundArgb)
        assertEquals(0xFFE6ECF5.toInt(), defaults.accentArgb)
        assertEquals(GlassStyle.FROSTED, defaults.glassStyle)
        assertEquals(50, defaults.cornerRoundnessPercent)
    }
}
