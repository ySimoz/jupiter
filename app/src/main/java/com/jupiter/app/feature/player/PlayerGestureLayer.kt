package com.jupiter.app.feature.player

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.BrightnessLow
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jupiter.app.core.designsystem.JupiterColors
import com.jupiter.app.core.designsystem.JupiterSpecularBorderBrush
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

private const val LONG_PRESS_THRESHOLD_MS = 400L
private const val METER_OVERLAY_LINGER_MS = 450L
private val PlayerGlassBackdrop = Color(0xFF12161E).copy(alpha = 0.78f)

@Composable
fun PlayerGestureLayer(
    isLocked: Boolean,
    gestureOverlay: GestureOverlayState,
    currentPositionMs: () -> Long,
    durationMs: Long,
    currentZoom: Double,
    onSingleTap: () -> Unit,
    onLockedTap: () -> Unit,
    onDoubleTap: (DoubleTapSide) -> Unit,
    onBrightnessGesture: (Float) -> Unit,
    onVolumeGesture: (Float, Int) -> Unit,
    onHorizontalDragUpdate: (targetMs: Long, deltaMs: Long) -> Unit,
    onHorizontalDragEnd: (targetMs: Long) -> Unit,
    onHorizontalDragCancel: () -> Unit,
    onLongPressSpeedStart: () -> Unit,
    onLongPressSpeedEnd: () -> Unit,
    onPinchZoom: (Double) -> Unit,
    onClearGestureOverlay: () -> Unit,
    modifier: Modifier = Modifier,
    brightnessGestureEnabled: Boolean = true,
    volumeGestureEnabled: Boolean = true,
    seekGestureEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val scope = rememberCoroutineScope()

    val currentIsLocked by rememberUpdatedState(isLocked)
    val currentOverlay by rememberUpdatedState(gestureOverlay)
    val currentPosProvider by rememberUpdatedState(currentPositionMs)
    val currentDurationMs by rememberUpdatedState(durationMs)
    val currentZoomValue by rememberUpdatedState(currentZoom)
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnLockedTap by rememberUpdatedState(onLockedTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnBrightness by rememberUpdatedState(onBrightnessGesture)
    val currentOnVolume by rememberUpdatedState(onVolumeGesture)
    val currentOnHDragUpdate by rememberUpdatedState(onHorizontalDragUpdate)
    val currentOnHDragEnd by rememberUpdatedState(onHorizontalDragEnd)
    val currentOnHDragCancel by rememberUpdatedState(onHorizontalDragCancel)
    val currentOnLongPressStart by rememberUpdatedState(onLongPressSpeedStart)
    val currentOnLongPressEnd by rememberUpdatedState(onLongPressSpeedEnd)
    val currentOnPinchZoom by rememberUpdatedState(onPinchZoom)
    val currentOnClearOverlay by rememberUpdatedState(onClearGestureOverlay)
    val currentBrightnessEnabled by rememberUpdatedState(brightnessGestureEnabled)
    val currentVolumeEnabled by rememberUpdatedState(volumeGestureEnabled)
    val currentSeekEnabled by rememberUpdatedState(seekGestureEnabled)

    val overlayClearHolder = remember { object { var job: Job? = null } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val edgeMarginPx = with(density) { 24.dp.toPx() }
                val doubleTapSlopPx = with(density) { 48.dp.toPx() }
                val touchSlop = viewConfiguration.touchSlop

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val widthPx = size.width.toFloat()
                    val heightPx = size.height.toFloat()
                    if (widthPx <= 0f || heightPx <= 0f) return@awaitEachGesture

                    if (
                        PlayerGestureMath.isInEdgeGuard(
                            xPx = down.position.x,
                            yPx = down.position.y,
                            widthPx = widthPx,
                            heightPx = heightPx,
                            marginPx = edgeMarginPx,
                        )
                    ) {
                        return@awaitEachGesture
                    }

                    if (currentIsLocked) {
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val downChange = event.changes.firstOrNull { it.id == down.id }
                            if (downChange != null && (downChange.position - down.position).getDistance() > touchSlop) {
                                moved = true
                            }
                            if (event.changes.none { it.pressed }) {
                                if (!moved && event.changes.size == 1) {
                                    currentOnLockedTap()
                                }
                                break
                            }
                        }
                        return@awaitEachGesture
                    }

                    var activeAxis = GestureAxis.UNDECIDED
                    var isPinching = false
                    var isLongPressActive = false
                    var movedBeyondTouchSlop = false
                    var horizontalCommitted = false
                    var releasedCleanlyAsTap = false

                    var dragLockX = down.position.x
                    var dragStartPosMs = 0L
                    var lastSeekTargetMs = 0L

                    var brightnessValue = readInitialBrightness(activity, context)
                    val maxMusicVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    var volumeFraction = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxMusicVolume
                    var lastAppliedVolume = -1

                    var zoomValue = currentZoomValue
                    var prevPinchSpan = 0f
                    var prevSinglePos = down.position
                    val downUptimeMs = down.uptimeMillis

                    try {
                        while (true) {
                            val canTriggerLongPress = !isLongPressActive &&
                                !isPinching &&
                                !movedBeyondTouchSlop &&
                                activeAxis == GestureAxis.UNDECIDED

                            val event = if (canTriggerLongPress) {
                                val elapsed = (SystemClock.uptimeMillis() - downUptimeMs).coerceAtLeast(0L)
                                val remaining = (LONG_PRESS_THRESHOLD_MS - elapsed).coerceAtLeast(1L)
                                val timedEvent = withTimeoutOrNull(remaining) {
                                    awaitPointerEvent()
                                }
                                if (timedEvent == null) {
                                    isLongPressActive = true
                                    currentOnLongPressStart()
                                    continue
                                }
                                timedEvent
                            } else {
                                awaitPointerEvent()
                            }

                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                if (isLongPressActive) {
                                    isLongPressActive = false
                                    currentOnLongPressEnd()
                                } else if (activeAxis == GestureAxis.HORIZONTAL) {
                                    horizontalCommitted = true
                                    currentOnHDragEnd(lastSeekTargetMs)
                                } else if (
                                    activeAxis == GestureAxis.VERTICAL_LEFT ||
                                    activeAxis == GestureAxis.VERTICAL_RIGHT
                                ) {
                                    overlayClearHolder.job = scope.launch {
                                        delay(METER_OVERLAY_LINGER_MS)
                                        currentOnClearOverlay()
                                    }
                                } else if (!isPinching && !movedBeyondTouchSlop) {
                                    releasedCleanlyAsTap = true
                                }
                                break
                            }

                            if (pressed.size >= 2) {
                                if (isLongPressActive) {
                                    isLongPressActive = false
                                    currentOnLongPressEnd()
                                }
                                if (activeAxis == GestureAxis.HORIZONTAL) {
                                    currentOnHDragCancel()
                                    activeAxis = GestureAxis.UNDECIDED
                                } else if (
                                    activeAxis == GestureAxis.VERTICAL_LEFT ||
                                    activeAxis == GestureAxis.VERTICAL_RIGHT
                                ) {
                                    currentOnClearOverlay()
                                    activeAxis = GestureAxis.UNDECIDED
                                }

                                isPinching = true
                                val span = (pressed[0].position - pressed[1].position).getDistance()
                                if (prevPinchSpan > 0f && span > 0f) {
                                    val ratio = span / prevPinchSpan
                                    val deltaZoom = log2(ratio.toDouble())
                                    if (abs(deltaZoom) > 0.001) {
                                        zoomValue = PlayerGestureMath.clampZoom(zoomValue + deltaZoom)
                                        currentOnPinchZoom(zoomValue)
                                    }
                                }
                                prevPinchSpan = span
                                pressed.forEach { it.consume() }
                                continue
                            }

                            if (isPinching) {
                                prevPinchSpan = 0f
                                pressed[0].consume()
                                continue
                            }

                            if (isLongPressActive) {
                                pressed[0].consume()
                                continue
                            }

                            val current = pressed[0]
                            if (activeAxis == GestureAxis.UNDECIDED) {
                                val totalDx = current.position.x - down.position.x
                                val totalDy = current.position.y - down.position.y
                                val classified = PlayerGestureMath.classifyDragAxis(
                                    startXPx = down.position.x,
                                    widthPx = widthPx,
                                    totalDxPx = totalDx,
                                    totalDyPx = totalDy,
                                    touchSlopPx = touchSlop,
                                )
                                if (classified != GestureAxis.UNDECIDED) {
                                    val axisEnabled = when (classified) {
                                        GestureAxis.VERTICAL_LEFT -> currentBrightnessEnabled
                                        GestureAxis.VERTICAL_RIGHT -> currentVolumeEnabled
                                        GestureAxis.HORIZONTAL -> currentSeekEnabled
                                        GestureAxis.UNDECIDED -> false
                                    }
                                    if (!axisEnabled) {
                                        movedBeyondTouchSlop = true
                                        prevSinglePos = current.position
                                        continue
                                    }
                                    activeAxis = classified
                                    dragLockX = current.position.x
                                    prevSinglePos = current.position
                                    if (
                                        classified == GestureAxis.VERTICAL_LEFT ||
                                        classified == GestureAxis.VERTICAL_RIGHT
                                    ) {
                                        overlayClearHolder.job?.cancel()
                                    } else if (classified == GestureAxis.HORIZONTAL) {
                                        overlayClearHolder.job?.cancel()
                                        dragStartPosMs = currentPosProvider()
                                        lastSeekTargetMs = dragStartPosMs
                                    }
                                    current.consume()
                                }
                            } else {
                                val dy = current.position.y - prevSinglePos.y
                                prevSinglePos = current.position

                                when (activeAxis) {
                                    GestureAxis.VERTICAL_LEFT -> {
                                        val delta = PlayerGestureMath.computeVerticalDeltaFraction(dy, heightPx)
                                        brightnessValue = PlayerGestureMath.clampBrightness(brightnessValue + delta)
                                        applyWindowBrightness(activity, brightnessValue)
                                        currentOnBrightness(brightnessValue)
                                    }

                                    GestureAxis.VERTICAL_RIGHT -> {
                                        val delta = PlayerGestureMath.computeVerticalDeltaFraction(dy, heightPx)
                                        volumeFraction = (volumeFraction + delta).coerceIn(0f, 1f)
                                        val targetIdx = (volumeFraction * maxMusicVolume).roundToInt().coerceIn(0, maxMusicVolume)
                                        if (targetIdx != lastAppliedVolume) {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIdx, 0)
                                            lastAppliedVolume = targetIdx
                                        }
                                        val percent = (volumeFraction * 100f).roundToInt().coerceIn(0, 100)
                                        currentOnVolume(volumeFraction, percent)
                                    }

                                    GestureAxis.HORIZONTAL -> {
                                        val rawDeltaMs = PlayerGestureMath.computeHorizontalSeekDeltaMs(
                                            dxPx = current.position.x - dragLockX,
                                            widthPx = widthPx,
                                        )
                                        val maxMs = if (currentDurationMs > 0L) currentDurationMs else Long.MAX_VALUE
                                        val targetMs = (dragStartPosMs + rawDeltaMs).coerceIn(0L, maxMs)
                                        lastSeekTargetMs = targetMs
                                        currentOnHDragUpdate(targetMs, targetMs - dragStartPosMs)
                                    }

                                    GestureAxis.UNDECIDED -> Unit
                                }
                                current.consume()
                            }
                        }
                    } finally {
                        if (isLongPressActive) {
                            currentOnLongPressEnd()
                        }
                        if (activeAxis == GestureAxis.HORIZONTAL && !horizontalCommitted) {
                            currentOnHDragCancel()
                        }
                    }

                    if (!releasedCleanlyAsTap) return@awaitEachGesture

                    if (
                        currentOverlay is GestureOverlayState.Brightness ||
                        currentOverlay is GestureOverlayState.Volume
                    ) {
                        overlayClearHolder.job?.cancel()
                        currentOnClearOverlay()
                    }

                    val tapSide = PlayerGestureMath.classifyDoubleTapSide(down.position.x, widthPx)
                    val activeDoubleTap = currentOverlay as? GestureOverlayState.DoubleTapSeek
                    if (tapSide != null && activeDoubleTap != null && activeDoubleTap.side == tapSide) {
                        currentOnDoubleTap(tapSide)
                        return@awaitEachGesture
                    }

                    if (tapSide == null) {
                        currentOnSingleTap()
                        return@awaitEachGesture
                    }

                    val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                        awaitFirstDown(requireUnconsumed = false)
                    }
                    if (secondDown == null) {
                        currentOnSingleTap()
                        return@awaitEachGesture
                    }

                    var secondMoved = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val pressedChanges = ev.changes.filter { it.pressed }
                        if (pressedChanges.any { (it.position - secondDown.position).getDistance() > touchSlop }) {
                            secondMoved = true
                        }
                        if (pressedChanges.isEmpty()) break
                    }

                    val isNearFirstTap = (secondDown.position - down.position).getDistance() <= doubleTapSlopPx
                    if (!secondMoved && isNearFirstTap) {
                        currentOnDoubleTap(tapSide)
                    } else {
                        currentOnSingleTap()
                    }
                }
            },
    ) {
        GestureIndicatorOverlays(gestureOverlay = gestureOverlay)
    }
}

