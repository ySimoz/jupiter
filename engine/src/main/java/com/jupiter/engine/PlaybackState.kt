package com.jupiter.engine

enum class EngineLifecycleState {
    IDLE,
    INITIALIZED,
    DESTROYED
}

data class PlaybackState(
    val lifecycleState: EngineLifecycleState = EngineLifecycleState.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPaused: Boolean = false,
    val isBuffering: Boolean = false,
    val bufferingPercent: Int = 0,
    val speed: Float = 1.0f,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoAspect: Double = 0.0,
    val mediaTitle: String? = null,
    val eofReached: Boolean = false,
    val videoTracks: List<VideoTrack> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val currentChapter: Int = -1,
    val hwDecMode: HwDecMode = HwDecMode.AUTO,
    val subDelaySec: Double = 0.0,
    val audioDelaySec: Double = 0.0,
    val videoZoom: Double = 0.0,
    val aspectOverride: String = "-1"
)

object PlaybackStateReducer {
    fun reduceStringProperty(state: PlaybackState, name: String, value: String?): PlaybackState =
        when (name) {
            "track-list" -> {
                val parsed = MpvJsonParser.parseTrackList(value)
                state.copy(
                    videoTracks = parsed.videoTracks,
                    audioTracks = parsed.audioTracks,
                    subtitleTracks = parsed.subtitleTracks
                )
            }

            "chapter-list" -> state.copy(chapters = MpvJsonParser.parseChapterList(value))
            "media-title" -> state.copy(mediaTitle = value)
            "video-aspect-override" -> state.copy(aspectOverride = value ?: "-1")
            else -> state
        }

    fun reduceLongProperty(state: PlaybackState, name: String, value: Long): PlaybackState =
        when (name) {
            "cache-buffering-state" -> state.copy(bufferingPercent = value.toInt().coerceIn(0, 100))
            "video-params/w" -> state.copy(videoWidth = value.toInt().coerceAtLeast(0))
            "video-params/h" -> state.copy(videoHeight = value.toInt().coerceAtLeast(0))
            "chapter" -> state.copy(currentChapter = value.toInt())
            else -> state
        }

    fun reduceDoubleProperty(state: PlaybackState, name: String, value: Double): PlaybackState {
        if (!value.isFinite()) return state
        return when (name) {
            "time-pos" -> state.copy(positionMs = (value * 1000.0).toLong().coerceAtLeast(0L))
            "duration" -> state.copy(durationMs = (value * 1000.0).toLong().coerceAtLeast(0L))
            "speed" -> state.copy(speed = value.toFloat())
            "video-params/aspect" -> state.copy(videoAspect = value)
            "sub-delay" -> state.copy(subDelaySec = value)
            "audio-delay" -> state.copy(audioDelaySec = value)
            "video-zoom" -> state.copy(videoZoom = value)
            else -> state
        }
    }

    fun reduceBooleanProperty(state: PlaybackState, name: String, value: Boolean): PlaybackState =
        when (name) {
            "pause" -> state.copy(isPaused = value)
            "paused-for-cache" -> state.copy(isBuffering = value)
            "eof-reached" -> state.copy(eofReached = value)
            else -> state
        }

    fun reduceEvent(state: PlaybackState, event: MpvEvent): PlaybackState =
        when (event) {
            MpvEvent.StartFile -> state.copy(
                positionMs = 0L,
                durationMs = 0L,
                isBuffering = false,
                bufferingPercent = 0,
                eofReached = false,
                chapters = emptyList(),
                currentChapter = -1
            )

            is MpvEvent.EndFile -> state.copy(
                isBuffering = false,
                eofReached = event.isEof || state.eofReached
            )

            MpvEvent.Shutdown -> state.copy(
                lifecycleState = EngineLifecycleState.DESTROYED,
                isBuffering = false
            )

            else -> state
        }
}
