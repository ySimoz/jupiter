package com.jupiter.app.core.shell

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

internal enum class NavBubbleAxis {
    HORIZONTAL,
    VERTICAL,
}

internal data class MorphingBubbleFrame(
    val rect: Rect,
    val motionEnergy: Float,
    val travelDirection: Float,
)

internal data class EdgeStiffness(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal const val NAV_BUBBLE_LEADING_STIFFNESS = 560f
internal const val NAV_BUBBLE_TRAILING_STIFFNESS = 230f
internal const val NAV_BUBBLE_CROSS_AXIS_STIFFNESS = 420f
internal const val NAV_BUBBLE_MAX_SQUASH = 0.07f

internal fun selectEdgeStiffness(
    from: Rect,
    to: Rect,
    axis: NavBubbleAxis,
): EdgeStiffness = when (axis) {
    NavBubbleAxis.HORIZONTAL -> {
        if (to.center.x >= from.center.x) {
            EdgeStiffness(
                left = NAV_BUBBLE_TRAILING_STIFFNESS,
                top = NAV_BUBBLE_CROSS_AXIS_STIFFNESS,
                right = NAV_BUBBLE_LEADING_STIFFNESS,
                bottom = NAV_BUBBLE_CROSS_AXIS_STIFFNESS,
            )
        } else {
            EdgeStiffness(
                left = NAV_BUBBLE_LEADING_STIFFNESS,
                top = NAV_BUBBLE_CROSS_AXIS_STIFFNESS,
                right = NAV_BUBBLE_TRAILING_STIFFNESS,
                bottom = NAV_BUBBLE_CROSS_AXIS_STIFFNESS,
            )
        }
    }

    NavBubbleAxis.VERTICAL -> {
        if (to.center.y >= from.center.y) {
            EdgeStiffness(
                left = NAV_BUBBLE_CROSS_AXIS_STIFFNESS,
                top = NAV_BUBBLE_TRAILING_STIFFNESS,
                right = NAV_BUBBLE_CROSS_AXIS_STIFFNESS,
                bottom = NAV_BUBBLE_LEADING_STIFFNESS,
            )
        } else {
            EdgeStiffness(
                left = NAV_BUBBLE_CROSS_AXIS_STIFFNESS,
                top = NAV_BUBBLE_LEADING_STIFFNESS,
                right = NAV_BUBBLE_CROSS_AXIS_STIFFNESS,
                bottom = NAV_BUBBLE_TRAILING_STIFFNESS,
            )
        }
    }
}

internal fun computeMorphingBubbleFrame(
    rawRect: Rect,
    restPrimarySize: Float,
    axis: NavBubbleAxis,
    velocityPxPerSec: Float,
    density: Float,
): MorphingBubbleFrame {
    val safeRect = Rect(
        left = minOf(rawRect.left, rawRect.right),
        top = minOf(rawRect.top, rawRect.bottom),
        right = maxOf(rawRect.left, rawRect.right),
        bottom = maxOf(rawRect.top, rawRect.bottom),
    )
    if (safeRect.width <= 0f || safeRect.height <= 0f) {
        return MorphingBubbleFrame(
            rect = safeRect,
            motionEnergy = 0f,
            travelDirection = 0f,
        )
    }

    val safeDensity = density.coerceAtLeast(1f)
    val currentPrimary = when (axis) {
        NavBubbleAxis.HORIZONTAL -> safeRect.width
        NavBubbleAxis.VERTICAL -> safeRect.height
    }
    val stretchRatio = if (restPrimarySize > 0f) {
        ((currentPrimary - restPrimarySize) / restPrimarySize).coerceAtLeast(0f)
    } else {
        0f
    }
    val speedDpPerSec = abs(velocityPxPerSec) / safeDensity
    val speedFactor = (speedDpPerSec / 650f).coerceIn(0f, 1f)
    val motionEnergy = maxOf(
        (stretchRatio / 0.45f).coerceIn(0f, 1f),
        speedFactor * 0.85f,
    ).coerceIn(0f, 1f)
    val travelDirection = (velocityPxPerSec / (safeDensity * 350f)).coerceIn(-1f, 1f)
    val squash = (motionEnergy * NAV_BUBBLE_MAX_SQUASH).coerceIn(0f, NAV_BUBBLE_MAX_SQUASH)

    val squashedRect = when (axis) {
        NavBubbleAxis.HORIZONTAL -> {
            val insetY = (safeRect.height * squash) / 2f
            Rect(
                left = safeRect.left,
                top = safeRect.top + insetY,
                right = safeRect.right,
                bottom = safeRect.bottom - insetY,
            )
        }

        NavBubbleAxis.VERTICAL -> {
            val insetX = (safeRect.width * squash) / 2f
            Rect(
                left = safeRect.left + insetX,
                top = safeRect.top,
                right = safeRect.right - insetX,
                bottom = safeRect.bottom,
            )
        }
    }

    return MorphingBubbleFrame(
        rect = squashedRect,
        motionEnergy = motionEnergy,
        travelDirection = travelDirection,
    )
}

internal fun computeTabOverlapFraction(
    bubbleRect: Rect?,
    itemRect: Rect?,
): Float {
    if (bubbleRect == null || itemRect == null) return 0f
    if (itemRect.width <= 0f || itemRect.height <= 0f) return 0f
    val overlapWidth = (minOf(bubbleRect.right, itemRect.right) - maxOf(bubbleRect.left, itemRect.left))
        .coerceAtLeast(0f)
    val overlapHeight = (minOf(bubbleRect.bottom, itemRect.bottom) - maxOf(bubbleRect.top, itemRect.top))
        .coerceAtLeast(0f)
    if (overlapWidth <= 0f || overlapHeight <= 0f) return 0f
    val itemArea = itemRect.width * itemRect.height
    return ((overlapWidth * overlapHeight) / itemArea).coerceIn(0f, 1f)
}
