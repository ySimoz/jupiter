package com.jupiter.app.feature.player

import com.jupiter.app.data.local.VideoBookmarkEntity
import com.jupiter.engine.AudioTrack
import com.jupiter.engine.Chapter
import com.jupiter.engine.HwDecMode
import com.jupiter.engine.SubtitleTrack
import java.util.Locale
import kotlin.math.abs

enum class AspectMode(
    val label: String,
    val keepAspect: Boolean,
    val mpvAspect: String,
    val mpvPanscan: Double,
) {
    FIT("Fit", true, "-1", 0.0),
    FILL("Fill", true, "-1", 1.0),
    STRETCH("Stretch", false, "-1", 0.0),
    RATIO_16_9("16:9", true, "16:9", 0.0),
    RATIO_4_3("4:3", true, "4:3", 0.0),
    RATIO_21_9("21:9", true, "21:9", 0.0),
}

enum class OrientationLock(val label: String) {
    AUTO("Auto"),
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait"),
}

enum class DoubleTapSide {
    LEFT,
    RIGHT,
}

sealed interface GestureOverlayState {
    data object None : GestureOverlayState
    data class Brightness(val fraction: Float) : GestureOverlayState
    data class Volume(val fraction: Float, val percent: Int) : GestureOverlayState
    data class HorizontalSeek(
        val targetMs: Long,
        val deltaMs: Long,
        val durationMs: Long,
    ) : GestureOverlayState
    data class DoubleTapSeek(
        val side: DoubleTapSide,
        val accumulatedSeconds: Int,
    ) : GestureOverlayState
    data object SpeedBoost : GestureOverlayState
    data class Zoom(
        val zoomValue: Double,
        val label: String,
    ) : GestureOverlayState
}

data class VideoEqualizerState(
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val gamma: Int = 0,
) {
    val isDefault: Boolean
        get() = brightness == 0 && contrast == 0 && saturation == 0 && gamma == 0
}

data class AbLoopState(
    val pointAMs: Long? = null,
    val pointBMs: Long? = null,
) {
    val isActive: Boolean
        get() = pointAMs != null
}

enum class ConnectionAlert(val message: String) {
    OFFLINE("No internet connection"),
    RECONNECTING("Reconnecting stream"),
    SLOW_CONNECTION("Slow connection — buffering"),
}

data class PlayerUiState(
    val title: String = "",
    val isHudVisible: Boolean = true,
    val isLocked: Boolean = false,
    val showUnlockHint: Boolean = false,
    val isPaused: Boolean = false,
    val isBuffering: Boolean = false,
    val bufferingPercent: Int = 0,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val isSpeedBoosted: Boolean = false,
    val showRemainingTime: Boolean = false,
    val aspectMode: AspectMode = AspectMode.FIT,
    val orientationLock: OrientationLock = OrientationLock.AUTO,
    val chapters: List<Chapter> = emptyList(),
    val videoZoom: Double = 0.0,
    val gestureOverlay: GestureOverlayState = GestureOverlayState.None,
    val isScrubbing: Boolean = false,
    val scrubPreviewMs: Long? = null,
    val errorMessage: String? = null,
    val audioTracks: List<AudioTrack> = emptyList(),
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val selectedAudioTrackId: Int? = null,
    val selectedSubtitleTrackId: Int? = null,
    val secondarySubtitleTrackId: Int? = null,
    val subDelaySec: Double = 0.0,
    val subScale: Double = 1.0,
    val audioDelaySec: Double = 0.0,
    val hwDecMode: HwDecMode = HwDecMode.AUTO,
    val currentChapter: Int = -1,
    val abLoop: AbLoopState = AbLoopState(),
    val equalizer: VideoEqualizerState = VideoEqualizerState(),
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val backgroundPlayEnabled: Boolean = true,
    val autoEnterPip: Boolean = true,
    val doubleTapSeekSec: Int = 10,
    val gestureBrightnessEnabled: Boolean = true,
    val gestureVolumeEnabled: Boolean = true,
    val gestureSeekEnabled: Boolean = true,
    val bookmarks: List<VideoBookmarkEntity> = emptyList(),
    val queueItems: List<QueueMediaItem> = emptyList(),
    val queueIndex: Int = -1,
    val queueSourceTitle: String = "",
    val autoPlayNext: Boolean = true,
    val upNextItem: QueueMediaItem? = null,
    val upNextCountdownSec: Int = 0,
    val isCapturingFrame: Boolean = false,
    val statusMessage: String? = null,
    val connectionAlert: ConnectionAlert? = null,
) {
    val hasQueue: Boolean
        get() = queueItems.size > 1

    val hasNextInQueue: Boolean
        get() = queueIndex >= 0 && queueIndex + 1 < queueItems.size

    val hasPreviousInQueue: Boolean
        get() = queueIndex > 0 && queueIndex < queueItems.size
}

internal fun formatPlaybackErrorMessage(
    errorCode: Int,
    rawMessage: String?,
    isNetworkStream: Boolean,
    isConnected: Boolean,
): String {
    if (isNetworkStream && !isConnected) {
        return "No internet connection. Playback will resume automatically when you're back online."
    }
    val trimmedRaw = rawMessage?.trim().orEmpty()
    val isGenericMpvMessage = trimmedRaw.isEmpty() ||
        trimmedRaw.startsWith("mpv playback error (", ignoreCase = true)
    if (isNetworkStream) {
        if (!isGenericMpvMessage) return trimmedRaw
        return when (errorCode) {
            -13 -> "Unable to connect to stream. Check your connection or stream URL."
            else -> "Stream interrupted (error $errorCode). Check your connection and retry."
        }
    }
    return trimmedRaw.ifEmpty { "Playback failed (error $errorCode)" }
}

object TimeFormatters {
    fun formatDuration(ms: Long): String {
        val clampedMs = ms.coerceAtLeast(0L)
        val totalSeconds = clampedMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatSignedDelta(deltaMs: Long): String {
        val sign = if (deltaMs >= 0L) "+" else "-"
        val magnitude = if (deltaMs == Long.MIN_VALUE) Long.MAX_VALUE else abs(deltaMs)
        return "$sign${formatDuration(magnitude)}"
    }
}
