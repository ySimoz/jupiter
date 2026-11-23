package com.jupiter.app.core.designsystem

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassOverlayMathTest {

    @Test
    fun overlayHost_filtersFloatingEntriesByActiveOwnerKey() {
        val host = GlassOverlayHostState(HazeState())
        val libraryFab = GlassOverlayEntry(id = host.nextId(), slotKey = "fab", ownerKey = "LIBRARY") {}
        val playlistsFab = GlassOverlayEntry(id = host.nextId(), slotKey = "fab", ownerKey = "PLAYLISTS") {}
        host.add(libraryFab)
        host.add(playlistsFab)

        host.activeOwnerKey = "SETTINGS"
        assertTrue(host.visibleEntries().isEmpty())

        host.activeOwnerKey = "LIBRARY"
        assertEquals(listOf(libraryFab.id), host.visibleEntries().map { it.id })

        host.activeOwnerKey = "PLAYLISTS"
        assertEquals(listOf(playlistsFab.id), host.visibleEntries().map { it.id })
    }

    @Test
    fun leftSideAnchor_opensBelowAndLeftAligned() {
        val offset = computeDropdownMenuOffset(
            anchorBounds = Rect(left = 40f, top = 120f, right = 140f, bottom = 160f),
            menuSize = IntSize(width = 200, height = 160),
            containerSize = IntSize(width = 1080, height = 2400),
            marginPx = 16,
            verticalGapPx = 8,
        )

        assertEquals(IntOffset(x = 40, y = 168), offset)
    }

    @Test
    fun rightSideAnchor_opensBelowAndRightAligned() {
        val offset = computeDropdownMenuOffset(
            anchorBounds = Rect(left = 960f, top = 120f, right = 1040f, bottom = 160f),
            menuSize = IntSize(width = 220, height = 160),
            containerSize = IntSize(width = 1080, height = 2400),
            marginPx = 16,
            verticalGapPx = 8,
        )

        assertEquals(IntOffset(x = 820, y = 168), offset)
    }

    @Test
    fun bottomEdgeAnchor_flipsAboveAnchor() {
        val offset = computeDropdownMenuOffset(
            anchorBounds = Rect(left = 960f, top = 2280f, right = 1040f, bottom = 2340f),
            menuSize = IntSize(width = 200, height = 180),
            containerSize = IntSize(width = 1080, height = 2400),
            marginPx = 16,
            verticalGapPx = 8,
        )

        assertEquals(IntOffset(x = 840, y = 2092), offset)
    }

    @Test
    fun edgeTouchingAnchor_clampsWithinContainerMargins() {
        val offset = computeDropdownMenuOffset(
            anchorBounds = Rect(left = 0f, top = 0f, right = 20f, bottom = 20f),
            menuSize = IntSize(width = 260, height = 300),
            containerSize = IntSize(width = 400, height = 320),
            marginPx = 16,
            verticalGapPx = 8,
        )

        assertEquals(16, offset.x)
        assertTrue(offset.y >= 16)
    }
}
