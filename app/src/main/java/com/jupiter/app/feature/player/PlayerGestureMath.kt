package com.jupiter.app.feature.player

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class GestureAxis {
    UNDECIDED,
    VERTICAL_LEFT,
    VERTICAL_RIGHT,
    HORIZONTAL,
}

object PlayerGestureMath {
    fun isInEdgeGuard(
        xPx: Float,
        yPx: Float,
        widthPx: Float,
        heightPx: Float,
        marginPx: Float,
    ): Boolean {
        if (widthPx <= 0f || heightPx <= 0f) return true
        if (marginPx <= 0f) return false
        return xPx < marginPx ||
            xPx > widthPx - marginPx ||
            yPx < marginPx ||
            yPx > heightPx - marginPx
    }

    fun classifyDoubleTapSide(xPx: Float, widthPx: Float): DoubleTapSide? {
        if (widthPx <= 0f) return null
        val fraction = xPx / widthPx
        return when {
            fraction < (1f / 3f) -> DoubleTapSide.LEFT
            fraction > (2f / 3f) -> DoubleTapSide.RIGHT
            else -> null
        }
    }

    fun classifyDragAxis(
        startXPx: Float,
        widthPx: Float,
        totalDxPx: Float,
        totalDyPx: Float,
        touchSlopPx: Float,
    ): GestureAxis {
        val absDx = abs(totalDxPx)
        val absDy = abs(totalDyPx)
        if (absDx < touchSlopPx && absDy < touchSlopPx) {
            return GestureAxis.UNDECIDED
        }
        return if (absDx >= absDy) {
            GestureAxis.HORIZONTAL
        } else if (widthPx > 0f && startXPx < widthPx * 0.5f) {
            GestureAxis.VERTICAL_LEFT
        } else {
            GestureAxis.VERTICAL_RIGHT
        }
    }

    fun computeVerticalDeltaFraction(
        dyPx: Float,
        heightPx: Float,
        sensitivity: Float = 1.25f,
    ): Float {
        if (heightPx <= 0f) return 0f
        return (-dyPx / heightPx) * sensitivity
    }

    fun computeHorizontalSeekDeltaMs(
        dxPx: Float,
        widthPx: Float,
        fullWidthSeekMs: Long = 90_000L,
    ): Long {
        if (widthPx <= 0f) return 0L
        return ((dxPx / widthPx) * fullWidthSeekMs).roundToLong()
    }

    fun clampBrightness(value: Float): Float =
        if (!value.isFinite()) 0.5f else value.coerceIn(0.01f, 1.0f)

    fun clampZoom(value: Double): Double =
        if (!value.isFinite()) 0.0 else value.coerceIn(-0.5, 2.0)

    fun formatZoomLabel(zoom: Double): String {
        if (!zoom.isFinite()) return "100%"
        val percent = (2.0.pow(zoom) * 100.0).roundToInt()
        return "$percent%"
    }
}

fun isInEdgeGuard(
    xPx: Float,
    yPx: Float,
    widthPx: Float,
    heightPx: Float,
    marginPx: Float,
): Boolean = PlayerGestureMath.isInEdgeGuard(xPx, yPx, widthPx, heightPx, marginPx)

fun classifyDoubleTapSide(xPx: Float, widthPx: Float): DoubleTapSide? =
    PlayerGestureMath.classifyDoubleTapSide(xPx, widthPx)

fun classifyDragAxis(
    startXPx: Float,
    widthPx: Float,
    totalDxPx: Float,
    totalDyPx: Float,
    touchSlopPx: Float,
): GestureAxis = PlayerGestureMath.classifyDragAxis(startXPx, widthPx, totalDxPx, totalDyPx, touchSlopPx)

fun computeVerticalDeltaFraction(
    dyPx: Float,
    heightPx: Float,
    sensitivity: Float = 1.25f,
): Float = PlayerGestureMath.computeVerticalDeltaFraction(dyPx, heightPx, sensitivity)

fun computeHorizontalSeekDeltaMs(
    dxPx: Float,
    widthPx: Float,
    fullWidthSeekMs: Long = 90_000L,
): Long = PlayerGestureMath.computeHorizontalSeekDeltaMs(dxPx, widthPx, fullWidthSeekMs)

fun clampBrightness(value: Float): Float = PlayerGestureMath.clampBrightness(value)

fun clampZoom(value: Double): Double = PlayerGestureMath.clampZoom(value)

fun formatZoomLabel(zoom: Double): String = PlayerGestureMath.formatZoomLabel(zoom)