@Composable
private fun BoxScope.GestureIndicatorOverlays(gestureOverlay: GestureOverlayState) {
    AnimatedVisibility(
        visible = gestureOverlay is GestureOverlayState.Brightness,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(180)),
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = 28.dp),
    ) {
        val state = gestureOverlay as? GestureOverlayState.Brightness
        if (state != null) {
            val icon = when {
                state.fraction < 0.33f -> Icons.Outlined.BrightnessLow
                state.fraction < 0.67f -> Icons.Outlined.BrightnessMedium
                else -> Icons.Outlined.BrightnessHigh
            }
            VerticalGlassMeterPill(
                icon = icon,
                fraction = state.fraction,
                label = "${(state.fraction * 100f).roundToInt().coerceIn(1, 100)}%",
            )
        }
    }

    AnimatedVisibility(
        visible = gestureOverlay is GestureOverlayState.Volume,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(180)),
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 28.dp),
    ) {
        val state = gestureOverlay as? GestureOverlayState.Volume
        if (state != null) {
            val icon = when {
                state.percent <= 0 -> Icons.AutoMirrored.Outlined.VolumeOff
                state.percent < 50 -> Icons.AutoMirrored.Outlined.VolumeDown
                else -> Icons.AutoMirrored.Outlined.VolumeUp
            }
            VerticalGlassMeterPill(
                icon = icon,
                fraction = state.fraction,
                label = "${state.percent}%",
            )
        }
    }

    AnimatedVisibility(
        visible = gestureOverlay is GestureOverlayState.HorizontalSeek,
        enter = fadeIn(tween(100)),
        exit = fadeOut(tween(160)),
        modifier = Modifier.align(Alignment.Center),
    ) {
        val state = gestureOverlay as? GestureOverlayState.HorizontalSeek
        if (state != null) {
            HorizontalSeekPreviewPill(state = state)
        }
    }

    AnimatedVisibility(
        visible = gestureOverlay is GestureOverlayState.DoubleTapSeek,
        enter = fadeIn(tween(100)),
        exit = fadeOut(tween(200)),
        modifier = Modifier
            .align(
                if ((gestureOverlay as? GestureOverlayState.DoubleTapSeek)?.side == DoubleTapSide.LEFT) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            )
            .padding(horizontal = 32.dp),
    ) {
        val state = gestureOverlay as? GestureOverlayState.DoubleTapSeek
        if (state != null) {
            DoubleTapSeekPill(state = state)
        }
    }

    AnimatedVisibility(
        visible = gestureOverlay is GestureOverlayState.SpeedBoost,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(160)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .systemBarsPadding()
            .padding(top = 16.dp),
    ) {
        SpeedBoostGlassPill()
    }

    AnimatedVisibility(
        visible = gestureOverlay is GestureOverlayState.Zoom,
        enter = fadeIn(tween(100)),
        exit = fadeOut(tween(180)),
        modifier = Modifier.align(Alignment.Center),
    ) {
        val state = gestureOverlay as? GestureOverlayState.Zoom
        if (state != null) {
            ZoomRatioGlassPill(label = state.label)
        }
    }
}

