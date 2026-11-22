package com.jupiter.engine

import android.view.Surface
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class MpvEngine {
    private val lock = ReentrantReadWriteLock()

    @Volatile
    private var nativeHandle: Long = 0L

    @Volatile
    private var lifecycleState: EngineLifecycleState = EngineLifecycleState.IDLE

    private var lastSurfaceWidth: Int = 0
    private var lastSurfaceHeight: Int = 0

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<MpvEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<MpvEvent> = _events.asSharedFlow()

    private val callback = object : MpvEventCallback {
        override fun onPropertyChangeString(name: String, value: String?) {
            if (lifecycleState == EngineLifecycleState.DESTROYED) return
            _state.update { PlaybackStateReducer.reduceStringProperty(it, name, value) }
        }

        override fun onPropertyChangeLong(name: String, value: Long) {
            if (lifecycleState == EngineLifecycleState.DESTROYED) return
            _state.update { PlaybackStateReducer.reduceLongProperty(it, name, value) }
        }

        override fun onPropertyChangeDouble(name: String, value: Double) {
            if (lifecycleState == EngineLifecycleState.DESTROYED) return
            _state.update { PlaybackStateReducer.reduceDoubleProperty(it, name, value) }
        }

        override fun onPropertyChangeBoolean(name: String, value: Boolean) {
            if (lifecycleState == EngineLifecycleState.DESTROYED) return
            _state.update { PlaybackStateReducer.reduceBooleanProperty(it, name, value) }
        }

        override fun onEvent(eventId: Int, reason: Int, errorCode: Int) {
            if (lifecycleState == EngineLifecycleState.DESTROYED) return
            val event = when (eventId) {
                MpvNative.EVENT_START_FILE -> MpvEvent.StartFile
                MpvNative.EVENT_FILE_LOADED -> MpvEvent.FileLoaded
                MpvNative.EVENT_END_FILE -> MpvEvent.EndFile(reason, errorCode)
                MpvNative.EVENT_SEEK -> MpvEvent.Seek
                MpvNative.EVENT_PLAYBACK_RESTART -> MpvEvent.PlaybackRestart
                MpvNative.EVENT_SHUTDOWN -> MpvEvent.Shutdown
                else -> null
            } ?: return

            _state.update { PlaybackStateReducer.reduceEvent(it, event) }
            _events.tryEmit(event)

            if (event is MpvEvent.EndFile && event.isError) {
                _events.tryEmit(
                    MpvEvent.Error(
                        message = "mpv playback error ($errorCode)",
                        errorCode = errorCode
                    )
                )
            }
        }
    }

    fun initialize(configDir: String, extraOptions: Map<String, String> = emptyMap()) {
        lock.write {
            when (lifecycleState) {
                EngineLifecycleState.INITIALIZED -> return
                EngineLifecycleState.DESTROYED ->
                    throw IllegalStateException("Cannot initialize a destroyed MpvEngine")

                EngineLifecycleState.IDLE -> Unit
            }

            val handle = MpvNative.nativeCreate(callback, configDir)
            if (handle == 0L) {
                throw IllegalStateException("nativeCreate failed")
            }

            try {
                val resolvedOptions = LinkedHashMap<String, String>(
                    DEFAULT_STREAM_OPTIONS.size + DEFAULT_SUBTITLE_OPTIONS.size + extraOptions.size
                )
                resolvedOptions.putAll(DEFAULT_STREAM_OPTIONS)
                resolvedOptions.putAll(DEFAULT_SUBTITLE_OPTIONS)
                resolvedOptions.putAll(extraOptions)
                for ((key, value) in resolvedOptions) {
                    val optRc = MpvNative.nativeSetOptionString(handle, key, value)
                    if (optRc < 0) {
                        _events.tryEmit(MpvEvent.Error("Failed to set option $key ($optRc)", optRc))
                    }
                }
                val initRc = MpvNative.nativeInitialize(handle)
                if (initRc < 0) {
                    throw IllegalStateException("nativeInitialize failed with error $initRc")
                }
                if (lastSurfaceWidth > 0 && lastSurfaceHeight > 0) {
                    MpvNative.nativeSetPropertyString(
                        handle,
                        PROP_ANDROID_SURFACE_SIZE,
                        formatSurfaceSize(lastSurfaceWidth, lastSurfaceHeight)
                    )
                }
            } catch (t: Throwable) {
                MpvNative.nativeDestroy(handle)
                throw t
            }

            nativeHandle = handle
            lifecycleState = EngineLifecycleState.INITIALIZED
            _state.update { it.copy(lifecycleState = EngineLifecycleState.INITIALIZED) }
        }
    }

    fun destroy() {
        lock.write {
            if (lifecycleState == EngineLifecycleState.DESTROYED) return
            val handle = nativeHandle
            nativeHandle = 0L
            lastSurfaceWidth = 0
            lastSurfaceHeight = 0
            lifecycleState = EngineLifecycleState.DESTROYED
            _state.update { it.copy(lifecycleState = EngineLifecycleState.DESTROYED, isBuffering = false) }
            if (handle != 0L) {
                MpvNative.nativeDestroy(handle)
            }
        }
    }

    fun attachSurface(surface: Surface): Int = lock.write {
        val handle = nativeHandle
        if (handle == 0L || lifecycleState != EngineLifecycleState.INITIALIZED) {
            return@write -1
        }
        val rc = MpvNative.nativeAttachSurface(handle, surface)
        if (rc >= 0 && lastSurfaceWidth > 0 && lastSurfaceHeight > 0) {
            MpvNative.nativeSetPropertyString(
                handle,
                PROP_ANDROID_SURFACE_SIZE,
                formatSurfaceSize(lastSurfaceWidth, lastSurfaceHeight)
            )
        }
        rc
    }

    fun setSurfaceSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 0
        return lock.write {
            lastSurfaceWidth = width
            lastSurfaceHeight = height
            val handle = nativeHandle
            if (handle == 0L || lifecycleState != EngineLifecycleState.INITIALIZED) {
                0
            } else {
                MpvNative.nativeSetPropertyString(
                    handle,
                    PROP_ANDROID_SURFACE_SIZE,
                    formatSurfaceSize(width, height)
                )
            }
        }
    }

    fun detachSurface(): Int = lock.write {
        lastSurfaceWidth = 0
        lastSurfaceHeight = 0
        val handle = nativeHandle
        if (handle == 0L || lifecycleState != EngineLifecycleState.INITIALIZED) {
            return@write -1
        }
        MpvNative.nativeDetachSurface(handle)
    }

    fun loadFile(uriOrFd: String, options: List<String> = emptyList()): Int =
        withInitializedHandle(-1) { handle ->
            val optArray = if (options.isEmpty()) null else options.toTypedArray()
            MpvNative.nativeLoadFile(handle, uriOrFd, optArray)
        }

    fun play(): Int = withInitializedHandle(-1) { handle ->
        MpvNative.nativeSetPropertyBoolean(handle, "pause", false)
    }

    fun pause(): Int = withInitializedHandle(-1) { handle ->
        MpvNative.nativeSetPropertyBoolean(handle, "pause", true)
    }

    fun togglePlayPause(): Int = command("cycle", "pause")

    fun seekTo(ms: Long): Int {
        val seconds = ms.coerceAtLeast(0L) / 1000.0
        return command("seek", String.format(Locale.US, "%.3f", seconds), "absolute")
    }

    fun seekBy(deltaSec: Double): Int =
        command("seek", String.format(Locale.US, "%.3f", deltaSec), "relative")

    fun setSpeed(speed: Float): Int = withInitializedHandle(-1) { handle ->
        val rc = MpvNative.nativeSetPropertyDouble(handle, "speed", speed.toDouble())
        if (rc >= 0) {
            _state.update { it.copy(speed = speed) }
        }
        rc
    }

    fun setVolume(percent: Int): Int = withInitializedHandle(-1) { handle ->
        val clamped = percent.coerceIn(0, 100).toDouble()
        MpvNative.nativeSetPropertyDouble(handle, "volume", clamped)
    }

    fun selectAudioTrack(id: Int?): Int = withInitializedHandle(-1) { handle ->
        MpvNative.nativeSetPropertyString(handle, "aid", id?.toString() ?: "no")
    }

    fun selectSubtitleTrack(id: Int?): Int = withInitializedHandle(-1) { handle ->
        MpvNative.nativeSetPropertyString(handle, "sid", id?.toString() ?: "no")
    }

    fun selectSecondarySubtitleTrack(id: Int?): Int = withInitializedHandle(-1) { handle ->
        MpvNative.nativeSetPropertyString(handle, "secondary-sid", id?.toString() ?: "no")
    }

    fun addExternalSubtitle(fdPath: String): Int =
        command("sub-add", fdPath, "select")

    fun setSubtitleDelay(sec: Double): Int = withInitializedHandle(-1) { handle ->
        val rc = MpvNative.nativeSetPropertyDouble(handle, "sub-delay", sec)
        if (rc >= 0) {
            _state.update { it.copy(subDelaySec = sec) }
        }
        rc
    }

    fun setAudioDelay(sec: Double): Int = withInitializedHandle(-1) { handle ->
        val rc = MpvNative.nativeSetPropertyDouble(handle, "audio-delay", sec)
        if (rc >= 0) {
            _state.update { it.copy(audioDelaySec = sec) }
        }
        rc
    }

    fun setSubtitleScale(scale: Double): Int = withInitializedHandle(-1) { handle ->
        MpvNative.nativeSetPropertyDouble(handle, "sub-scale", scale)
    }

    fun setVideoZoom(zoom: Double): Int = withInitializedHandle(-1) { handle ->
        val rc = MpvNative.nativeSetPropertyDouble(handle, "video-zoom", zoom)
        if (rc >= 0) {
            _state.update { it.copy(videoZoom = zoom) }
        }
        rc
    }

    fun setAspectOverride(aspect: String): Int = withInitializedHandle(-1) { handle ->
        val mpvAspect = if (aspect.equals("auto", ignoreCase = true)) "-1" else aspect
        val rc = MpvNative.nativeSetPropertyString(handle, "video-aspect-override", mpvAspect)
        if (rc >= 0) {
            _state.update { it.copy(aspectOverride = mpvAspect) }
        }
        rc
    }

    fun setHwDec(mode: HwDecMode): Int = withInitializedHandle(-1) { handle ->
        val rc = MpvNative.nativeSetPropertyString(handle, "hwdec", mode.mpvValue)
        if (rc >= 0) {
            _state.update { it.copy(hwDecMode = mode) }
        }
        rc
    }

    fun setAbLoopA(seconds: Double?): Int = withInitializedHandle(-1) { handle ->
        val value = seconds?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)?.toString() ?: "no"
        MpvNative.nativeSetPropertyString(handle, "ab-loop-a", value)
    }

    fun setAbLoopB(seconds: Double?): Int = withInitializedHandle(-1) { handle ->
        val value = seconds?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)?.toString() ?: "no"
        MpvNative.nativeSetPropertyString(handle, "ab-loop-b", value)
    }

    fun clearAbLoop(): Int = withInitializedHandle(-1) { handle ->
        val rcA = MpvNative.nativeSetPropertyString(handle, "ab-loop-a", "no")
        val rcB = MpvNative.nativeSetPropertyString(handle, "ab-loop-b", "no")
        if (rcA < 0) rcA else rcB
    }

    fun setEqualizerProperty(name: String, value: Int): Int {
        require(name in EQUALIZER_PROPERTIES) { "Unsupported equalizer property: $name" }
        val clamped = value.coerceIn(-100, 100).toLong()
        return withInitializedHandle(-1) { handle ->
            MpvNative.nativeSetPropertyLong(handle, name, clamped)
        }
    }

    fun selectChapter(index: Int): Int = withInitializedHandle(-1) { handle ->
        val target = index.coerceAtLeast(0)
        val rc = MpvNative.nativeSetPropertyLong(handle, "chapter", target.toLong())
        if (rc >= 0) {
            _state.update { it.copy(currentChapter = target) }
        }
        rc
    }

    fun setOption(name: String, value: String): Int = withInitializedHandle(-1) { handle ->
        MpvNative.nativeSetOptionString(handle, name, value)
    }

    fun setNetworkHeaders(
        userAgent: String?,
        referer: String?,
        headers: Map<String, String>,
    ) {
        withInitializedHandle(Unit) { handle ->
            val cleanUserAgent = sanitizeHeaderToken(userAgent.orEmpty())
            val cleanReferer = sanitizeHeaderToken(referer.orEmpty())
            val formattedHeaders = buildHeaderFieldsValue(headers)

            MpvNative.nativeSetPropertyString(handle, "user-agent", cleanUserAgent)
            MpvNative.nativeSetPropertyString(handle, "referrer", cleanReferer)
            MpvNative.nativeSetPropertyString(handle, "http-header-fields", formattedHeaders)
        }
    }

    fun command(vararg args: String): Int = withInitializedHandle(-1) { handle ->
        if (args.isEmpty()) return@withInitializedHandle -1
        MpvNative.nativeCommand(handle, arrayOf(*args))
    }

    fun getPropertyString(name: String): String? = withInitializedHandle(null) { handle ->
        MpvNative.nativeGetPropertyString(handle, name)
    }

    fun getPropertyLong(name: String): Long? = withInitializedHandle(null) { handle ->
        MpvNative.nativeGetPropertyLong(handle, name)
    }

    fun getPropertyDouble(name: String): Double? = withInitializedHandle(null) { handle ->
        MpvNative.nativeGetPropertyDouble(handle, name)
    }

    fun getPropertyBoolean(name: String): Boolean? = withInitializedHandle(null) { handle ->
        MpvNative.nativeGetPropertyBoolean(handle, name)
    }

    private inline fun <T> withInitializedHandle(default: T, block: (Long) -> T): T {
        return lock.read {
            val handle = nativeHandle
            if (handle == 0L || lifecycleState != EngineLifecycleState.INITIALIZED) {
                default
            } else {
                block(handle)
            }
        }
    }

    companion object {
        internal val DEFAULT_STREAM_OPTIONS: Map<String, String> = linkedMapOf(
            "cache" to "yes",
            "demuxer-max-bytes" to "128MiB",
            "demuxer-max-back-bytes" to "32MiB",
            "ytdl" to "no",
        )

        internal val DEFAULT_SUBTITLE_OPTIONS: Map<String, String> = linkedMapOf(
            "sub-font-provider" to "none",
            "embeddedfonts" to "yes",
        )

        private val EQUALIZER_PROPERTIES: Set<String> = setOf(
            "brightness",
            "contrast",
            "saturation",
            "gamma",
        )

        private const val PROP_ANDROID_SURFACE_SIZE = "android-surface-size"

        internal fun formatSurfaceSize(width: Int, height: Int): String = "${width}x${height}"

        internal fun sanitizeHeaderToken(raw: String): String =
            raw.replace("\r", "").replace("\n", "").trim()

        internal fun buildHeaderFieldsValue(headers: Map<String, String>): String {
            if (headers.isEmpty()) return ""
            return headers.entries
                .mapNotNull { (rawKey, rawValue) ->
                    val key = sanitizeHeaderToken(rawKey).replace(":", "")
                    if (key.isBlank()) return@mapNotNull null
                    val value = sanitizeHeaderToken(rawValue)
                    "$key: $value"
                }
                .joinToString(",")
        }
    }
}
