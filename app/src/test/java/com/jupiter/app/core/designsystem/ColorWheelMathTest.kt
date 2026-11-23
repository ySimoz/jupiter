package com.jupiter.app.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorWheelMathTest {

    @Test
    fun `hsvToArgb and argbToHsv round-trip across hues, shades, and presets`() {
        val samples = listOf(
            0xFFFF0000.toInt(),
            0xFF00FF00.toInt(),
            0xFF0000FF.toInt(),
            0xFF5CE1E6.toInt(),
            0xFF090C12.toInt(),
            0xFFF4F6FA.toInt(),
            0xFF7DA8F5.toInt(),
            0xFFE8B86D.toInt(),
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
        ) + BackgroundColorPresets.map { it.argb } +
            AccentColorPresets.map { it.argb } +
            TextColorPresets.map { it.argb }
        for (argb in samples) {
            val (h, s, v) = ColorWheelMath.argbToHsv(argb)
            val roundTrip = ColorWheelMath.hsvToArgb(h, s, v)
            assertEquals("Round-trip failed for ${ColorWheelMath.formatHex(argb)}", argb, roundTrip)
        }
    }

    @Test
    fun `background, accent, and text preset lists expose expected curated defaults`() {
        assertEquals(5, BackgroundColorPresets.size)
        assertEquals("Obsidian" to 0xFF090C12.toInt(), BackgroundColorPresets[0].label to BackgroundColorPresets[0].argb)
        assertEquals(5, AccentColorPresets.size)
        assertEquals("Monochrome" to 0xFFE6ECF5.toInt(), AccentColorPresets[0].label to AccentColorPresets[0].argb)
        assertEquals(5, TextColorPresets.size)
        assertEquals("Snow" to 0xFFF2F5FA.toInt(), TextColorPresets[0].label to TextColorPresets[0].argb)
    }

    @Test
    fun `hsvToArgb handles hue wraparound and clamps saturation and value`() {
        assertEquals(ColorWheelMath.hsvToArgb(0f, 1f, 1f), ColorWheelMath.hsvToArgb(360f, 1f, 1f))
        assertEquals(ColorWheelMath.hsvToArgb(300f, 1f, 1f), ColorWheelMath.hsvToArgb(-60f, 1f, 1f))
        assertEquals(0xFFFF0000.toInt(), ColorWheelMath.hsvToArgb(0f, 2f, 2f))
        assertEquals(0xFF000000.toInt(), ColorWheelMath.hsvToArgb(120f, 0.5f, -0.4f))
    }

    @Test
    fun `pointToHueSaturation maps angles and clamps radius`() {
        assertEquals(0f to 0f, ColorWheelMath.pointToHueSaturation(0f, 0f, 100f))
        assertEquals(0f to 0f, ColorWheelMath.pointToHueSaturation(10f, 10f, 0f))

        val (hRight, sRight) = ColorWheelMath.pointToHueSaturation(50f, 0f, 100f)
        assertEquals(0f, hRight, 0.01f)
        assertEquals(0.5f, sRight, 0.01f)

        val (hDown, sDown) = ColorWheelMath.pointToHueSaturation(0f, 100f, 100f)
        assertEquals(90f, hDown, 0.01f)
        assertEquals(1f, sDown, 0.01f)

        val (hLeft, sLeft) = ColorWheelMath.pointToHueSaturation(-250f, 0f, 100f)
        assertEquals(180f, hLeft, 0.01f)
        assertEquals(1f, sLeft, 0.01f)

        val (hUp, sUp) = ColorWheelMath.pointToHueSaturation(0f, -150f, 100f)
        assertEquals(270f, hUp, 0.01f)
        assertEquals(1f, sUp, 0.01f)
    }

    @Test
    fun `luminance and on-color contrast adapt for dark and light fills`() {
        assertEquals(0.0, ColorWheelMath.relativeLuminance(0xFF000000.toInt()), 0.0001)
        assertEquals(1.0, ColorWheelMath.relativeLuminance(0xFFFFFFFF.toInt()), 0.0001)

        assertFalse(ColorWheelMath.isLightColor(0xFF090C12.toInt()))
        assertTrue(ColorWheelMath.isLightColor(0xFFF4F6FA.toInt()))
        assertTrue(ColorWheelMath.isLightColor(0xFF5CE1E6.toInt()))

        assertEquals(0xFFF2F5FA.toInt(), ColorWheelMath.computeOnColorArgb(0xFF090C12.toInt()))
        assertEquals(0xFF090C12.toInt(), ColorWheelMath.computeOnColorArgb(0xFFF4F6FA.toInt()))
        assertEquals(0xFF090C12.toInt(), ColorWheelMath.computeOnColorArgb(0xFF5CE1E6.toInt()))
    }

    @Test
    fun `blendArgb and formatHex behave deterministically`() {
        assertEquals(0xFF000000.toInt(), ColorWheelMath.blendArgb(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0f))
        assertEquals(0xFFFFFFFF.toInt(), ColorWheelMath.blendArgb(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 1f))
        assertEquals(0xFF808080.toInt(), ColorWheelMath.blendArgb(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))

        assertEquals("#090C12", ColorWheelMath.formatHex(0xFF090C12.toInt()))
        assertEquals("#5CE1E6", ColorWheelMath.formatHex(0x555CE1E6))
    }

    @Test
    fun `deriveJupiterPalette adapts surfaces, text, and borders for dark and light canvases`() {
        val dark = deriveJupiterPalette(0xFF090C12.toInt())
        assertFalse(dark.isLightCanvas)
        assertEquals(Color(0xFF090C12), dark.canvas)
        assertEquals(Color(0xFFF2F5FA), dark.textPrimary)
        assertNotEquals(dark.canvas, dark.glassPrimary)
        assertNotEquals(dark.glassPrimary, dark.glassElevated)

        val midnightBlue = deriveJupiterPalette(0xFF0D1B2A.toInt())
        assertFalse(midnightBlue.isLightCanvas)
        assertEquals(Color(0xFF0D1B2A), midnightBlue.canvas)
        assertTrue(midnightBlue.glassPrimary.blue > midnightBlue.glassPrimary.red)

        val light = deriveJupiterPalette(0xFFF4F6FA.toInt())
        assertTrue(light.isLightCanvas)
        assertEquals(Color(0xFFF4F6FA), light.canvas)
        assertEquals(Color(0xFF111620), light.textPrimary)
        assertNotEquals(light.canvas, light.glassPrimary)
        assertNotEquals(light.glassPrimary, light.glassElevated)

        val white = deriveJupiterPalette(0xFFFFFFFF.toInt())
        assertTrue(white.isLightCanvas)
        assertNotEquals(white.canvas, white.glassPrimary)
        assertNotEquals(white.glassPrimary, white.glassElevated)

        assertEquals(0xFFF2F5FA.toInt(), defaultTextArgbForCanvas(0xFF090C12.toInt()))
        assertEquals(0xFF111620.toInt(), defaultTextArgbForCanvas(0xFFF4F6FA.toInt()))

        val customDarkText = deriveJupiterPalette(
            canvasArgb = 0xFF090C12.toInt(),
            textArgb = 0x33E8D2B5,
        )
        assertEquals(Color(0xFFE8D2B5), customDarkText.textPrimary)
        assertEquals(
            Color(ColorWheelMath.blendArgb(0xFFE8D2B5.toInt(), 0xFF090C12.toInt(), 0.34f)),
            customDarkText.textSecondary,
        )

        val customLightText = deriveJupiterPalette(
            canvasArgb = 0xFFF4F6FA.toInt(),
            textArgb = 0xFF1E293B.toInt(),
        )
        assertEquals(Color(0xFF1E293B), customLightText.textPrimary)
        assertEquals(
            Color(ColorWheelMath.blendArgb(0xFF1E293B.toInt(), 0xFFF4F6FA.toInt(), 0.34f)),
            customLightText.textSecondary,
        )
    }
}