@Composable
private fun PlayerOverlayGlassBox(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(PlayerGlassBackdrop, shape)
            .border(width = 1.dp, brush = JupiterSpecularBorderBrush, shape = shape),
        content = content,
    )
}

@Composable
private fun VerticalGlassMeterPill(
    icon: ImageVector,
    fraction: Float,
    label: String,
) {
    PlayerOverlayGlassBox(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.width(56.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = JupiterColors.TextPrimary,
                modifier = Modifier.size(18.dp),
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(112.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction.coerceIn(0f, 1f))
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
                color = JupiterColors.TextPrimary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HorizontalSeekPreviewPill(state: GestureOverlayState.HorizontalSeek) {
    val accent = MaterialTheme.colorScheme.primary
    PlayerOverlayGlassBox(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = TimeFormatters.formatDuration(state.targetMs),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Medium,
                    color = JupiterColors.TextPrimary,
                )
                Text(
                    text = "(${TimeFormatters.formatSignedDelta(state.deltaMs)})",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Normal,
                    color = accent,
                )
            }
            if (state.durationMs > 0L) {
                val progress = (state.targetMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .width(128.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .clip(CircleShape)
                            .background(accent),
                    )
                }
            }
        }
    }
}

@Composable
private fun DoubleTapSeekPill(state: GestureOverlayState.DoubleTapSeek) {
    val isLeft = state.side == DoubleTapSide.LEFT
    val label = if (isLeft) "-${state.accumulatedSeconds}s" else "+${state.accumulatedSeconds}s"
    val icon = if (isLeft) Icons.Outlined.FastRewind else Icons.Outlined.FastForward

    PlayerOverlayGlassBox(shape = CircleShape) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLeft) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = JupiterColors.TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
                color = JupiterColors.TextPrimary,
            )
            if (!isLeft) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = JupiterColors.TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SpeedBoostGlassPill() {
    PlayerOverlayGlassBox(shape = CircleShape) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "2x speed",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
                color = JupiterColors.TextPrimary,
            )
            Icon(
                imageVector = Icons.Outlined.FastForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ZoomRatioGlassPill(label: String) {
    PlayerOverlayGlassBox(shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ZoomIn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
                color = JupiterColors.TextPrimary,
            )
        }
    }
}

private fun readInitialBrightness(activity: android.app.Activity?, context: Context): Float {
    val windowBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
    if (windowBrightness >= 0f) {
        return PlayerGestureMath.clampBrightness(windowBrightness)
    }
    val systemBrightness = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
    }.getOrDefault(0.5f)
    return PlayerGestureMath.clampBrightness(systemBrightness)
}

private fun applyWindowBrightness(activity: android.app.Activity?, brightness: Float) {
    val window = activity?.window ?: return
    val params: WindowManager.LayoutParams = window.attributes
    params.screenBrightness = PlayerGestureMath.clampBrightness(brightness)
    window.attributes = params
}
