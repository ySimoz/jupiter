package com.jupiter.app.core.shell

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavBubbleMathTest {

    @Test
    fun selectEdgeStiffness_assignsLeadingToAdvancingEdge_forAllDirections() {
        val origin = Rect(100f, 100f, 200f, 150f)

        val right = selectEdgeStiffness(origin, Rect(240f, 100f, 340f, 150f), NavBubbleAxis.HORIZONTAL)
        assertEquals(NAV_BUBBLE_TRAILING_STIFFNESS, right.left, 0f)
        assertEquals(NAV_BUBBLE_LEADING_STIFFNESS, right.right, 0f)
        assertEquals(NAV_BUBBLE_CROSS_AXIS_STIFFNESS, right.top, 0f)
        assertEquals(NAV_BUBBLE_CROSS_AXIS_STIFFNESS, right.bottom, 0f)

        val left = selectEdgeStiffness(origin, Rect(0f, 100f, 80f, 150f), NavBubbleAxis.HORIZONTAL)
        assertEquals(NAV_BUBBLE_LEADING_STIFFNESS, left.left, 0f)
        assertEquals(NAV_BUBBLE_TRAILING_STIFFNESS, left.right, 0f)

        val down = selectEdgeStiffness(origin, Rect(100f, 200f, 200f, 250f), NavBubbleAxis.VERTICAL)
        assertEquals(NAV_BUBBLE_TRAILING_STIFFNESS, down.top, 0f)
        assertEquals(NAV_BUBBLE_LEADING_STIFFNESS, down.bottom, 0f)
        assertEquals(NAV_BUBBLE_CROSS_AXIS_STIFFNESS, down.left, 0f)
        assertEquals(NAV_BUBBLE_CROSS_AXIS_STIFFNESS, down.right, 0f)

        val up = selectEdgeStiffness(origin, Rect(100f, 20f, 200f, 70f), NavBubbleAxis.VERTICAL)
        assertEquals(NAV_BUBBLE_LEADING_STIFFNESS, up.top, 0f)
        assertEquals(NAV_BUBBLE_TRAILING_STIFFNESS, up.bottom, 0f)
    }

    @Test
    fun computeMorphingBubbleFrame_atRest_hasZeroEnergyAndUnchangedRect() {
        val raw = Rect(20f, 10f, 120f, 62f)
        val frame = computeMorphingBubbleFrame(
            rawRect = raw,
            restPrimarySize = raw.width,
            axis = NavBubbleAxis.HORIZONTAL,
            velocityPxPerSec = 0f,
            density = 3f,
        )

        assertEquals(0f, frame.motionEnergy, 0.0001f)
        assertEquals(0f, frame.travelDirection, 0.0001f)
        assertEquals(raw, frame.rect)
    }

    @Test
    fun computeMorphingBubbleFrame_horizontalStretch_appliesSymmetricVerticalSquash() {
        val raw = Rect(20f, 10f, 180f, 110f)
        val frame = computeMorphingBubbleFrame(
            rawRect = raw,
            restPrimarySize = 100f,
            axis = NavBubbleAxis.HORIZONTAL,
            velocityPxPerSec = 500f,
            density = 2f,
        )

        assertTrue(frame.motionEnergy > 0f)
        assertTrue(frame.travelDirection > 0f)
        assertEquals(raw.left, frame.rect.left, 0.001f)
        assertEquals(raw.right, frame.rect.right, 0.001f)
        val topInset = frame.rect.top - raw.top
        val bottomInset = raw.bottom - frame.rect.bottom
        assertEquals(topInset, bottomInset, 0.001f)
        assertTrue(topInset > 0f)
        assertTrue((raw.height - frame.rect.height) / raw.height <= NAV_BUBBLE_MAX_SQUASH + 0.0001f)
    }

    @Test
    fun computeMorphingBubbleFrame_verticalStretch_appliesSymmetricHorizontalSquash() {
        val raw = Rect(10f, 20f, 110f, 180f)
        val frame = computeMorphingBubbleFrame(
            rawRect = raw,
            restPrimarySize = 100f,
            axis = NavBubbleAxis.VERTICAL,
            velocityPxPerSec = -400f,
            density = 2f,
        )

        assertTrue(frame.motionEnergy > 0f)
        assertTrue(frame.travelDirection < 0f)
        assertEquals(raw.top, frame.rect.top, 0.001f)
        assertEquals(raw.bottom, frame.rect.bottom, 0.001f)
        val leftInset = frame.rect.left - raw.left
        val rightInset = raw.right - frame.rect.right
        assertEquals(leftInset, rightInset, 0.001f)
        assertTrue(leftInset > 0f)
        assertTrue((raw.width - frame.rect.width) / raw.width <= NAV_BUBBLE_MAX_SQUASH + 0.0001f)
    }

    @Test
    fun computeTabOverlapFraction_handlesFullHalfDisjointAndInvalidRects() {
        val item = Rect(100f, 0f, 200f, 50f)

        assertEquals(1f, computeTabOverlapFraction(item, item), 0.0001f)
        assertEquals(0.5f, computeTabOverlapFraction(Rect(50f, 0f, 150f, 50f), item), 0.0001f)
        assertEquals(0f, computeTabOverlapFraction(Rect(0f, 0f, 90f, 50f), item), 0.0001f)
        assertEquals(0f, computeTabOverlapFraction(null, item), 0.0001f)
        assertEquals(0f, computeTabOverlapFraction(item, Rect.Zero), 0.0001f)
    }
}
