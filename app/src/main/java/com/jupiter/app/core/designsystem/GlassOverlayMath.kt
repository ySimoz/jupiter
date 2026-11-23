package com.jupiter.app.core.designsystem

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

internal fun computeDropdownMenuOffset(
    anchorBounds: Rect,
    menuSize: IntSize,
    containerSize: IntSize,
    marginPx: Int,
    verticalGapPx: Int,
): IntOffset {
    val safeMargin = marginPx.coerceAtLeast(0)
    val maxX = (containerSize.width - menuSize.width - safeMargin).coerceAtLeast(safeMargin)
    val preferredX = if (anchorBounds.center.x > containerSize.width / 2f) {
        (anchorBounds.right - menuSize.width).roundToInt()
    } else {
        anchorBounds.left.roundToInt()
    }
    val x = preferredX.coerceIn(safeMargin, maxX)

    val maxY = (containerSize.height - menuSize.height - safeMargin).coerceAtLeast(safeMargin)
    val belowY = anchorBounds.bottom.roundToInt() + verticalGapPx
    val aboveY = anchorBounds.top.roundToInt() - verticalGapPx - menuSize.height
    val y = when {
        belowY + menuSize.height + safeMargin <= containerSize.height -> belowY
        aboveY >= safeMargin -> aboveY
        else -> belowY.coerceIn(safeMargin, maxY)
    }

    return IntOffset(x = x, y = y)
}
