package com.jupiter.app.core.adaptive

import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowSizeUtilsTest {

    @Test
    fun `compact width maps to bottom bar without side sheet`() {
        val info = computeAdaptiveInfo(
            widthSizeClass = WindowWidthSizeClass.Compact,
            heightSizeClass = WindowHeightSizeClass.Medium,
        )

        assertEquals(WindowWidthSizeClass.Compact, info.widthSizeClass)
        assertEquals(WindowHeightSizeClass.Medium, info.heightSizeClass)
        assertEquals(NavigationLayout.BOTTOM_BAR, info.navigationLayout)
        assertFalse(info.useSideSheet)
        assertEquals(160, info.gridMinColumnDp)
    }

    @Test
    fun `medium width maps to navigation rail with side sheet`() {
        val info = computeAdaptiveInfo(
            widthSizeClass = WindowWidthSizeClass.Medium,
            heightSizeClass = WindowHeightSizeClass.Compact,
        )

        assertEquals(WindowWidthSizeClass.Medium, info.widthSizeClass)
        assertEquals(WindowHeightSizeClass.Compact, info.heightSizeClass)
        assertEquals(NavigationLayout.NAVIGATION_RAIL, info.navigationLayout)
        assertTrue(info.useSideSheet)
        assertEquals(220, info.gridMinColumnDp)
    }

    @Test
    fun `expanded width maps to permanent rail with side sheet`() {
        val info = computeAdaptiveInfo(
            widthSizeClass = WindowWidthSizeClass.Expanded,
            heightSizeClass = WindowHeightSizeClass.Expanded,
        )

        assertEquals(WindowWidthSizeClass.Expanded, info.widthSizeClass)
        assertEquals(WindowHeightSizeClass.Expanded, info.heightSizeClass)
        assertEquals(NavigationLayout.PERMANENT_RAIL, info.navigationLayout)
        assertTrue(info.useSideSheet)
        assertEquals(240, info.gridMinColumnDp)
    }
}
