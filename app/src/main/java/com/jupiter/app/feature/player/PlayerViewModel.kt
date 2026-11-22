package com.jupiter.app.feature.player

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.app.core.di.MpvConfigDir
import com.jupiter.app.core.di.PlaybackController
import com.jupiter.app.data.local.MediaHistoryEntity
import com.jupiter.app.data.local.UserPreferences
import com.jupiter.app.data.media.AndroidUriResolver
import com.jupiter.app.data.media.LocalVideoItem
import com.jupiter.app.data.media.MpvSource
import com.jupiter.app.data.media.UriResolver
import com.jupiter.app.data.repository.InMemorySettingsRepository
import com.jupiter.app.data.repository.MediaRepository
import com.jupiter.app.data.repository.SettingsRepository
import com.jupiter.app.data.repository.StreamHeaderConfig
import com.jupiter.app.service.AudioFocusController
import com.jupiter.app.service.PlaybackServiceLauncher
import com.jupiter.engine.AudioTrack
import com.jupiter.engine.Chapter
import com.jupiter.engine.EngineLifecycleState
import com.jupiter.engine.HwDecMode
import com.jupiter.engine.MpvEvent
import com.jupiter.engine.SubtitleTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToLong

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    @MpvConfigDir private val configDir: String,
    private val uriResolver: UriResolver,
    private val mediaRepository: MediaRepository,
    private val audioFocusController: AudioFocusController,
    private val playbackServiceLauncher: PlaybackServiceLauncher,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private var persistenceDispatcher: CoroutineDispatcher = Dispatchers.IO

    internal constructor(
        playbackController: PlaybackController,
        configDir: String,
        uriResolver: UriResolver = PassThroughUriResolver,
        mediaRepository: MediaRepository = NoOpMediaRepository,
        audioFocusController: AudioFocusController = NoOpAudioFocusController,
        playbackServiceLauncher: PlaybackServiceLauncher = NoOpPlaybackServiceLauncher,
        settingsRepository: SettingsRepository = InMemorySettingsRepository(),
        persistenceDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        playbackController = playbackController,
        configDir = configDir,
        uriResolver = uriResolver,
        mediaRepository = mediaRepository,
        audioFocusController = audioFocusController,
        playbackServiceLauncher = playbackServiceLauncher,
        settingsRepository = settingsRepository,
    ) {
        this.persistenceDispatcher = persistenceDispatcher
    }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // Isolated from PlayerUiState so 60Hz time-pos updates only recompose the scrubber.
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private var loadMediaJob: Job? = null
    private var hudHideJob: Job? = null
    private var unlockHintJob: Job? = null
    private var doubleTapJob: Job? = null
    private var zoomOverlayJob: Job? = null
    private var progressSaveJob: Job? = null

    private var lastDoubleTapSide: DoubleTapSide? = null
    private var accumulatedDoubleTapSeconds: Int = 0
    private var preBoostSpeed: Float = 1.0f
    private var lastWasPaused: Boolean = false
    private var isSheetOpen: Boolean = false
    private var hasAudioFocus: Boolean = false
    private var lastAudioTracks: List<AudioTrack>? = null
    private var lastSubtitleTracks: List<SubtitleTrack>? = null

    private var currentMediaUri: String? = null
    private var currentDisplayName: String = ""
    private var currentIsNetworkStream: Boolean = false
    private var hasLoadedCurrentMedia: Boolean = false
    private var hasExitedCurrentSession: Boolean = false
    private var pendingResumeMs: Long = 0L

    private var lastUri: String? = null
    private var lastTitle: String = ""
    private var lastIsNetworkStream: Boolean = false
    private var lastHeadersJson: String? = null
    private var latestPrefs: UserPreferences = UserPreferences()
    private var appliedPreferredAudioLang: String? = null
    private var appliedPreferredSubtitleLang: String? = null
    private var pendingSurface: Surface? = null
    private var pendingSurfaceWidth: Int = 0
    private var pendingSurfaceHeight: Int = 0

    init {
        viewModelScope.launch {
            settingsRepository.preferences.collect { prefs ->
                val resumeChanged = prefs.rememberResumePosition != latestPrefs.rememberResumePosition
                latestPrefs = prefs
                _uiState.update {
                    it.copy(
                        backgroundPlayEnabled = prefs.backgroundPlayEnabled,
                        autoEnterPip = prefs.autoEnterPip,
                        doubleTapSeekSec = prefs.doubleTapSeekSec,
                        gestureBrightnessEnabled = prefs.gestureBrightnessEnabled,
                        gestureVolumeEnabled = prefs.gestureVolumeEnabled,
                        gestureSeekEnabled = prefs.gestureSeekEnabled,
                    )
                }
                if (resumeChanged) {
                    syncProgressSaveLoop()
                }
            }
        }

        viewModelScope.launch {
            playbackController.state.collect { playback ->
                if (!hasLoadedCurrentMedia && playback.durationMs > 0L && playback.positionMs > 0L) {
                    hasLoadedCurrentMedia = true
                }
                if (!_uiState.value.isScrubbing) {
                    val canAcceptZeroPosition = hasLoadedCurrentMedia && pendingResumeMs == 0L
                    if (playback.positionMs > 0L || canAcceptZeroPosition) {
                        if (playback.positionMs > 0L) {
                            pendingResumeMs = 0L
                        }
                        _positionMs.value = playback.positionMs
                    }
                }

                val pausedChanged = playback.isPaused != lastWasPaused
                lastWasPaused = playback.isPaused

                val audioTracksChanged = playback.audioTracks != lastAudioTracks
                if (audioTracksChanged) {
                    lastAudioTracks = playback.audioTracks
                }
                val subtitleTracksChanged = playback.subtitleTracks != lastSubtitleTracks
                if (subtitleTracksChanged) {
                    lastSubtitleTracks = playback.subtitleTracks
                }

                _uiState.update { current ->
                    val resolvedTitle = if (current.title.isBlank() && !playback.mediaTitle.isNullOrBlank()) {
                        playback.mediaTitle!!
                    } else {
                        current.title
                    }
                    val resolvedDurationMs = if (!hasLoadedCurrentMedia && playback.durationMs == 0L) {
                        current.durationMs
                    } else {
                        playback.durationMs
                    }
                    val resolvedSpeed = if (current.isSpeedBoosted) current.speed else playback.speed
                    val resolvedAudioId = if (audioTracksChanged) {
                        playback.audioTracks.firstOrNull { it.isSelected }?.id
                    } else {
                        current.selectedAudioTrackId
                    }
                    val resolvedSubId = if (subtitleTracksChanged) {
                        playback.subtitleTracks
                            .firstOrNull { it.isSelected && it.id != current.secondarySubtitleTrackId }
                            ?.id
                            ?: if (current.secondarySubtitleTrackId == null) {
                                playback.subtitleTracks.firstOrNull { it.isSelected }?.id
                            } else {
                                null
                            }
                    } else {
                        current.selectedSubtitleTrackId
                    }
                    current.copy(
                        title = resolvedTitle,
                        isPaused = playback.isPaused,
                        isBuffering = playback.isBuffering,
                        bufferingPercent = playback.bufferingPercent,
                        durationMs = resolvedDurationMs,
                        speed = resolvedSpeed,
                        chapters = playback.chapters,
                        videoZoom = playback.videoZoom,
                        audioTracks = playback.audioTracks,
                        subtitleTracks = playback.subtitleTracks,
                        selectedAudioTrackId = resolvedAudioId,
                        selectedSubtitleTrackId = resolvedSubId,
                        subDelaySec = playback.subDelaySec,
                        audioDelaySec = playback.audioDelaySec,
                        hwDecMode = playback.hwDecMode,
                        currentChapter = playback.currentChapter,
                        videoWidth = playback.videoWidth,
                        videoHeight = playback.videoHeight,
                    )
                }

                syncProgressSaveLoop()

                if (pausedChanged) {
                    if (playback.isPaused) {
                        hudHideJob?.cancel()
                    } else {
                        rescheduleHudAutoHide()
                        if (!hasAudioFocus && currentMediaUri != null) {
                            if (requestAudioFocus()) {
                                playbackServiceLauncher.startOrUpdate()
                            } else {
                                playbackController.pause()
                            }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            playbackController.events.collect { event ->
                when (event) {
                    is MpvEvent.FileLoaded -> {
                        hasLoadedCurrentMedia = true
                    }

                    is MpvEvent.Error -> {
                        _uiState.update { it.copy(errorMessage = event.message) }
                    }

                    is MpvEvent.EndFile -> {
                        if (event.isError) {
                            progressSaveJob?.cancel()
                            progressSaveJob = null
                            abandonAudioFocus()
                            _uiState.update { current ->
                                val msg = current.errorMessage
                                    ?: "Playback failed (error ${event.errorCode})"
                                current.copy(errorMessage = msg)
                            }
                        } else if (event.isEof && hasLoadedCurrentMedia) {
                            progressSaveJob?.cancel()
                            progressSaveJob = null
                            abandonAudioFocus()
                            val duration = _uiState.value.durationMs
                            if (duration > 0L) {
                                launchImmediateProgressSave(overridePositionMs = duration)
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    fun loadMedia(
        uri: String,
        title: String,
        isNetworkStream: Boolean = false,
        headersJson: String? = null,
    ) {
        lastUri = uri
        lastTitle = title
        lastIsNetworkStream = isNetworkStream
        lastHeadersJson = headersJson
        hasLoadedCurrentMedia = false
        hasExitedCurrentSession = false
        pendingResumeMs = 0L

        loadMediaJob?.cancel()
        loadMediaJob = viewModelScope.launch {
            val prefs = runCatching { settingsRepository.preferences.first() }.getOrDefault(latestPrefs)
            latestPrefs = prefs

            val previousUri = currentMediaUri
            if (prefs.rememberResumePosition && previousUri != null && previousUri != uri && _uiState.value.durationMs > 0L) {
                saveCurrentProgress()
            }

            if (!ensureInitializedOffMain()) return@launch

            val resolved = withContext(persistenceDispatcher) {
                uriResolver.resolveToMpvPath(uri, title)
            }
            if (resolved.isFailure) {
                currentMediaUri = null
                progressSaveJob?.cancel()
                abandonAudioFocus()
                _uiState.update {
                    it.copy(
                        title = title.ifBlank { extractTitleFromUri(uri) },
                        errorMessage = resolved.exceptionOrNull()?.message ?: "Unable to open media",
                        isPaused = true,
                    )
                }
                return@launch
            }

            val source = resolved.getOrThrow()
            val resolvedTitle = source.displayName.ifBlank {
                title.ifBlank { extractTitleFromUri(uri) }
            }

            currentMediaUri = source.originalUri
            currentDisplayName = resolvedTitle
            currentIsNetworkStream = isNetworkStream

            playbackController.clearAbLoop()
            playbackController.selectSecondarySubtitleTrack(null)
            lastAudioTracks = null
            lastSubtitleTracks = null

            _uiState.update {
                it.copy(
                    title = resolvedTitle,
                    durationMs = if (previousUri != null) 0L else it.durationMs,
                    errorMessage = null,
                    isPaused = false,
                    abLoop = AbLoopState(),
                    secondarySubtitleTrackId = null,
                )
            }

            applyDefaultPlaybackPreferences(prefs)

            if (isNetworkStream) {
                playbackController.setOption("ytdl", "no")
                playbackController.setOption("cache", "yes")
            }
            val headerConfig = StreamHeaderConfig.fromJson(headersJson)
            playbackController.setNetworkHeaders(
                userAgent = headerConfig.userAgent,
                referer = headerConfig.referer,
                headers = headerConfig.headers,
            )

            val resumeMs = if (prefs.rememberResumePosition) {
                runCatching { mediaRepository.getHistory(source.originalUri) }.getOrNull()?.positionMs ?: 0L
            } else {
                0L
            }
            pendingResumeMs = resumeMs
            if (resumeMs > 0L || previousUri != null) {
                _positionMs.value = resumeMs
            }
            val loadOptions = if (resumeMs > 0L) {
                listOf("start=+${resumeMs / 1000.0}")
            } else {
                emptyList()
            }

            val rc = playbackController.loadFile(source.mpvPath, loadOptions)
            if (rc < 0) {
                _uiState.update { it.copy(errorMessage = "Failed to load media ($rc)") }
            } else if (requestAudioFocus(force = true)) {
                playbackController.play()
                playbackServiceLauncher.startOrUpdate()
            } else {
                playbackController.pause()
            }
            syncProgressSaveLoop()
            showHudAndResetTimer()
        }
    }

    fun retryLoad() {
        val uri = lastUri ?: return
        _uiState.update { it.copy(errorMessage = null) }
        loadMedia(
            uri = uri,
            title = lastTitle,
            isNetworkStream = lastIsNetworkStream,
            headersJson = lastHeadersJson,
        )
    }

    fun attachSurface(surface: Surface) {
        pendingSurface = surface
        if (playbackController.state.value.lifecycleState != EngineLifecycleState.INITIALIZED) {
            return
        }
        playbackController.attachSurface(surface)
        if (pendingSurfaceWidth > 0 && pendingSurfaceHeight > 0) {
            playbackController.setSurfaceSize(pendingSurfaceWidth, pendingSurfaceHeight)
        }
    }

    fun onSurfaceSizeChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        pendingSurfaceWidth = width
        pendingSurfaceHeight = height
        if (playbackController.state.value.lifecycleState == EngineLifecycleState.INITIALIZED) {
            playbackController.setSurfaceSize(width, height)
        }
    }

    fun detachSurface() {
        pendingSurface = null
        pendingSurfaceWidth = 0
        pendingSurfaceHeight = 0
        if (playbackController.state.value.lifecycleState == EngineLifecycleState.INITIALIZED) {
            playbackController.detachSurface()
        }
    }

    fun toggleHud() {
        if (_uiState.value.isLocked) {
            flashUnlockHint()
            return
        }
        if (_uiState.value.isHudVisible) {
            hideHud()
        } else {
            showHudAndResetTimer()
        }
    }

    fun showHudAndResetTimer() {
        if (_uiState.value.isLocked) {
            flashUnlockHint()
            return
        }
        _uiState.update { it.copy(isHudVisible = true) }
        rescheduleHudAutoHide()
    }

    fun hideHud() {
        hudHideJob?.cancel()
        _uiState.update { it.copy(isHudVisible = false) }
    }

    fun toggleLock() {
        val nextLocked = !_uiState.value.isLocked
        if (nextLocked) {
            hudHideJob?.cancel()
            doubleTapJob?.cancel()
            zoomOverlayJob?.cancel()
            _uiState.update {
                it.copy(
                    isLocked = true,
                    isHudVisible = false,
                    showUnlockHint = true,
                    gestureOverlay = GestureOverlayState.None,
                )
            }
            scheduleUnlockHintHide()
        } else {
            unlockHintJob?.cancel()
            _uiState.update {
                it.copy(
                    isLocked = false,
                    showUnlockHint = false,
                    isHudVisible = true,
                )
            }
            rescheduleHudAutoHide()
        }
    }

    fun flashUnlockHint() {
        if (!_uiState.value.isLocked) return
        _uiState.update { it.copy(showUnlockHint = true) }
        scheduleUnlockHintHide()
    }

    fun togglePlayPause() {
        val wasPaused = _uiState.value.isPaused
        if (wasPaused) {
            if (!requestAudioFocus(force = true)) return
            playbackController.togglePlayPause()
            playbackServiceLauncher.startOrUpdate()
        } else {
            playbackController.togglePlayPause()
            abandonAudioFocus()
            launchImmediateProgressSave()
        }
        if (_uiState.value.isHudVisible) {
            rescheduleHudAutoHide()
        }
    }

    fun pauseIfPlaying() {
        if (!_uiState.value.isPaused) {
            playbackController.pause()
            abandonAudioFocus()
            launchImmediateProgressSave()
        }
    }

    fun onHostStop(isInPipMode: Boolean) {
        if (isInPipMode) {
            return
        }
        val state = _uiState.value
        if (state.backgroundPlayEnabled && !state.isPaused) {
            playbackServiceLauncher.startOrUpdate()
            return
        }
        pauseIfPlaying()
        playbackServiceLauncher.stop()
    }

    fun onPipAction(action: PipAction) {
        when (action) {
            PipAction.SEEK_BACK -> seekBy(-PIP_SEEK_SECONDS)
            PipAction.PLAY_PAUSE -> togglePlayPause()
            PipAction.SEEK_FORWARD -> seekBy(PIP_SEEK_SECONDS)
        }
    }

    fun setBackgroundPlayEnabled(enabled: Boolean) {
        _uiState.update { it.copy(backgroundPlayEnabled = enabled) }
    }

    fun setAutoEnterPip(enabled: Boolean) {
        _uiState.update { it.copy(autoEnterPip = enabled) }
    }

    fun seekBy(deltaSeconds: Int) {
        pendingResumeMs = 0L
        val target = clampSeekTarget(
            targetMs = _positionMs.value + deltaSeconds * 1000L,
            durationMs = _uiState.value.durationMs,
        )
        _positionMs.value = target
        playbackController.seekBy(deltaSeconds.toDouble())
        if (_uiState.value.isHudVisible) {
            rescheduleHudAutoHide()
        }
    }

    fun seekTo(targetMs: Long) {
        pendingResumeMs = 0L
        val clamped = clampSeekTarget(targetMs, _uiState.value.durationMs)
        _positionMs.value = clamped
        playbackController.seekTo(clamped)
        if (_uiState.value.isHudVisible) {
            rescheduleHudAutoHide()
        }
    }

    fun onScrubStart() {
        hudHideJob?.cancel()
        _uiState.update {
            it.copy(
                isScrubbing = true,
                scrubPreviewMs = _positionMs.value,
            )
        }
    }

    fun onScrubChange(targetMs: Long) {
        val clamped = clampSeekTarget(targetMs, _uiState.value.durationMs)
        _positionMs.value = clamped
        _uiState.update {
            it.copy(
                isScrubbing = true,
                scrubPreviewMs = clamped,
            )
        }
    }

    fun onScrubFinish(targetMs: Long) {
        pendingResumeMs = 0L
        val clamped = clampSeekTarget(targetMs, _uiState.value.durationMs)
        _positionMs.value = clamped
        _uiState.update {
            it.copy(
                isScrubbing = false,
                scrubPreviewMs = null,
            )
        }
        playbackController.seekTo(clamped)
        rescheduleHudAutoHide()
    }

    fun onDoubleTap(side: DoubleTapSide) {
        if (_uiState.value.isLocked) {
            flashUnlockHint()
            return
        }
        pendingResumeMs = 0L
        val stepSeconds = _uiState.value.doubleTapSeekSec
        val sameSideActive = lastDoubleTapSide == side && doubleTapJob?.isActive == true
        accumulatedDoubleTapSeconds = if (sameSideActive) {
            accumulatedDoubleTapSeconds + stepSeconds
        } else {
            lastDoubleTapSide = side
            stepSeconds
        }

        val deltaSeconds = if (side == DoubleTapSide.LEFT) -stepSeconds else stepSeconds
        val target = clampSeekTarget(
            targetMs = _positionMs.value + deltaSeconds * 1000L,
            durationMs = _uiState.value.durationMs,
        )
        _positionMs.value = target
        playbackController.seekBy(deltaSeconds.toDouble())

        _uiState.update {
            it.copy(
                gestureOverlay = GestureOverlayState.DoubleTapSeek(
                    side = side,
                    accumulatedSeconds = accumulatedDoubleTapSeconds,
                ),
            )
        }

        doubleTapJob?.cancel()
        doubleTapJob = viewModelScope.launch {
            delay(DOUBLE_TAP_TIMEOUT_MS)
            accumulatedDoubleTapSeconds = 0
            lastDoubleTapSide = null
            _uiState.update { current ->
                if (current.gestureOverlay is GestureOverlayState.DoubleTapSeek) {
                    current.copy(gestureOverlay = GestureOverlayState.None)
                } else {
                    current
                }
            }
        }
    }

    fun onHorizontalDragUpdate(targetMs: Long, deltaMs: Long) {
        if (_uiState.value.isLocked || !_uiState.value.gestureSeekEnabled) return
        val duration = _uiState.value.durationMs
        val clampedTarget = clampSeekTarget(targetMs, duration)
        _uiState.update {
            it.copy(
                gestureOverlay = GestureOverlayState.HorizontalSeek(
                    targetMs = clampedTarget,
                    deltaMs = deltaMs,
                    durationMs = duration,
                ),
            )
        }
    }

    fun onHorizontalDragEnd(commitMs: Long) {
        if (_uiState.value.isLocked || !_uiState.value.gestureSeekEnabled) return
        pendingResumeMs = 0L
        val clamped = clampSeekTarget(commitMs, _uiState.value.durationMs)
        _positionMs.value = clamped
        _uiState.update { it.copy(gestureOverlay = GestureOverlayState.None) }
        playbackController.seekTo(clamped)
        if (_uiState.value.isHudVisible) {
            rescheduleHudAutoHide()
        }
    }

    fun onHorizontalDragCancel() {
        _uiState.update { current ->
            if (current.gestureOverlay is GestureOverlayState.HorizontalSeek) {
                current.copy(gestureOverlay = GestureOverlayState.None)
            } else {
                current
            }
        }
    }

    fun onBrightnessGesture(fraction: Float) {
        if (_uiState.value.isLocked || !_uiState.value.gestureBrightnessEnabled) return
        _uiState.update {
            it.copy(gestureOverlay = GestureOverlayState.Brightness(clampBrightness(fraction)))
        }
    }

    fun onVolumeGesture(fraction: Float, percent: Int) {
        if (_uiState.value.isLocked || !_uiState.value.gestureVolumeEnabled) return
        _uiState.update {
            it.copy(
                gestureOverlay = GestureOverlayState.Volume(
                    fraction = fraction.coerceIn(0f, 1f),
                    percent = percent.coerceIn(0, 100),
                ),
            )
        }
    }

    fun clearGestureOverlay() {
        doubleTapJob?.cancel()
        zoomOverlayJob?.cancel()
        accumulatedDoubleTapSeconds = 0
        lastDoubleTapSide = null
        _uiState.update { it.copy(gestureOverlay = GestureOverlayState.None) }
    }

    fun onLongPressSpeedStart() {
        if (_uiState.value.isLocked || _uiState.value.isSpeedBoosted) return
        preBoostSpeed = _uiState.value.speed
        playbackController.setSpeed(BOOST_SPEED)
        _uiState.update {
            it.copy(
                isSpeedBoosted = true,
                speed = BOOST_SPEED,
                gestureOverlay = GestureOverlayState.SpeedBoost,
            )
        }
    }

    fun onLongPressSpeedEnd() {
        if (!_uiState.value.isSpeedBoosted) return
        val restored = preBoostSpeed
        playbackController.setSpeed(restored)
        _uiState.update { current ->
            current.copy(
                isSpeedBoosted = false,
                speed = restored,
                gestureOverlay = if (current.gestureOverlay is GestureOverlayState.SpeedBoost) {
                    GestureOverlayState.None
                } else {
                    current.gestureOverlay
                },
            )
        }
    }

    fun onPinchZoom(newZoom: Double) {
        if (_uiState.value.isLocked) return
        val clamped = clampZoom(newZoom)
        playbackController.setVideoZoom(clamped)
        _uiState.update {
            it.copy(
                videoZoom = clamped,
                gestureOverlay = GestureOverlayState.Zoom(
                    zoomValue = clamped,
                    label = formatZoomLabel(clamped),
                ),
            )
        }
        zoomOverlayJob?.cancel()
        zoomOverlayJob = viewModelScope.launch {
            delay(ZOOM_OVERLAY_TIMEOUT_MS)
            _uiState.update { current ->
                if (current.gestureOverlay is GestureOverlayState.Zoom) {
                    current.copy(gestureOverlay = GestureOverlayState.None)
                } else {
                    current
                }
            }
        }
    }

    fun cycleAspectMode() {
        val entries = AspectMode.entries
        val next = entries[(_uiState.value.aspectMode.ordinal + 1) % entries.size]
        setAspectMode(next)
    }

    fun setAspectMode(mode: AspectMode) {
        playbackController.setProperty("keepaspect", if (mode.keepAspect) "yes" else "no")
        playbackController.setAspectOverride(mode.mpvAspect)
        playbackController.setProperty("panscan", mode.mpvPanscan.toString())
        _uiState.update { it.copy(aspectMode = mode) }
        rescheduleHudAutoHideIfVisible()
    }

    fun cycleSpeed() {
        val current = _uiState.value.speed
        val index = SPEED_PRESETS.indexOfFirst { abs(it - current) < 0.01f }
        val next = if (index >= 0) {
            SPEED_PRESETS[(index + 1) % SPEED_PRESETS.size]
        } else {
            SPEED_PRESETS.firstOrNull { it > current } ?: SPEED_PRESETS.first()
        }
        setSpeed(next)
    }

    fun setSpeed(speed: Float) {
        val clamped = if (speed.isFinite()) speed.coerceIn(MIN_SPEED, MAX_SPEED) else 1.0f
        playbackController.setSpeed(clamped)
        _uiState.update { it.copy(speed = clamped, isSpeedBoosted = false) }
        rescheduleHudAutoHideIfVisible()
    }

    fun selectAudioTrack(id: Int?) {
        playbackController.selectAudioTrack(id)
        _uiState.update { it.copy(selectedAudioTrackId = id) }
        rescheduleHudAutoHideIfVisible()
    }

    fun selectSubtitleTrack(id: Int?) {
        playbackController.selectSubtitleTrack(id)
        _uiState.update { it.copy(selectedSubtitleTrackId = id) }
        rescheduleHudAutoHideIfVisible()
    }

    fun selectSecondarySubtitleTrack(id: Int?) {
        playbackController.selectSecondarySubtitleTrack(id)
        _uiState.update { it.copy(secondarySubtitleTrackId = id) }
        rescheduleHudAutoHideIfVisible()
    }

    fun addExternalSubtitle(uriString: String) {
        if (uriString.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Subtitle URI is empty") }
            return
        }
        viewModelScope.launch {
            if (!ensureInitializedOffMain()) return@launch
            val resolved = withContext(persistenceDispatcher) {
                uriResolver.resolveToMpvPath(uriString)
            }
            if (resolved.isFailure) {
                _uiState.update {
                    it.copy(
                        errorMessage = resolved.exceptionOrNull()?.message
                            ?: "Unable to load external subtitle",
                    )
                }
                return@launch
            }
            val source = resolved.getOrThrow()
            val rc = playbackController.addExternalSubtitle(source.mpvPath)
            if (rc < 0) {
                _uiState.update { it.copy(errorMessage = "Failed to add external subtitle ($rc)") }
            }
            rescheduleHudAutoHideIfVisible()
        }
    }

    fun adjustSubtitleDelay(deltaSec: Double) {
        if (!deltaSec.isFinite()) return
        val next = roundToMillisSeconds(_uiState.value.subDelaySec + deltaSec)
        playbackController.setSubtitleDelay(next)
        _uiState.update { it.copy(subDelaySec = next) }
        rescheduleHudAutoHideIfVisible()
    }

    fun resetSubtitleDelay() {
        playbackController.setSubtitleDelay(0.0)
        _uiState.update { it.copy(subDelaySec = 0.0) }
        rescheduleHudAutoHideIfVisible()
    }

    fun setSubtitleScale(scale: Double) {
        val clamped = if (scale.isFinite()) scale.coerceIn(MIN_SUB_SCALE, MAX_SUB_SCALE) else DEFAULT_SUB_SCALE
        playbackController.setSubtitleScale(clamped)
        _uiState.update { it.copy(subScale = clamped) }
        rescheduleHudAutoHideIfVisible()
    }

    fun resetSubtitleScale() {
        setSubtitleScale(DEFAULT_SUB_SCALE)
    }

    fun adjustAudioDelay(deltaSec: Double) {
        if (!deltaSec.isFinite()) return
        val next = roundToMillisSeconds(_uiState.value.audioDelaySec + deltaSec)
        playbackController.setAudioDelay(next)
        _uiState.update { it.copy(audioDelaySec = next) }
        rescheduleHudAutoHideIfVisible()
    }

    fun resetAudioDelay() {
        playbackController.setAudioDelay(0.0)
        _uiState.update { it.copy(audioDelaySec = 0.0) }
        rescheduleHudAutoHideIfVisible()
    }

    fun setHwDecMode(mode: HwDecMode) {
        playbackController.setHwDec(mode)
        _uiState.update { it.copy(hwDecMode = mode) }
        rescheduleHudAutoHideIfVisible()
    }

    fun jumpToChapter(chapter: Chapter) {
        pendingResumeMs = 0L
        val targetMs = clampSeekTarget((chapter.timeSec * 1000.0).toLong(), _uiState.value.durationMs)
        _positionMs.value = targetMs
        playbackController.selectChapter(chapter.index)
        _uiState.update { it.copy(currentChapter = chapter.index) }
        rescheduleHudAutoHideIfVisible()
    }

    fun toggleAbLoop() {
        val current = _uiState.value.abLoop
        val posMs = _positionMs.value.coerceAtLeast(0L)
        when {
            current.pointAMs == null -> {
                playbackController.setAbLoopA(posMs / 1000.0)
                _uiState.update { it.copy(abLoop = AbLoopState(pointAMs = posMs, pointBMs = null)) }
            }

            current.pointBMs == null -> {
                val pointA = current.pointAMs
                if (posMs > pointA) {
                    playbackController.setAbLoopB(posMs / 1000.0)
                    _uiState.update { it.copy(abLoop = AbLoopState(pointAMs = pointA, pointBMs = posMs)) }
                } else {
                    playbackController.setAbLoopA(posMs / 1000.0)
                    playbackController.setAbLoopB(pointA / 1000.0)
                    _uiState.update { it.copy(abLoop = AbLoopState(pointAMs = posMs, pointBMs = pointA)) }
                }
            }

            else -> {
                clearAbLoop()
                return
            }
        }
        rescheduleHudAutoHideIfVisible()
    }

    fun clearAbLoop() {
        playbackController.clearAbLoop()
        _uiState.update { it.copy(abLoop = AbLoopState()) }
        rescheduleHudAutoHideIfVisible()
    }

    fun setEqualizer(
        brightness: Int = uiState.value.equalizer.brightness,
        contrast: Int = uiState.value.equalizer.contrast,
        saturation: Int = uiState.value.equalizer.saturation,
        gamma: Int = uiState.value.equalizer.gamma,
    ) {
        val current = _uiState.value.equalizer
        val next = VideoEqualizerState(
            brightness = brightness.coerceIn(MIN_EQUALIZER, MAX_EQUALIZER),
            contrast = contrast.coerceIn(MIN_EQUALIZER, MAX_EQUALIZER),
            saturation = saturation.coerceIn(MIN_EQUALIZER, MAX_EQUALIZER),
            gamma = gamma.coerceIn(MIN_EQUALIZER, MAX_EQUALIZER),
        )
        if (next.brightness != current.brightness) {
            playbackController.setEqualizerProperty("brightness", next.brightness)
        }
        if (next.contrast != current.contrast) {
            playbackController.setEqualizerProperty("contrast", next.contrast)
        }
        if (next.saturation != current.saturation) {
            playbackController.setEqualizerProperty("saturation", next.saturation)
        }
        if (next.gamma != current.gamma) {
            playbackController.setEqualizerProperty("gamma", next.gamma)
        }
        _uiState.update { it.copy(equalizer = next) }
        rescheduleHudAutoHideIfVisible()
    }

    fun resetEqualizer() {
        setEqualizer(brightness = 0, contrast = 0, saturation = 0, gamma = 0)
    }

    fun cycleOrientationLock() {
        val entries = OrientationLock.entries
        val next = entries[(_uiState.value.orientationLock.ordinal + 1) % entries.size]
        _uiState.update { it.copy(orientationLock = next) }
        if (_uiState.value.isHudVisible) {
            rescheduleHudAutoHide()
        }
    }

    fun toggleRemainingTime() {
        _uiState.update { it.copy(showRemainingTime = !it.showRemainingTime) }
        if (_uiState.value.isHudVisible) {
            rescheduleHudAutoHide()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onSheetVisibilityChanged(isOpen: Boolean) {
        if (isSheetOpen == isOpen) return
        isSheetOpen = isOpen
        if (isOpen) {
            hudHideJob?.cancel()
        } else {
            rescheduleHudAutoHide()
        }
    }

    fun prepareForExit() {
        if (hasExitedCurrentSession) return
        hasExitedCurrentSession = true
        loadMediaJob?.cancel()
        progressSaveJob?.cancel()
        progressSaveJob = null
        hudHideJob?.cancel()

        val uriToSave = currentMediaUri
        val titleToSave = currentDisplayName
        val posToSave = _positionMs.value
        val stateSnapshot = _uiState.value
        if (latestPrefs.rememberResumePosition && uriToSave != null && stateSnapshot.durationMs > 0L) {
            val effectiveSpeed = if (stateSnapshot.isSpeedBoosted) preBoostSpeed else stateSnapshot.speed
            val isNetwork = currentIsNetworkStream
            CoroutineScope(SupervisorJob() + persistenceDispatcher).launch {
                runCatching {
                    mediaRepository.saveProgress(
                        uri = uriToSave,
                        displayName = titleToSave,
                        positionMs = posToSave,
                        durationMs = stateSnapshot.durationMs,
                        playbackSpeed = effectiveSpeed,
                        isNetworkStream = isNetwork,
                    )
                }
            }
        }
        if (stateSnapshot.isSpeedBoosted) {
            playbackController.setSpeed(preBoostSpeed)
        }
        playbackController.pause()
        abandonAudioFocus(force = true)
        playbackServiceLauncher.stop()
    }

    override fun onCleared() {
        prepareForExit()
        super.onCleared()
    }

    private fun requestAudioFocus(force: Boolean = false): Boolean {
        if (hasAudioFocus && !force) return true
        val granted = audioFocusController.requestFocus(
            onPause = {
                hasAudioFocus = false
                if (!_uiState.value.isPaused) {
                    playbackController.pause()
                    launchImmediateProgressSave()
                }
            },
            onResume = {
                hasAudioFocus = true
                if (currentMediaUri != null && _uiState.value.isPaused) {
                    playbackController.play()
                    playbackServiceLauncher.startOrUpdate()
                }
            },
            onVolumeChange = { percent ->
                playbackController.setVolume(percent)
            },
        )
        hasAudioFocus = granted
        return granted
    }

    private fun abandonAudioFocus(force: Boolean = false) {
        if (hasAudioFocus || force) {
            hasAudioFocus = false
            audioFocusController.abandonFocus()
        }
    }

    private fun applyDefaultPlaybackPreferences(prefs: UserPreferences) {
        playbackController.setHwDec(prefs.hwDecMode)
        val defaultSpeed = prefs.defaultPlaybackSpeed.coerceIn(MIN_SPEED, MAX_SPEED)
        playbackController.setSpeed(defaultSpeed)
        val defaultSubScale = prefs.subtitleFontScale.toDouble().coerceIn(MIN_SUB_SCALE, MAX_SUB_SCALE)
        playbackController.setSubtitleScale(defaultSubScale)

        val alang = prefs.preferredAudioLang.trim()
        if (alang.isNotEmpty()) {
            playbackController.setProperty("alang", alang)
            appliedPreferredAudioLang = alang
        } else if (appliedPreferredAudioLang != null) {
            playbackController.setProperty("alang", "")
            appliedPreferredAudioLang = null
        }

        val slang = prefs.preferredSubtitleLang.trim()
        if (slang.isNotEmpty()) {
            playbackController.setProperty("slang", slang)
            appliedPreferredSubtitleLang = slang
        } else if (appliedPreferredSubtitleLang != null) {
            playbackController.setProperty("slang", "")
            appliedPreferredSubtitleLang = null
        }

        _uiState.update { current ->
            current.copy(
                hwDecMode = prefs.hwDecMode,
                speed = if (current.isSpeedBoosted) current.speed else defaultSpeed,
                subScale = defaultSubScale,
                backgroundPlayEnabled = prefs.backgroundPlayEnabled,
                autoEnterPip = prefs.autoEnterPip,
                doubleTapSeekSec = prefs.doubleTapSeekSec,
                gestureBrightnessEnabled = prefs.gestureBrightnessEnabled,
                gestureVolumeEnabled = prefs.gestureVolumeEnabled,
                gestureSeekEnabled = prefs.gestureSeekEnabled,
            )
        }
    }

    private fun syncProgressSaveLoop() {
        val state = _uiState.value
        val shouldRun = latestPrefs.rememberResumePosition &&
            currentMediaUri != null &&
            !state.isPaused &&
            state.durationMs > 0L
        if (shouldRun) {
            if (progressSaveJob?.isActive != true) {
                progressSaveJob = viewModelScope.launch {
                    while (true) {
                        delay(PROGRESS_SAVE_INTERVAL_MS)
                        saveCurrentProgress()
                    }
                }
            }
        } else {
            progressSaveJob?.cancel()
            progressSaveJob = null
        }
    }

    private fun launchImmediateProgressSave(overridePositionMs: Long? = null) {
        if (!latestPrefs.rememberResumePosition || currentMediaUri == null || _uiState.value.durationMs <= 0L) return
        viewModelScope.launch {
            saveCurrentProgress(overridePositionMs = overridePositionMs)
        }
    }

    private suspend fun saveCurrentProgress(overridePositionMs: Long? = null) {
        if (!latestPrefs.rememberResumePosition) return
        val uri = currentMediaUri ?: return
        val state = _uiState.value
        if (state.durationMs <= 0L) return
        val effectiveSpeed = if (state.isSpeedBoosted) preBoostSpeed else state.speed
        val pos = overridePositionMs ?: _positionMs.value
        runCatching {
            mediaRepository.saveProgress(
                uri = uri,
                displayName = currentDisplayName,
                positionMs = pos,
                durationMs = state.durationMs,
                playbackSpeed = effectiveSpeed,
                isNetworkStream = currentIsNetworkStream,
            )
        }
    }

    private suspend fun ensureInitializedOffMain(): Boolean {
        val wasIdle = playbackController.state.value.lifecycleState == EngineLifecycleState.IDLE
        val initialized = withContext(persistenceDispatcher) { ensureInitialized() }
        if (initialized && wasIdle) {
            replayPendingSurface()
        }
        return initialized
    }

    private fun replayPendingSurface() {
        val surface = pendingSurface
        if (surface != null) {
            playbackController.attachSurface(surface)
        }
        if (pendingSurfaceWidth > 0 && pendingSurfaceHeight > 0) {
            playbackController.setSurfaceSize(pendingSurfaceWidth, pendingSurfaceHeight)
        }
    }

    private fun ensureInitialized(): Boolean {
        if (playbackController.state.value.lifecycleState != EngineLifecycleState.IDLE) {
            return true
        }
        return runCatching {
            playbackController.initialize(configDir)
            true
        }.getOrElse { error ->
            _uiState.update {
                it.copy(errorMessage = error.message ?: "Failed to initialize playback engine")
            }
            false
        }
    }

    private fun rescheduleHudAutoHideIfVisible() {
        if (_uiState.value.isHudVisible) {
            rescheduleHudAutoHide()
        }
    }

    private fun roundToMillisSeconds(seconds: Double): Double =
        (seconds * 1000.0).roundToLong() / 1000.0

    private fun rescheduleHudAutoHide() {
        hudHideJob?.cancel()
        val state = _uiState.value
        if (!state.isHudVisible || state.isPaused || state.isLocked || state.isScrubbing || isSheetOpen) {
            return
        }
        hudHideJob = viewModelScope.launch {
            delay(HUD_AUTO_HIDE_MS)
            _uiState.update { current ->
                if (current.isHudVisible && !current.isPaused && !current.isLocked && !current.isScrubbing && !isSheetOpen) {
                    current.copy(isHudVisible = false)
                } else {
                    current
                }
            }
        }
    }

    private fun scheduleUnlockHintHide() {
        unlockHintJob?.cancel()
        unlockHintJob = viewModelScope.launch {
            delay(UNLOCK_HINT_TIMEOUT_MS)
            _uiState.update { it.copy(showUnlockHint = false) }
        }
    }

    private fun clampSeekTarget(targetMs: Long, durationMs: Long): Long =
        if (durationMs > 0L) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L)

    private fun extractTitleFromUri(uri: String): String =
        AndroidUriResolver.extractTitleFromUri(uri)

    private object PassThroughUriResolver : UriResolver {
        override fun resolveToMpvPath(uriString: String, fallbackTitle: String): Result<MpvSource> {
            val name = fallbackTitle.ifBlank { AndroidUriResolver.extractTitleFromUri(uriString) }
            return Result.success(
                MpvSource(
                    mpvPath = uriString,
                    displayName = name,
                    originalUri = uriString,
                ),
            )
        }
    }

    private object NoOpMediaRepository : MediaRepository {
        override suspend fun scanLocalVideos(): List<LocalVideoItem> = emptyList()
        override fun observeContinueWatching(limit: Int): Flow<List<MediaHistoryEntity>> = emptyFlow()
        override suspend fun getHistory(uri: String): MediaHistoryEntity? = null
        override suspend fun saveProgress(
            uri: String,
            displayName: String,
            positionMs: Long,
            durationMs: Long,
            playbackSpeed: Float,
            isNetworkStream: Boolean,
        ) = Unit

        override suspend fun removeHistory(uri: String) = Unit
        override suspend fun clearHistory() = Unit
    }

    private object NoOpAudioFocusController : AudioFocusController {
        override fun requestFocus(
            onPause: () -> Unit,
            onResume: () -> Unit,
            onVolumeChange: (Int) -> Unit,
        ): Boolean = true

        override fun abandonFocus() = Unit
    }

    private object NoOpPlaybackServiceLauncher : PlaybackServiceLauncher {
        override fun startOrUpdate() = Unit
        override fun stop() = Unit
    }

    companion object {
        private const val HUD_AUTO_HIDE_MS = 4_000L
        private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
        private const val DOUBLE_TAP_TIMEOUT_MS = 800L
        private const val ZOOM_OVERLAY_TIMEOUT_MS = 900L
        private const val UNLOCK_HINT_TIMEOUT_MS = 2_000L
        private const val DOUBLE_TAP_STEP_SECONDS = 10
        private const val PIP_SEEK_SECONDS = 10
        private const val BOOST_SPEED = 2.0f
        private const val MIN_SPEED = 0.25f
        private const val MAX_SPEED = 4.0f
        private const val MIN_SUB_SCALE = 0.5
        private const val MAX_SUB_SCALE = 2.5
        private const val DEFAULT_SUB_SCALE = 1.0
        private const val MIN_EQUALIZER = -100
        private const val MAX_EQUALIZER = 100
        private val SPEED_PRESETS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    }
}
