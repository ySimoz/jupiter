package com.jupiter.app.feature.player

import android.view.Surface
import android.view.SurfaceView
import com.jupiter.app.core.di.PlaybackController
import com.jupiter.app.data.local.MediaHistoryEntity
import com.jupiter.app.data.local.UserPreferences
import com.jupiter.app.data.media.LocalVideoItem
import com.jupiter.app.data.media.MpvSource
import com.jupiter.app.data.media.UriHash
import com.jupiter.app.data.media.UriResolver
import com.jupiter.app.data.repository.InMemorySettingsRepository
import com.jupiter.app.data.repository.InMemoryVideoBookmarkRepository
import com.jupiter.app.data.repository.MediaRepository
import com.jupiter.app.data.repository.ResumeRules
import com.jupiter.app.data.repository.StreamHeaderConfig
import com.jupiter.app.service.AudioFocusController
import com.jupiter.app.service.PlaybackServiceLauncher
import com.jupiter.engine.AudioTrack
import com.jupiter.engine.Chapter
import com.jupiter.engine.EngineLifecycleState
import com.jupiter.engine.HwDecMode
import com.jupiter.engine.MpvEvent
import com.jupiter.engine.MpvNative
import com.jupiter.engine.PlaybackState
import com.jupiter.engine.SubtitleTrack
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeController: FakePlaybackController
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeController = FakePlaybackController()
        viewModel = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            persistenceDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadMedia initializes engine lazily resolves fallback title and isolates positionMs`() = runTest {
        viewModel.loadMedia(uri = "https://example.com/movies/Big%20Buck%20Bunny.mp4?token=1", title = "")
        runCurrent()

        assertEquals(1, fakeController.initializeCount)
        assertEquals("https://example.com/movies/Big%20Buck%20Bunny.mp4?token=1", fakeController.lastLoadedUri)
        assertEquals("Big Buck Bunny.mp4", viewModel.uiState.value.title)

        val initialUiSnapshot = viewModel.uiState.value
        fakeController.emitState { it.copy(positionMs = 42_500L) }
        runCurrent()

        assertEquals(42_500L, viewModel.positionMs.value)
        assertEquals(initialUiSnapshot, viewModel.uiState.value)
    }

    @Test
    fun `HUD auto-hides after 4000ms while playing and cancels when paused scrubbing or locked`() = runTest {
        viewModel.loadMedia(uri = "file:///sample.mkv", title = "Sample")
        fakeController.emitState { it.copy(durationMs = 120_000L, isPaused = false) }
        runCurrent()
        assertTrue(viewModel.uiState.value.isHudVisible)

        advanceTimeBy(3_999L)
        runCurrent()
        assertTrue(viewModel.uiState.value.isHudVisible)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(viewModel.uiState.value.isHudVisible)

        // Show HUD again, then pause -> must remain visible past 4000ms
        viewModel.showHudAndResetTimer()
        runCurrent()
        fakeController.emitState { it.copy(isPaused = true) }
        runCurrent()
        advanceTimeBy(10_000L)
        runCurrent()
        assertTrue(viewModel.uiState.value.isHudVisible)

        // Resume playing -> auto-hide resumes; scrubbing cancels it
        fakeController.emitState { it.copy(isPaused = false) }
        runCurrent()
        advanceTimeBy(2_000L)
        viewModel.onScrubStart()
        runCurrent()
        advanceTimeBy(10_000L)
        runCurrent()
        assertTrue(viewModel.uiState.value.isHudVisible)

        viewModel.onScrubFinish(30_000L)
        runCurrent()
        assertEquals(30_000L, fakeController.lastSeekToMs)
        advanceTimeBy(4_000L)
        runCurrent()
        assertFalse(viewModel.uiState.value.isHudVisible)
        viewModel.pauseIfPlaying()
        runCurrent()
    }

    @Test
    fun `doubleTap accumulates consecutive taps on same side within 800ms and auto clears`() = runTest {
        viewModel.loadMedia(uri = "file:///sample.mkv", title = "Sample")
        fakeController.emitState { it.copy(durationMs = 120_000L, positionMs = 50_000L) }
        runCurrent()

        viewModel.onDoubleTap(DoubleTapSide.RIGHT)
        runCurrent()
        assertEquals(
            GestureOverlayState.DoubleTapSeek(DoubleTapSide.RIGHT, 10),
            viewModel.uiState.value.gestureOverlay,
        )
        assertEquals(listOf(10.0), fakeController.seekByHistory)

        advanceTimeBy(400L)
        viewModel.onDoubleTap(DoubleTapSide.RIGHT)
        runCurrent()
        assertEquals(
            GestureOverlayState.DoubleTapSeek(DoubleTapSide.RIGHT, 20),
            viewModel.uiState.value.gestureOverlay,
        )

        advanceTimeBy(400L)
        viewModel.onDoubleTap(DoubleTapSide.RIGHT)
        runCurrent()
        assertEquals(
            GestureOverlayState.DoubleTapSeek(DoubleTapSide.RIGHT, 30),
            viewModel.uiState.value.gestureOverlay,
        )
        assertEquals(listOf(10.0, 10.0, 10.0), fakeController.seekByHistory)

        // Opposite side resets accumulation
        viewModel.onDoubleTap(DoubleTapSide.LEFT)
        runCurrent()
        assertEquals(
            GestureOverlayState.DoubleTapSeek(DoubleTapSide.LEFT, 10),
            viewModel.uiState.value.gestureOverlay,
        )

        advanceTimeBy(800L)
        runCurrent()
        assertEquals(GestureOverlayState.None, viewModel.uiState.value.gestureOverlay)
        viewModel.pauseIfPlaying()
        runCurrent()
    }

    @Test
    fun `longPress boosts speed to 2x and restores previous speed on release`() = runTest {
        viewModel.loadMedia(uri = "file:///sample.mkv", title = "Sample")
        runCurrent()

        viewModel.cycleSpeed() // 1.0 -> 1.25
        runCurrent()
        assertEquals(1.25f, viewModel.uiState.value.speed, 0.001f)

        viewModel.onLongPressSpeedStart()
        runCurrent()
        assertTrue(viewModel.uiState.value.isSpeedBoosted)
        assertEquals(2.0f, viewModel.uiState.value.speed, 0.001f)
        assertEquals(GestureOverlayState.SpeedBoost, viewModel.uiState.value.gestureOverlay)
        assertEquals(2.0f, fakeController.lastSpeed, 0.001f)

        viewModel.onLongPressSpeedEnd()
        runCurrent()
        assertFalse(viewModel.uiState.value.isSpeedBoosted)
        assertEquals(1.25f, viewModel.uiState.value.speed, 0.001f)
        assertEquals(GestureOverlayState.None, viewModel.uiState.value.gestureOverlay)
        assertEquals(1.25f, fakeController.lastSpeed, 0.001f)
    }

    @Test
    fun `cycleAspectMode updates mpv keepaspect aspect override and panscan`() = runTest {
        viewModel.loadMedia(uri = "file:///sample.mkv", title = "Sample")
        runCurrent()
        assertEquals(AspectMode.FIT, viewModel.uiState.value.aspectMode)

        viewModel.cycleAspectMode() // FILL
        runCurrent()
        assertEquals(AspectMode.FILL, viewModel.uiState.value.aspectMode)
        assertEquals("yes", fakeController.properties["keepaspect"])
        assertEquals("-1", fakeController.lastAspectOverride)
        assertEquals("1.0", fakeController.properties["panscan"])

        viewModel.cycleAspectMode() // STRETCH
        runCurrent()
        assertEquals(AspectMode.STRETCH, viewModel.uiState.value.aspectMode)
        assertEquals("no", fakeController.properties["keepaspect"])
        assertEquals("0.0", fakeController.properties["panscan"])

        viewModel.cycleAspectMode() // 16:9
        runCurrent()
        assertEquals(AspectMode.RATIO_16_9, viewModel.uiState.value.aspectMode)
        assertEquals("yes", fakeController.properties["keepaspect"])
        assertEquals("16:9", fakeController.lastAspectOverride)
    }

    @Test
    fun `toggleLock hides HUD ignores gestures and flashes unlock hint`() = runTest {
        viewModel.loadMedia(uri = "file:///sample.mkv", title = "Sample")
        runCurrent()

        viewModel.toggleLock()
        runCurrent()
        assertTrue(viewModel.uiState.value.isLocked)
        assertFalse(viewModel.uiState.value.isHudVisible)
        assertTrue(viewModel.uiState.value.showUnlockHint)

        advanceTimeBy(2_000L)
        runCurrent()
        assertFalse(viewModel.uiState.value.showUnlockHint)

        // Single tap or double tap while locked flashes unlock hint and does not seek
        viewModel.toggleHud()
        runCurrent()
        assertFalse(viewModel.uiState.value.isHudVisible)
        assertTrue(viewModel.uiState.value.showUnlockHint)

        viewModel.onDoubleTap(DoubleTapSide.RIGHT)
        runCurrent()
        assertTrue(fakeController.seekByHistory.isEmpty())
        assertEquals(GestureOverlayState.None, viewModel.uiState.value.gestureOverlay)

        viewModel.toggleLock()
        runCurrent()
        assertFalse(viewModel.uiState.value.isLocked)
        assertTrue(viewModel.uiState.value.isHudVisible)
        assertFalse(viewModel.uiState.value.showUnlockHint)
    }

    @Test
    fun `pinchZoom clamps zoom and auto clears overlay after 900ms`() = runTest {
        viewModel.loadMedia(uri = "file:///sample.mkv", title = "Sample")
        runCurrent()

        viewModel.onPinchZoom(3.5)
        runCurrent()
        assertEquals(2.0, viewModel.uiState.value.videoZoom, 0.0001)
        assertEquals(
            GestureOverlayState.Zoom(2.0, "400%"),
            viewModel.uiState.value.gestureOverlay,
        )

        advanceTimeBy(900L)
        runCurrent()
        assertEquals(GestureOverlayState.None, viewModel.uiState.value.gestureOverlay)
    }

    @Test
    fun `errors from EndFile and Error events populate errorMessage and dismiss clears it`() = runTest {
        viewModel.loadMedia(uri = "https://example.com/broken.m3u8", title = "Broken")
        runCurrent()

        fakeController.emitEvent(MpvEvent.EndFile(MpvNative.END_FILE_REASON_ERROR, -13))
        runCurrent()
        assertEquals("Playback failed (error -13)", viewModel.uiState.value.errorMessage)

        viewModel.dismissError()
        runCurrent()
        assertNull(viewModel.uiState.value.errorMessage)

        fakeController.emitEvent(MpvEvent.Error("Network timeout", -5))
        runCurrent()
        assertEquals("Network timeout", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `cycleOrientationLock and toggleRemainingTime update uiState`() = runTest {
        assertEquals(OrientationLock.AUTO, viewModel.uiState.value.orientationLock)
        viewModel.cycleOrientationLock()
        assertEquals(OrientationLock.LANDSCAPE, viewModel.uiState.value.orientationLock)
        viewModel.cycleOrientationLock()
        assertEquals(OrientationLock.PORTRAIT, viewModel.uiState.value.orientationLock)
        viewModel.cycleOrientationLock()
        assertEquals(OrientationLock.AUTO, viewModel.uiState.value.orientationLock)

        assertFalse(viewModel.uiState.value.showRemainingTime)
        viewModel.toggleRemainingTime()
        assertTrue(viewModel.uiState.value.showRemainingTime)
    }

    @Test
    fun `onSheetVisibilityChanged pauses HUD auto-hide while open and reschedules on close`() = runTest {
        viewModel.loadMedia(uri = "file:///sample.mkv", title = "Sample")
        fakeController.emitState { it.copy(durationMs = 120_000L, isPaused = false) }
        runCurrent()
        assertTrue(viewModel.uiState.value.isHudVisible)

        advanceTimeBy(2_000L)
        viewModel.onSheetVisibilityChanged(true)
        runCurrent()

        advanceTimeBy(10_000L)
        runCurrent()
        assertTrue(viewModel.uiState.value.isHudVisible)

        viewModel.onSheetVisibilityChanged(false)
        runCurrent()
        advanceTimeBy(3_999L)
        runCurrent()
        assertTrue(viewModel.uiState.value.isHudVisible)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(viewModel.uiState.value.isHudVisible)
        viewModel.pauseIfPlaying()
        runCurrent()
    }

    @Test
    fun `loadMedia resumes paused singleton engine and onCleared restores speed and pauses`() = runTest {
        fakeController.pause()
        assertTrue(fakeController.state.value.isPaused)

        viewModel.loadMedia(uri = "file:///next.mkv", title = "Next")
        runCurrent()
        assertFalse(fakeController.state.value.isPaused)

        viewModel.cycleSpeed() // 1.25f
        viewModel.onLongPressSpeedStart()
        runCurrent()
        assertEquals(2.0f, fakeController.lastSpeed, 0.001f)

        val onClearedMethod = PlayerViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(viewModel)

        assertEquals(1.25f, fakeController.lastSpeed, 0.001f)
        assertTrue(fakeController.state.value.isPaused)
    }

    @Test
    fun `loadMedia resolves content URI to fdclose and surfaces resolution failure`() = runTest {
        val fakeUriResolver = FakeUriResolver { uri, _ ->
            if (uri.startsWith("content://bad")) {
                Result.failure(IllegalStateException("Permission revoked"))
            } else {
                Result.success(
                    MpvSource(
                        mpvPath = "fdclose://42",
                        displayName = "Resolved Movie.mkv",
                        originalUri = uri,
                    ),
                )
            }
        }
        val repo = FakeMediaRepository()
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            uriResolver = fakeUriResolver,
            mediaRepository = repo,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadMedia(uri = "content://media/external/video/media/42", title = "")
        runCurrent()

        assertEquals("fdclose://42", fakeController.lastLoadedUri)
        assertEquals("Resolved Movie.mkv", vm.uiState.value.title)
        assertNull(vm.uiState.value.errorMessage)

        vm.loadMedia(uri = "content://bad/99", title = "Broken")
        runCurrent()

        assertEquals("Permission revoked", vm.uiState.value.errorMessage)
        assertEquals("fdclose://42", fakeController.lastLoadedUri)
    }

    @Test
    fun `loadMedia passes start option from history and auto-saves every 5s, on pause, EndFile, and onCleared`() = runTest {
        val mediaUri = "content://media/external/video/media/88"
        val repo = FakeMediaRepository()
        repo.seedHistory(
            MediaHistoryEntity(
                uriHash = UriHash.of(mediaUri),
                uri = mediaUri,
                displayName = "Episode 1.mkv",
                durationMs = 100_000L,
                positionMs = 45_000L,
                lastPlayedEpochMs = 1_000L,
            ),
        )
        val fakeUriResolver = FakeUriResolver { uri, _ ->
            Result.success(
                MpvSource(
                    mpvPath = "fdclose://88",
                    displayName = "Episode 1.mkv",
                    originalUri = uri,
                ),
            )
        }
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            uriResolver = fakeUriResolver,
            mediaRepository = repo,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadMedia(uri = mediaUri, title = "")
        fakeController.emitState {
            it.copy(durationMs = 100_000L, positionMs = 45_000L, isPaused = false)
        }
        runCurrent()

        assertEquals("fdclose://88", fakeController.lastLoadedUri)
        assertEquals(listOf("start=+45.0"), fakeController.lastLoadOptions)

        // Periodic 5000ms auto-save while playing
        fakeController.emitState { it.copy(positionMs = 50_000L) }
        runCurrent()
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(1, repo.saveCalls.size)
        assertEquals(50_000L, repo.getHistory(mediaUri)?.positionMs)

        // Immediate save on togglePlayPause when pausing
        fakeController.emitState { it.copy(positionMs = 62_000L) }
        runCurrent()
        vm.togglePlayPause()
        runCurrent()
        assertEquals(2, repo.saveCalls.size)
        assertEquals(62_000L, repo.getHistory(mediaUri)?.positionMs)

        // Immediate save on onCleared (>95% resets resume position to 0)
        fakeController.emitState { it.copy(positionMs = 96_000L) }
        runCurrent()
        val onClearedMethod = PlayerViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(vm)
        runCurrent()
        assertEquals(3, repo.saveCalls.size)
        assertEquals(0L, repo.getHistory(mediaUri)?.positionMs)

        // EndFile EOF also resets resume position to 0
        repo.seedHistory(
            MediaHistoryEntity(
                uriHash = UriHash.of(mediaUri),
                uri = mediaUri,
                displayName = "Episode 1.mkv",
                durationMs = 100_000L,
                positionMs = 50_000L,
                lastPlayedEpochMs = 2_000L,
            ),
        )
        fakeController.emitEvent(MpvEvent.EndFile(MpvNative.END_FILE_REASON_EOF, 0))
        runCurrent()
        assertEquals(4, repo.saveCalls.size)
        assertEquals(0L, repo.getHistory(mediaUri)?.positionMs)
    }

    @Test
    fun `loadMedia applies network headers clears them for local files and retryLoad reloads with saved headers`() = runTest {
        val headersJson = StreamHeaderConfig(
            userAgent = "JupiterPlayer/1.0",
            referer = "https://portal.example.com/",
            headers = mapOf("Authorization" to "Bearer secret", "X-Env" to "prod"),
        ).toJsonOrNull()

        viewModel.loadMedia(
            uri = "https://cdn.example.com/live/master.m3u8",
            title = "Live Stream",
            isNetworkStream = true,
            headersJson = headersJson,
        )
        runCurrent()

        assertEquals(1, fakeController.headerCalls.size)
        assertEquals(
            FakePlaybackController.HeaderCall(
                userAgent = "JupiterPlayer/1.0",
                referer = "https://portal.example.com/",
                headers = mapOf("Authorization" to "Bearer secret", "X-Env" to "prod"),
            ),
            fakeController.headerCalls.last(),
        )
        assertEquals("https://cdn.example.com/live/master.m3u8", fakeController.lastLoadedUri)

        // Simulate stream error then retryLoad -> clears error and reloads with the same saved headers
        fakeController.emitEvent(MpvEvent.Error("Connection reset", -5))
        runCurrent()
        assertEquals("Connection reset", viewModel.uiState.value.errorMessage)

        viewModel.retryLoad()
        runCurrent()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(2, fakeController.headerCalls.size)
        assertEquals(fakeController.headerCalls[0], fakeController.headerCalls[1])
        assertEquals("https://cdn.example.com/live/master.m3u8", fakeController.lastLoadedUri)

        // Subsequent local file load must clear previous stream headers
        viewModel.loadMedia(
            uri = "file:///sdcard/Movies/local.mkv",
            title = "Local Movie",
            isNetworkStream = false,
            headersJson = null,
        )
        runCurrent()

        assertEquals(3, fakeController.headerCalls.size)
        assertEquals(
            FakePlaybackController.HeaderCall(
                userAgent = null,
                referer = null,
                headers = emptyMap(),
            ),
            fakeController.headerCalls.last(),
        )
    }

    @Test
    fun `audio subtitle and secondary subtitle selection sync from state and handle Off`() = runTest {
        viewModel.loadMedia(uri = "file:///movie.mkv", title = "Movie")
        runCurrent()

        val audioTracks = listOf(
            AudioTrack(id = 1, title = "English", lang = "eng", isSelected = true),
            AudioTrack(id = 2, title = "Japanese", lang = "jpn", isSelected = false),
        )
        val subTracks = listOf(
            SubtitleTrack(id = 1, title = "Signs", lang = "eng", isSelected = true),
            SubtitleTrack(id = 2, title = "Full", lang = "jpn", isSelected = false),
        )
        fakeController.emitState {
            it.copy(audioTracks = audioTracks, subtitleTracks = subTracks)
        }
        runCurrent()

        assertEquals(audioTracks, viewModel.uiState.value.audioTracks)
        assertEquals(subTracks, viewModel.uiState.value.subtitleTracks)
        assertEquals(1, viewModel.uiState.value.selectedAudioTrackId)
        assertEquals(1, viewModel.uiState.value.selectedSubtitleTrackId)
        assertNull(viewModel.uiState.value.secondarySubtitleTrackId)

        viewModel.selectAudioTrack(2)
        viewModel.selectSubtitleTrack(null)
        viewModel.selectSecondarySubtitleTrack(2)
        runCurrent()

        assertEquals(2, fakeController.lastAudioTrackId)
        assertEquals(2, viewModel.uiState.value.selectedAudioTrackId)
        assertNull(fakeController.lastSubtitleTrackId)
        assertNull(viewModel.uiState.value.selectedSubtitleTrackId)
        assertEquals(2, fakeController.lastSecondarySubtitleTrackId)
        assertEquals(2, viewModel.uiState.value.secondarySubtitleTrackId)

        // When track-list emits both primary=1 and secondary=2 as selected, primary stays 1
        fakeController.emitState {
            it.copy(
                subtitleTracks = listOf(
                    SubtitleTrack(id = 1, title = "Signs", lang = "eng", isSelected = true),
                    SubtitleTrack(id = 2, title = "Full", lang = "jpn", isSelected = true),
                ),
            )
        }
        runCurrent()
        assertEquals(1, viewModel.uiState.value.selectedSubtitleTrackId)
        assertEquals(2, viewModel.uiState.value.secondarySubtitleTrackId)
    }

    @Test
    fun `addExternalSubtitle resolves fdclose path and surfaces resolution failures`() = runTest {
        val fakeUriResolver = FakeUriResolver { uri, _ ->
            if (uri.contains("invalid")) {
                Result.failure(IllegalStateException("Subtitle permission denied"))
            } else {
                Result.success(
                    MpvSource(
                        mpvPath = "fdclose://77",
                        displayName = "movie.ass",
                        originalUri = uri,
                    ),
                )
            }
        }
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            uriResolver = fakeUriResolver,
            persistenceDispatcher = testDispatcher,
        )

        vm.addExternalSubtitle("content://com.android.externalstorage.documents/document/77")
        runCurrent()

        assertEquals(1, fakeController.initializeCount)
        assertEquals(listOf("fdclose://77"), fakeController.addedExternalSubtitles)
        assertNull(vm.uiState.value.errorMessage)

        vm.addExternalSubtitle("content://invalid/sub.srt")
        runCurrent()

        assertEquals("Subtitle permission denied", vm.uiState.value.errorMessage)
        assertEquals(1, fakeController.addedExternalSubtitles.size)
    }

    @Test
    fun `subtitle delay audio delay and subtitle scale adjust clamp and reset cleanly`() = runTest {
        viewModel.loadMedia(uri = "file:///movie.mkv", title = "Movie")
        runCurrent()

        viewModel.adjustSubtitleDelay(0.1)
        viewModel.adjustSubtitleDelay(0.1)
        viewModel.adjustSubtitleDelay(0.1)
        runCurrent()
        assertEquals(0.3, viewModel.uiState.value.subDelaySec, 0.0001)
        assertEquals(0.3, fakeController.lastSubDelaySec, 0.0001)

        viewModel.resetSubtitleDelay()
        runCurrent()
        assertEquals(0.0, viewModel.uiState.value.subDelaySec, 0.0001)
        assertEquals(0.0, fakeController.lastSubDelaySec, 0.0001)

        viewModel.adjustAudioDelay(-0.2)
        viewModel.adjustAudioDelay(0.1)
        runCurrent()
        assertEquals(-0.1, viewModel.uiState.value.audioDelaySec, 0.0001)
        assertEquals(-0.1, fakeController.lastAudioDelaySec, 0.0001)

        viewModel.resetAudioDelay()
        runCurrent()
        assertEquals(0.0, viewModel.uiState.value.audioDelaySec, 0.0001)

        viewModel.setSubtitleScale(0.1)
        assertEquals(0.5, viewModel.uiState.value.subScale, 0.0001)
        assertEquals(0.5, fakeController.lastSubScale, 0.0001)

        viewModel.setSubtitleScale(5.0)
        assertEquals(2.5, viewModel.uiState.value.subScale, 0.0001)
        assertEquals(2.5, fakeController.lastSubScale, 0.0001)

        viewModel.resetSubtitleScale()
        assertEquals(1.0, viewModel.uiState.value.subScale, 0.0001)
    }

    @Test
    fun `speed clamping setAspectMode hwdec switch and chapter jump update engine and state`() = runTest {
        viewModel.loadMedia(uri = "file:///movie.mkv", title = "Movie")
        fakeController.emitState { it.copy(durationMs = 180_000L) }
        runCurrent()

        viewModel.setSpeed(0.1f)
        assertEquals(0.25f, viewModel.uiState.value.speed, 0.001f)
        viewModel.setSpeed(6.0f)
        assertEquals(4.0f, viewModel.uiState.value.speed, 0.001f)

        viewModel.setAspectMode(AspectMode.RATIO_21_9)
        assertEquals(AspectMode.RATIO_21_9, viewModel.uiState.value.aspectMode)
        assertEquals("21:9", fakeController.lastAspectOverride)

        viewModel.setHwDecMode(HwDecMode.SOFTWARE)
        assertEquals(HwDecMode.SOFTWARE, viewModel.uiState.value.hwDecMode)
        assertEquals(HwDecMode.SOFTWARE, fakeController.lastHwDecMode)

        val chapter = Chapter(index = 2, title = "Act II", timeSec = 65.5)
        viewModel.jumpToChapter(chapter)
        assertEquals(2, fakeController.lastSelectedChapter)
        assertEquals(2, viewModel.uiState.value.currentChapter)
        assertEquals(65_500L, viewModel.positionMs.value)

        viewModel.pauseIfPlaying()
        runCurrent()
    }

    @Test
    fun `abLoop state machine sets A sets or swaps B clears and resets on loadMedia`() = runTest {
        viewModel.loadMedia(uri = "file:///movie.mkv", title = "Movie")
        fakeController.emitState { it.copy(durationMs = 120_000L, positionMs = 15_000L) }
        runCurrent()

        // Step 1: A = 15s
        viewModel.toggleAbLoop()
        assertEquals(AbLoopState(pointAMs = 15_000L, pointBMs = null), viewModel.uiState.value.abLoop)
        assertTrue(viewModel.uiState.value.abLoop.isActive)
        assertEquals(15.0, fakeController.lastAbLoopA!!, 0.001)

        // Step 2: pos > A -> B = 35s
        fakeController.emitState { it.copy(positionMs = 35_000L) }
        runCurrent()
        viewModel.toggleAbLoop()
        assertEquals(AbLoopState(pointAMs = 15_000L, pointBMs = 35_000L), viewModel.uiState.value.abLoop)
        assertEquals(35.0, fakeController.lastAbLoopB!!, 0.001)

        // Step 3: both set -> clear (loadMedia already called clearAbLoop once on load)
        viewModel.toggleAbLoop()
        assertEquals(AbLoopState(), viewModel.uiState.value.abLoop)
        assertFalse(viewModel.uiState.value.abLoop.isActive)
        assertEquals(2, fakeController.clearAbLoopCount)

        // Swap case: A = 40s, then pos = 10s -> A = 10s, B = 40s
        fakeController.emitState { it.copy(positionMs = 40_000L) }
        runCurrent()
        viewModel.toggleAbLoop()
        fakeController.emitState { it.copy(positionMs = 10_000L) }
        runCurrent()
        viewModel.toggleAbLoop()
        assertEquals(AbLoopState(pointAMs = 10_000L, pointBMs = 40_000L), viewModel.uiState.value.abLoop)
        assertEquals(10.0, fakeController.lastAbLoopA!!, 0.001)
        assertEquals(40.0, fakeController.lastAbLoopB!!, 0.001)

        // Loading a new media file resets A-B loop and secondary subtitle
        viewModel.selectSecondarySubtitleTrack(2)
        viewModel.loadMedia(uri = "file:///next.mkv", title = "Next")
        runCurrent()
        assertEquals(AbLoopState(), viewModel.uiState.value.abLoop)
        assertNull(viewModel.uiState.value.secondarySubtitleTrackId)

        viewModel.pauseIfPlaying()
        runCurrent()
        assertTrue(viewModel.uiState.value.isPaused)
        val onClearedMethod = PlayerViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(viewModel)
        runCurrent()
    }

    @Test
    fun `equalizer clamps values applies only changed properties and resets`() = runTest {
        viewModel.loadMedia(uri = "file:///movie.mkv", title = "Movie")
        runCurrent()

        assertTrue(viewModel.uiState.value.equalizer.isDefault)

        viewModel.setEqualizer(brightness = 150, contrast = -150, saturation = 25)
        assertEquals(
            VideoEqualizerState(brightness = 100, contrast = -100, saturation = 25, gamma = 0),
            viewModel.uiState.value.equalizer,
        )
        assertFalse(viewModel.uiState.value.equalizer.isDefault)
        assertEquals(
            listOf("brightness" to 100, "contrast" to -100, "saturation" to 25),
            fakeController.equalizerCalls,
        )

        fakeController.equalizerCalls.clear()
        viewModel.setEqualizer(gamma = 12)
        assertEquals(listOf("gamma" to 12), fakeController.equalizerCalls)

        fakeController.equalizerCalls.clear()
        viewModel.resetEqualizer()
        assertTrue(viewModel.uiState.value.equalizer.isDefault)
        assertEquals(
            listOf("brightness" to 0, "contrast" to 0, "saturation" to 0, "gamma" to 0),
            fakeController.equalizerCalls,
        )
    }

    @Test
    fun `videoWidth and videoHeight sync from PlaybackState into PlayerUiState`() = runTest {
        viewModel.loadMedia(uri = "file:///movie.mkv", title = "Movie")
        runCurrent()

        fakeController.emitState { it.copy(videoWidth = 1920, videoHeight = 1080) }
        runCurrent()

        assertEquals(1920, viewModel.uiState.value.videoWidth)
        assertEquals(1080, viewModel.uiState.value.videoHeight)
    }

    @Test
    fun `onHostStop keeps playing in PiP and with backgroundPlayEnabled true, pauses when disabled`() = runTest {
        val focusController = FakeAudioFocusController()
        val serviceLauncher = FakePlaybackServiceLauncher()
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            audioFocusController = focusController,
            playbackServiceLauncher = serviceLauncher,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadMedia(uri = "file:///movie.mkv", title = "Movie")
        fakeController.emitState { it.copy(durationMs = 120_000L, isPaused = false) }
        runCurrent()
        assertFalse(vm.uiState.value.isPaused)
        assertEquals(1, serviceLauncher.startOrUpdateCount)
        assertEquals(0, serviceLauncher.stopCount)

        // In PiP: keep playing, do not pause or stop service
        vm.onHostStop(isInPipMode = true)
        runCurrent()
        assertFalse(vm.uiState.value.isPaused)
        assertEquals(0, serviceLauncher.stopCount)

        // Not in PiP, backgroundPlayEnabled = true (default): keep playing and keep service active
        vm.onHostStop(isInPipMode = false)
        runCurrent()
        assertFalse(vm.uiState.value.isPaused)
        assertEquals(2, serviceLauncher.startOrUpdateCount)
        assertEquals(0, serviceLauncher.stopCount)

        // Not in PiP, backgroundPlayEnabled = false: pause and stop service
        vm.setBackgroundPlayEnabled(false)
        vm.onHostStop(isInPipMode = false)
        runCurrent()
        assertTrue(vm.uiState.value.isPaused)
        assertEquals(1, serviceLauncher.stopCount)
        assertEquals(1, focusController.abandonCount)
    }

    @Test
    fun `onPipAction dispatches seek back play pause and seek forward`() = runTest {
        viewModel.loadMedia(uri = "file:///movie.mkv", title = "Movie")
        fakeController.emitState { it.copy(durationMs = 120_000L, positionMs = 40_000L, isPaused = false) }
        runCurrent()

        viewModel.onPipAction(PipAction.SEEK_BACK)
        viewModel.onPipAction(PipAction.SEEK_FORWARD)
        runCurrent()
        assertEquals(listOf(-10.0, 10.0), fakeController.seekByHistory)

        viewModel.onPipAction(PipAction.PLAY_PAUSE)
        runCurrent()
        assertTrue(viewModel.uiState.value.isPaused)

        viewModel.onPipAction(PipAction.PLAY_PAUSE)
        runCurrent()
        assertFalse(viewModel.uiState.value.isPaused)
        viewModel.pauseIfPlaying()
        runCurrent()
    }

    @Test
    fun `audio focus requested on start or resume, handles ducking and transient loss, abandoned on pause and cleared`() = runTest {
        val focusController = FakeAudioFocusController()
        val serviceLauncher = FakePlaybackServiceLauncher()
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            audioFocusController = focusController,
            playbackServiceLauncher = serviceLauncher,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadMedia(uri = "file:///movie.mkv", title = "Movie")
        fakeController.emitState { it.copy(durationMs = 120_000L, isPaused = false) }
        runCurrent()

        assertEquals(1, focusController.requestCount)
        assertEquals(1, serviceLauncher.startOrUpdateCount)
        assertEquals(0, focusController.abandonCount)

        // Ducking & restore via audio focus volume callback
        focusController.triggerVolumeChange(20)
        assertEquals(20, fakeController.lastVolumePercent)
        focusController.triggerVolumeChange(100)
        assertEquals(100, fakeController.lastVolumePercent)

        // Transient loss pauses without abandoning focus, then regain resumes
        focusController.triggerPause()
        runCurrent()
        assertTrue(vm.uiState.value.isPaused)
        assertEquals(0, focusController.abandonCount)

        focusController.triggerResume()
        runCurrent()
        assertFalse(vm.uiState.value.isPaused)
        assertEquals(2, serviceLauncher.startOrUpdateCount)

        // Explicit pause abandons focus, then resume requests it again
        vm.togglePlayPause()
        runCurrent()
        assertTrue(vm.uiState.value.isPaused)
        assertEquals(1, focusController.abandonCount)

        vm.togglePlayPause()
        runCurrent()
        assertFalse(vm.uiState.value.isPaused)
        assertEquals(2, focusController.requestCount)
        assertEquals(3, serviceLauncher.startOrUpdateCount)

        // onCleared abandons focus and stops service
        val onClearedMethod = PlayerViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(vm)
        runCurrent()

        assertEquals(2, focusController.abandonCount)
        assertEquals(1, serviceLauncher.stopCount)
    }

    @Test
    fun `manual togglePlayPause after focus loss pause re-requests audio focus`() = runTest {
        val focusController = FakeAudioFocusController()
        val serviceLauncher = FakePlaybackServiceLauncher()
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            audioFocusController = focusController,
            playbackServiceLauncher = serviceLauncher,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadMedia(uri = "file:///movie.mkv", title = "Movie")
        fakeController.emitState { it.copy(durationMs = 120_000L, isPaused = false) }
        runCurrent()
        assertEquals(1, focusController.requestCount)
        assertEquals(1, serviceLauncher.startOrUpdateCount)

        focusController.triggerPause()
        runCurrent()
        assertTrue(vm.uiState.value.isPaused)
        assertEquals(0, focusController.abandonCount)

        vm.togglePlayPause()
        runCurrent()
        assertFalse(vm.uiState.value.isPaused)
        assertEquals(2, focusController.requestCount)
        assertEquals(2, serviceLauncher.startOrUpdateCount)

        vm.pauseIfPlaying()
        runCurrent()
    }

    @Test
    fun `onCleared abandons audio focus even when paused by transient focus loss`() = runTest {
        val focusController = FakeAudioFocusController()
        val serviceLauncher = FakePlaybackServiceLauncher()
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            audioFocusController = focusController,
            playbackServiceLauncher = serviceLauncher,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadMedia(uri = "file:///movie.mkv", title = "Movie")
        fakeController.emitState { it.copy(durationMs = 120_000L, isPaused = false) }
        runCurrent()

        focusController.triggerPause()
        runCurrent()
        assertEquals(0, focusController.abandonCount)

        val onClearedMethod = PlayerViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(vm)
        runCurrent()

        assertEquals(1, focusController.abandonCount)
        assertEquals(1, serviceLauncher.stopCount)
    }

    @Test
    fun `settings doubleTapSeekSec updates doubleTap step and gesture flags propagate`() = runTest {
        val settingsRepo = InMemorySettingsRepository(
            UserPreferences(
                doubleTapSeekSec = 15,
                gestureBrightnessEnabled = false,
                gestureVolumeEnabled = true,
                gestureSeekEnabled = false,
                backgroundPlayEnabled = false,
                autoEnterPip = false,
            ),
        )
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            settingsRepository = settingsRepo,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadMedia(uri = "file:///movie.mkv", title = "Movie")
        fakeController.emitState { it.copy(durationMs = 180_000L, positionMs = 40_000L) }
        runCurrent()

        assertEquals(15, vm.uiState.value.doubleTapSeekSec)
        assertFalse(vm.uiState.value.gestureBrightnessEnabled)
        assertTrue(vm.uiState.value.gestureVolumeEnabled)
        assertFalse(vm.uiState.value.gestureSeekEnabled)
        assertFalse(vm.uiState.value.backgroundPlayEnabled)
        assertFalse(vm.uiState.value.autoEnterPip)

        vm.onDoubleTap(DoubleTapSide.RIGHT)
        vm.onDoubleTap(DoubleTapSide.RIGHT)
        runCurrent()
        assertEquals(listOf(15.0, 15.0), fakeController.seekByHistory)
        assertEquals(
            GestureOverlayState.DoubleTapSeek(DoubleTapSide.RIGHT, 30),
            vm.uiState.value.gestureOverlay,
        )

        // Disabled brightness & seek gestures are ignored, enabled volume gesture works
        vm.onBrightnessGesture(0.75f)
        assertEquals(
            GestureOverlayState.DoubleTapSeek(DoubleTapSide.RIGHT, 30),
            vm.uiState.value.gestureOverlay,
        )
        vm.onHorizontalDragUpdate(60_000L, 20_000L)
        assertEquals(
            GestureOverlayState.DoubleTapSeek(DoubleTapSide.RIGHT, 30),
            vm.uiState.value.gestureOverlay,
        )
        vm.onVolumeGesture(0.5f, 50)
        assertEquals(GestureOverlayState.Volume(0.5f, 50), vm.uiState.value.gestureOverlay)

        // Live preference update changes step to 30s
        settingsRepo.setDoubleTapSeekSec(30)
        runCurrent()
        assertEquals(30, vm.uiState.value.doubleTapSeekSec)
        vm.onDoubleTap(DoubleTapSide.LEFT)
        runCurrent()
        assertEquals(listOf(15.0, 15.0, -30.0), fakeController.seekByHistory)

        advanceTimeBy(800L)
        vm.pauseIfPlaying()
        runCurrent()
    }

    @Test
    fun `rememberResumePosition false skips history lookup and progress saving`() = runTest {
        val mediaUri = "file:///no-resume.mkv"
        val repo = FakeMediaRepository()
        repo.seedHistory(
            MediaHistoryEntity(
                uriHash = UriHash.of(mediaUri),
                uri = mediaUri,
                displayName = "No Resume",
                durationMs = 120_000L,
                positionMs = 45_000L,
                lastPlayedEpochMs = 1_000L,
            ),
        )
        val settingsRepo = InMemorySettingsRepository(
            UserPreferences(rememberResumePosition = false),
        )
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            mediaRepository = repo,
            settingsRepository = settingsRepo,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadMedia(uri = mediaUri, title = "No Resume")
        fakeController.emitState { it.copy(durationMs = 120_000L, positionMs = 10_000L, isPaused = false) }
        runCurrent()

        assertTrue(fakeController.lastLoadOptions.isEmpty())

        advanceTimeBy(10_000L)
        runCurrent()
        assertTrue(repo.saveCalls.isEmpty())

        vm.togglePlayPause()
        runCurrent()
        assertTrue(repo.saveCalls.isEmpty())

        fakeController.emitEvent(MpvEvent.EndFile(MpvNative.END_FILE_REASON_EOF, 0))
        runCurrent()
        assertTrue(repo.saveCalls.isEmpty())

        val onClearedMethod = PlayerViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(vm)
        runCurrent()
        assertTrue(repo.saveCalls.isEmpty())
    }

    @Test
    fun `loadMedia applies hwdec, default speed, subtitle scale, alang, and slang from settings`() = runTest {
        val settingsRepo = InMemorySettingsRepository(
            UserPreferences(
                hwDecMode = HwDecMode.MEDIACODEC,
                defaultPlaybackSpeed = 1.5f,
                subtitleFontScale = 1.4f,
                preferredAudioLang = "jpn,eng",
                preferredSubtitleLang = "eng",
            ),
        )
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            settingsRepository = settingsRepo,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadMedia(uri = "file:///anime.mkv", title = "Anime")
        runCurrent()

        assertEquals(HwDecMode.MEDIACODEC, fakeController.lastHwDecMode)
        assertEquals(HwDecMode.MEDIACODEC, vm.uiState.value.hwDecMode)
        assertEquals(1.5f, fakeController.lastSpeed, 0.001f)
        assertEquals(1.5f, vm.uiState.value.speed, 0.001f)
        assertEquals(1.4, fakeController.lastSubScale, 0.001)
        assertEquals(1.4, vm.uiState.value.subScale, 0.001)
        assertEquals("jpn,eng", fakeController.properties["alang"])
        assertEquals("eng", fakeController.properties["slang"])

        // Clearing preferred langs clears previously applied alang/slang on next load
        settingsRepo.setPreferredAudioLang("")
        settingsRepo.setPreferredSubtitleLang("")
        vm.loadMedia(uri = "file:///movie2.mkv", title = "Movie 2")
        runCurrent()

        assertEquals("", fakeController.properties["alang"])
        assertEquals("", fakeController.properties["slang"])
    }

    @Test
    fun `onSurfaceSizeChanged forwards positive dimensions and ignores non-positive dimensions`() = runTest {
        viewModel.loadMedia(uri = "file:///movie.mkv", title = "Movie")
        runCurrent()

        viewModel.onSurfaceSizeChanged(0, 1080)
        viewModel.onSurfaceSizeChanged(1080, 0)
        viewModel.onSurfaceSizeChanged(-2400, 1080)
        viewModel.onSurfaceSizeChanged(2400, -1080)
        assertTrue(fakeController.surfaceSizeCalls.isEmpty())

        viewModel.onSurfaceSizeChanged(2400, 1080)
        assertEquals(listOf(2400 to 1080), fakeController.surfaceSizeCalls)
    }

    @Test
    fun `attachSurface and onSurfaceSizeChanged replay pending surface and size after initialization`() = runTest {
        val surface = allocateTestSurface()
        viewModel.attachSurface(surface)
        viewModel.onSurfaceSizeChanged(2400, 1080)

        // Engine not initialized yet: buffered without calling playbackController.
        assertTrue(fakeController.attachedSurfaces.isEmpty())
        assertTrue(fakeController.surfaceSizeCalls.isEmpty())

        viewModel.loadMedia(uri = "file:///movie.mkv", title = "Movie")
        runCurrent()

        assertEquals(listOf(surface), fakeController.attachedSurfaces)
        assertEquals(listOf(2400 to 1080), fakeController.surfaceSizeCalls)
    }

    @Test
    fun `detachSurface clears pending surface and size so stale dimensions are not replayed`() = runTest {
        val surface = allocateTestSurface()
        viewModel.attachSurface(surface)
        viewModel.onSurfaceSizeChanged(1080, 2400)
        viewModel.detachSurface()

        viewModel.loadMedia(uri = "file:///movie.mkv", title = "Movie")
        runCurrent()

        assertTrue(fakeController.attachedSurfaces.isEmpty())
        assertTrue(fakeController.surfaceSizeCalls.isEmpty())
        assertEquals(0, fakeController.detachSurfaceCount)

        // Once initialized, detachSurface forwards to controller and clears pending dimensions.
        viewModel.attachSurface(surface)
        viewModel.onSurfaceSizeChanged(2400, 1080)
        viewModel.detachSurface()
        assertEquals(1, fakeController.detachSurfaceCount)
        assertEquals(listOf(2400 to 1080), fakeController.surfaceSizeCalls)
    }

    @Test
    fun `EndFile STOP during file replacement does not wipe progress and stale zero position does not clobber resumeMs`() = runTest {
        val uriA = "file:///video-a.mkv"
        val uriB = "file:///video-b.mkv"
        val repo = FakeMediaRepository()
        repo.seedHistory(
            MediaHistoryEntity(
                uriHash = UriHash.of(uriB),
                uri = uriB,
                displayName = "Video B",
                durationMs = 120_000L,
                positionMs = 40_000L,
                lastPlayedEpochMs = 1_000L,
            ),
        )
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            mediaRepository = repo,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadMedia(uri = uriA, title = "Video A")
        runCurrent()
        fakeController.emitEvent(MpvEvent.FileLoaded)
        fakeController.emitState { it.copy(durationMs = 60_000L, positionMs = 15_000L, isPaused = false) }
        runCurrent()

        // Switch to Video B while Video A was playing: mpv emits EndFile(STOP) and StartFile(pos=0, dur=0)
        vm.loadMedia(uri = uriB, title = "Video B")
        runCurrent()
        fakeController.emitEvent(MpvEvent.EndFile(MpvNative.END_FILE_REASON_STOP, 0))
        fakeController.emitEvent(MpvEvent.StartFile)
        fakeController.emitState { it.copy(positionMs = 0L, durationMs = 0L) }
        runCurrent()

        // Video B resume position must NOT be wiped in repo or clobbered in positionMs
        assertEquals(40_000L, vm.positionMs.value)
        assertEquals(40_000L, repo.getHistory(uriB)?.positionMs)
        assertEquals(15_000L, repo.getHistory(uriA)?.positionMs)

        // Even after FileLoaded + duration before time-pos arrives, stale 0 position must not overwrite resumeMs
        fakeController.emitEvent(MpvEvent.FileLoaded)
        fakeController.emitState { it.copy(durationMs = 120_000L, positionMs = 0L) }
        runCurrent()
        assertEquals(40_000L, vm.positionMs.value)

        // Real playback updates resume tracking cleanly
        fakeController.emitState { it.copy(positionMs = 42_000L) }
        runCurrent()
        assertEquals(42_000L, vm.positionMs.value)

        vm.pauseIfPlaying()
        runCurrent()
    }

    @Test
    fun `prepareForExit saves progress immediately restores speed pauses stops service and is idempotent with onCleared`() = runTest {
        val uri = "file:///episode.mkv"
        val repo = FakeMediaRepository()
        val focusController = FakeAudioFocusController()
        val serviceLauncher = FakePlaybackServiceLauncher()
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            mediaRepository = repo,
            audioFocusController = focusController,
            playbackServiceLauncher = serviceLauncher,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadMedia(uri = uri, title = "Episode")
        fakeController.emitEvent(MpvEvent.FileLoaded)
        fakeController.emitState { it.copy(durationMs = 120_000L, positionMs = 18_500L, isPaused = false) }
        runCurrent()

        vm.setSpeed(1.25f)
        vm.onLongPressSpeedStart()
        runCurrent()
        assertEquals(2.0f, fakeController.lastSpeed, 0.001f)

        vm.prepareForExit()
        runCurrent()

        assertEquals(1, repo.saveCalls.size)
        assertEquals(18_500L, repo.getHistory(uri)?.positionMs)
        assertEquals(1.25f, fakeController.lastSpeed, 0.001f)
        assertTrue(fakeController.state.value.isPaused)
        assertEquals(1, focusController.abandonCount)
        assertEquals(1, serviceLauncher.stopCount)

        // Subsequent prepareForExit() and onCleared() must be idempotent no-ops
        vm.prepareForExit()
        val onClearedMethod = PlayerViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(vm)
        runCurrent()

        assertEquals(1, repo.saveCalls.size)
        assertEquals(1, focusController.abandonCount)
        assertEquals(1, serviceLauncher.stopCount)
    }

    @Test
    fun `autoPlayNext counts down 5s on EndFile EOF and advances queue`() = runTest {
        val queueManager = PlaybackQueueManager()
        val items = listOf(
            QueueMediaItem(uri = "file:///ep1.mkv", title = "Episode 1"),
            QueueMediaItem(uri = "file:///ep2.mkv", title = "Episode 2"),
        )
        queueManager.setQueue(items, startIndex = 0, sourceTitle = "Season 1", sourceKind = QueueSourceKind.FOLDER)
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            playbackQueueManager = queueManager,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadInitialMediaIfNeeded(uri = "file:///ep1.mkv", title = "Episode 1")
        runCurrent()
        fakeController.emitEvent(MpvEvent.FileLoaded)
        fakeController.emitState { it.copy(durationMs = 60_000L, positionMs = 60_000L, isPaused = false) }
        runCurrent()

        fakeController.emitEvent(MpvEvent.EndFile(MpvNative.END_FILE_REASON_EOF, 0))
        runCurrent()

        assertEquals(items[1], vm.uiState.value.upNextItem)
        assertEquals(5, vm.uiState.value.upNextCountdownSec)

        for (expectedSec in 4 downTo 1) {
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(expectedSec, vm.uiState.value.upNextCountdownSec)
            assertEquals(items[1], vm.uiState.value.upNextItem)
        }

        advanceTimeBy(1_000L)
        runCurrent()

        assertNull(vm.uiState.value.upNextItem)
        assertEquals(0, vm.uiState.value.upNextCountdownSec)
        assertEquals(1, vm.uiState.value.queueIndex)
        assertEquals("file:///ep2.mkv", fakeController.lastLoadedUri)
        assertEquals("Episode 2", vm.uiState.value.title)

        // Simulating screen rotation after queue advance must NOT reload Episode 1
        vm.loadInitialMediaIfNeeded(uri = "file:///ep1.mkv", title = "Episode 1")
        runCurrent()
        assertEquals("file:///ep2.mkv", fakeController.lastLoadedUri)
        assertEquals(1, vm.uiState.value.queueIndex)

        vm.prepareForExit()
        runCurrent()
    }

    @Test
    fun `cancelUpNext, seekTo, and onScrubStart cancel countdown and prevent auto-advance`() = runTest {
        val queueManager = PlaybackQueueManager()
        val items = listOf(
            QueueMediaItem(uri = "file:///a.mkv", title = "A"),
            QueueMediaItem(uri = "file:///b.mkv", title = "B"),
        )
        queueManager.setQueue(items, startIndex = 0, sourceTitle = "List", sourceKind = QueueSourceKind.PLAYLIST)
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            playbackQueueManager = queueManager,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadMedia(uri = "file:///a.mkv", title = "A")
        runCurrent()
        fakeController.emitEvent(MpvEvent.FileLoaded)
        fakeController.emitState { it.copy(durationMs = 60_000L, positionMs = 60_000L) }
        runCurrent()

        // 1. Cancel via cancelUpNext()
        fakeController.emitEvent(MpvEvent.EndFile(MpvNative.END_FILE_REASON_EOF, 0))
        runCurrent()
        assertEquals(5, vm.uiState.value.upNextCountdownSec)
        vm.cancelUpNext()
        runCurrent()
        assertNull(vm.uiState.value.upNextItem)
        assertEquals(0, vm.uiState.value.upNextCountdownSec)
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals("file:///a.mkv", fakeController.lastLoadedUri)

        // 2. Cancel via seekTo()
        fakeController.emitEvent(MpvEvent.EndFile(MpvNative.END_FILE_REASON_EOF, 0))
        runCurrent()
        assertEquals(5, vm.uiState.value.upNextCountdownSec)
        vm.seekTo(20_000L)
        runCurrent()
        assertNull(vm.uiState.value.upNextItem)
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals("file:///a.mkv", fakeController.lastLoadedUri)

        // 3. Cancel via onScrubStart()
        fakeController.emitEvent(MpvEvent.EndFile(MpvNative.END_FILE_REASON_EOF, 0))
        runCurrent()
        assertEquals(5, vm.uiState.value.upNextCountdownSec)
        vm.onScrubStart()
        runCurrent()
        assertNull(vm.uiState.value.upNextItem)
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals("file:///a.mkv", fakeController.lastLoadedUri)

        vm.prepareForExit()
        runCurrent()
    }

    @Test
    fun `disabled autoPlayNext skips countdown and setAutoPlayNext updates preference`() = runTest {
        val settingsRepo = InMemorySettingsRepository(UserPreferences(autoPlayNext = false))
        val queueManager = PlaybackQueueManager()
        queueManager.setQueue(
            listOf(
                QueueMediaItem(uri = "file:///1.mkv", title = "One"),
                QueueMediaItem(uri = "file:///2.mkv", title = "Two"),
            ),
            startIndex = 0,
            sourceTitle = "Queue",
            sourceKind = QueueSourceKind.PLAYLIST,
        )
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            settingsRepository = settingsRepo,
            playbackQueueManager = queueManager,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadMedia(uri = "file:///1.mkv", title = "One")
        runCurrent()
        fakeController.emitEvent(MpvEvent.FileLoaded)
        fakeController.emitState { it.copy(durationMs = 30_000L, positionMs = 30_000L) }
        runCurrent()

        assertFalse(vm.uiState.value.autoPlayNext)
        fakeController.emitEvent(MpvEvent.EndFile(MpvNative.END_FILE_REASON_EOF, 0))
        runCurrent()
        assertNull(vm.uiState.value.upNextItem)
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals("file:///1.mkv", fakeController.lastLoadedUri)

        vm.setAutoPlayNext(true)
        runCurrent()
        assertTrue(vm.uiState.value.autoPlayNext)
        assertTrue(settingsRepo.current.autoPlayNext)

        vm.prepareForExit()
        runCurrent()
    }

    @Test
    fun `manual queue controls playNextNow playPrevious and playQueueIndex update playback and state`() = runTest {
        val queueManager = PlaybackQueueManager()
        val items = listOf(
            QueueMediaItem(uri = "file:///one.mkv", title = "One"),
            QueueMediaItem(uri = "file:///two.mkv", title = "Two"),
            QueueMediaItem(uri = "file:///three.mkv", title = "Three"),
        )
        queueManager.setQueue(items, startIndex = 0, sourceTitle = "Movies", sourceKind = QueueSourceKind.FOLDER)
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            playbackQueueManager = queueManager,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadMedia(uri = "file:///one.mkv", title = "One")
        runCurrent()
        assertTrue(vm.uiState.value.hasQueue)
        assertFalse(vm.uiState.value.hasPreviousInQueue)
        assertTrue(vm.uiState.value.hasNextInQueue)
        assertEquals("Movies", vm.uiState.value.queueSourceTitle)

        vm.playNextNow()
        runCurrent()
        assertEquals(1, vm.uiState.value.queueIndex)
        assertEquals("file:///two.mkv", fakeController.lastLoadedUri)
        assertTrue(vm.uiState.value.hasPreviousInQueue)
        assertTrue(vm.uiState.value.hasNextInQueue)

        vm.playQueueIndex(2)
        runCurrent()
        assertEquals(2, vm.uiState.value.queueIndex)
        assertEquals("file:///three.mkv", fakeController.lastLoadedUri)
        assertFalse(vm.uiState.value.hasNextInQueue)

        vm.playPrevious()
        runCurrent()
        assertEquals(1, vm.uiState.value.queueIndex)
        assertEquals("file:///two.mkv", fakeController.lastLoadedUri)

        vm.prepareForExit()
        runCurrent()
    }

    @Test
    fun `bookmarks add rename delete jump and switch dynamically across queue tracks`() = runTest {
        val bookmarkRepo = InMemoryVideoBookmarkRepository()
        val queueManager = PlaybackQueueManager()
        val items = listOf(
            QueueMediaItem(uri = "file:///movie1.mkv", title = "Movie 1"),
            QueueMediaItem(uri = "file:///movie2.mkv", title = "Movie 2"),
        )
        queueManager.setQueue(items, startIndex = 0, sourceTitle = "Watchlist", sourceKind = QueueSourceKind.PLAYLIST)
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            playbackQueueManager = queueManager,
            videoBookmarkRepository = bookmarkRepo,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadMedia(uri = "file:///movie1.mkv", title = "Movie 1")
        fakeController.emitEvent(MpvEvent.FileLoaded)
        fakeController.emitState { it.copy(durationMs = 120_000L, positionMs = 42_000L) }
        runCurrent()

        // Blank label defaults to "Bookmark at 00:42" and triggers status message
        vm.addBookmark()
        runCurrent()
        assertEquals(1, vm.uiState.value.bookmarks.size)
        val firstBookmark = vm.uiState.value.bookmarks.first()
        assertEquals("Bookmark at 00:42", firstBookmark.label)
        assertEquals(42_000L, firstBookmark.positionMs)
        assertEquals("Bookmark saved at 00:42", vm.uiState.value.statusMessage)

        advanceTimeBy(2_500L)
        runCurrent()
        assertNull(vm.uiState.value.statusMessage)

        // Custom trimmed label + rename + jump + delete
        fakeController.emitState { it.copy(positionMs = 80_000L) }
        runCurrent()
        vm.addBookmark("  Climax  ")
        runCurrent()
        assertEquals(2, vm.uiState.value.bookmarks.size)
        val secondBookmark = vm.uiState.value.bookmarks[1]
        assertEquals("Climax", secondBookmark.label)

        vm.renameBookmark(secondBookmark.id, "  Final act  ")
        runCurrent()
        assertEquals("Final act", vm.uiState.value.bookmarks[1].label)

        vm.jumpToBookmark(firstBookmark)
        runCurrent()
        assertEquals(42_000L, fakeController.lastSeekToMs)

        vm.deleteBookmark(firstBookmark.id)
        runCurrent()
        assertEquals(1, vm.uiState.value.bookmarks.size)

        // Advancing to track 2 switches observed bookmarks dynamically
        vm.playNextNow()
        runCurrent()
        assertTrue(vm.uiState.value.bookmarks.isEmpty())

        fakeController.emitEvent(MpvEvent.FileLoaded)
        fakeController.emitState { it.copy(durationMs = 90_000L, positionMs = 15_000L) }
        runCurrent()
        vm.addBookmark("Track 2 intro")
        runCurrent()
        assertEquals(1, vm.uiState.value.bookmarks.size)
        assertEquals("Track 2 intro", vm.uiState.value.bookmarks.first().label)

        vm.prepareForExit()
        runCurrent()
    }

    @Test
    fun `captureFrame guards re-entrancy and reports Saved and Failed status messages`() = runTest {
        val gate = CompletableDeferred<FrameCaptureResult>()
        var callCount = 0
        var nextResult: FrameCaptureResult? = null
        var lastSurfaceView: SurfaceView? = null
        var lastVideoWidth = 0
        var lastVideoHeight = 0
        var lastCropLetterbox = false
        val fakeCaptureManager = object : FrameCaptureManager {
            override suspend fun captureCurrentFrame(
                title: String,
                surfaceView: SurfaceView?,
                videoWidth: Int,
                videoHeight: Int,
                cropLetterbox: Boolean,
            ): FrameCaptureResult {
                callCount++
                lastSurfaceView = surfaceView
                lastVideoWidth = videoWidth
                lastVideoHeight = videoHeight
                lastCropLetterbox = cropLetterbox
                return nextResult ?: gate.await()
            }
        }
        val vm = PlayerViewModel(
            playbackController = fakeController,
            configDir = "/tmp/jupiter-mpv-test",
            frameCaptureManager = fakeCaptureManager,
            persistenceDispatcher = testDispatcher,
        )

        vm.loadMedia(uri = "file:///movie.mkv", title = "Movie")
        fakeController.emitState { it.copy(videoWidth = 1920, videoHeight = 1080) }
        runCurrent()

        val testSurfaceView = allocateTestSurfaceView()
        vm.captureFrame(testSurfaceView)
        runCurrent()
        assertTrue(vm.uiState.value.isCapturingFrame)
        assertEquals(testSurfaceView, lastSurfaceView)
        assertEquals(1920, lastVideoWidth)
        assertEquals(1080, lastVideoHeight)
        assertTrue(lastCropLetterbox)
        // Re-entrant call while capturing must be ignored
        vm.captureFrame(testSurfaceView)
        runCurrent()
        assertEquals(1, callCount)

        gate.complete(FrameCaptureResult.Saved("Jupiter_Movie_1.png", "Pictures/Jupiter"))
        runCurrent()
        assertFalse(vm.uiState.value.isCapturingFrame)
        assertEquals("Frame saved to Pictures/Jupiter", vm.uiState.value.statusMessage)

        vm.dismissStatusMessage()
        runCurrent()
        assertNull(vm.uiState.value.statusMessage)

        vm.setAspectMode(AspectMode.FILL)
        nextResult = FrameCaptureResult.Failed("Failed to capture current video frame")
        vm.captureFrame()
        runCurrent()
        assertFalse(vm.uiState.value.isCapturingFrame)
        assertNull(lastSurfaceView)
        assertFalse(lastCropLetterbox)
        assertEquals("Failed to capture current video frame", vm.uiState.value.statusMessage)

        vm.prepareForExit()
        runCurrent()
    }

    private fun allocateTestSurface(): Surface = allocateTestInstance(Surface::class.java)

    private fun allocateTestSurfaceView(): SurfaceView = allocateTestInstance(SurfaceView::class.java)

    private fun <T> allocateTestInstance(clazz: Class<T>): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        @Suppress("UNCHECKED_CAST")
        return allocateMethod.invoke(unsafe, clazz) as T
    }

    private class FakeAudioFocusController : AudioFocusController {
        var requestCount = 0
        var abandonCount = 0
        var shouldGrant = true
        private var lastOnPause: (() -> Unit)? = null
        private var lastOnResume: (() -> Unit)? = null
        private var lastOnVolumeChange: ((Int) -> Unit)? = null

        override fun requestFocus(
            onPause: () -> Unit,
            onResume: () -> Unit,
            onVolumeChange: (Int) -> Unit,
        ): Boolean {
            requestCount++
            lastOnPause = onPause
            lastOnResume = onResume
            lastOnVolumeChange = onVolumeChange
            return shouldGrant
        }

        override fun abandonFocus() {
            abandonCount++
        }

        fun triggerPause() {
            lastOnPause?.invoke()
        }

        fun triggerResume() {
            lastOnResume?.invoke()
        }

        fun triggerVolumeChange(percent: Int) {
            lastOnVolumeChange?.invoke(percent)
        }
    }

    private class FakePlaybackServiceLauncher : PlaybackServiceLauncher {
        var startOrUpdateCount = 0
        var stopCount = 0

        override fun startOrUpdate() {
            startOrUpdateCount++
        }

        override fun stop() {
            stopCount++
        }
    }

    private fun interface FakeUriResolver : UriResolver {
        override fun resolveToMpvPath(uriString: String, fallbackTitle: String): Result<MpvSource>
    }

    private class FakeMediaRepository : MediaRepository {
        private val history = MutableStateFlow<Map<String, MediaHistoryEntity>>(emptyMap())
        val saveCalls = mutableListOf<Long>()

        fun seedHistory(entity: MediaHistoryEntity) {
            history.value = history.value + (entity.uriHash to entity)
        }

        override suspend fun scanLocalVideos(): List<LocalVideoItem> = emptyList()

        override fun observeContinueWatching(limit: Int): Flow<List<MediaHistoryEntity>> =
            history.map { map ->
                map.values.filter { it.positionMs > 0L }
                    .sortedByDescending { it.lastPlayedEpochMs }
                    .take(limit)
            }

        override suspend fun getHistory(uri: String): MediaHistoryEntity? =
            history.value[UriHash.of(uri)]

        override suspend fun saveProgress(
            uri: String,
            displayName: String,
            positionMs: Long,
            durationMs: Long,
            playbackSpeed: Float,
            isNetworkStream: Boolean,
        ) {
            val normalized = ResumeRules.normalizePositionMs(positionMs, durationMs)
            saveCalls += normalized
            val hash = UriHash.of(uri)
            history.value = history.value + (
                hash to MediaHistoryEntity(
                    uriHash = hash,
                    uri = uri,
                    displayName = displayName,
                    durationMs = durationMs,
                    positionMs = normalized,
                    lastPlayedEpochMs = saveCalls.size.toLong(),
                    playbackSpeed = playbackSpeed,
                    isNetworkStream = isNetworkStream,
                )
            )
        }

        override suspend fun removeHistory(uri: String) {
            history.value = history.value - UriHash.of(uri)
        }

        override suspend fun clearHistory() {
            history.value = emptyMap()
        }
    }

    private class FakePlaybackController : PlaybackController {
        private val _state = MutableStateFlow(PlaybackState())
        override val state: StateFlow<PlaybackState> = _state.asStateFlow()

        private val _events = MutableSharedFlow<MpvEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<MpvEvent> = _events.asSharedFlow()

        var initializeCount = 0
        val attachedSurfaces = mutableListOf<Surface>()
        val surfaceSizeCalls = mutableListOf<Pair<Int, Int>>()
        var detachSurfaceCount = 0
        var lastLoadedUri: String? = null
        var lastLoadOptions: List<String> = emptyList()
        var lastSeekToMs: Long? = null
        val seekByHistory = mutableListOf<Double>()
        var lastSpeed: Float = 1.0f
        var lastAspectOverride: String = "-1"
        val properties = mutableMapOf<String, String>()
        val options = mutableMapOf<String, String>()
        val headerCalls = mutableListOf<HeaderCall>()
        var lastAudioTrackId: Int? = -1
        var lastSubtitleTrackId: Int? = -1
        var lastSecondarySubtitleTrackId: Int? = -1
        val addedExternalSubtitles = mutableListOf<String>()
        var lastSubDelaySec: Double = 0.0
        var lastAudioDelaySec: Double = 0.0
        var lastSubScale: Double = 1.0
        var lastHwDecMode: HwDecMode = HwDecMode.AUTO
        var lastSelectedChapter: Int = -1
        var lastAbLoopA: Double? = null
        var lastAbLoopB: Double? = null
        var clearAbLoopCount: Int = 0
        val equalizerCalls = mutableListOf<Pair<String, Int>>()
        var lastVolumePercent: Int? = null

        data class HeaderCall(
            val userAgent: String?,
            val referer: String?,
            val headers: Map<String, String>,
        )

        fun emitState(transform: (PlaybackState) -> PlaybackState) {
            _state.update(transform)
        }

        fun emitEvent(event: MpvEvent) {
            _events.tryEmit(event)
        }

        override fun initialize(configDir: String, extraOptions: Map<String, String>) {
            initializeCount++
            _state.update { it.copy(lifecycleState = EngineLifecycleState.INITIALIZED) }
        }

        override fun attachSurface(surface: Surface): Int {
            attachedSurfaces += surface
            return 0
        }

        override fun setSurfaceSize(width: Int, height: Int): Int {
            surfaceSizeCalls += width to height
            return 0
        }

        override fun detachSurface(): Int {
            detachSurfaceCount++
            return 0
        }

        override fun loadFile(uriOrFd: String, options: List<String>): Int {
            lastLoadedUri = uriOrFd
            lastLoadOptions = options
            return 0
        }

        override fun play(): Int {
            _state.update { it.copy(isPaused = false) }
            return 0
        }

        override fun pause(): Int {
            _state.update { it.copy(isPaused = true) }
            return 0
        }

        override fun togglePlayPause(): Int {
            _state.update { it.copy(isPaused = !it.isPaused) }
            return 0
        }

        override fun seekTo(ms: Long): Int {
            lastSeekToMs = ms
            _state.update { it.copy(positionMs = ms) }
            return 0
        }

        override fun seekBy(deltaSec: Double): Int {
            seekByHistory += deltaSec
            return 0
        }

        override fun setSpeed(speed: Float): Int {
            lastSpeed = speed
            _state.update { it.copy(speed = speed) }
            return 0
        }

        override fun setVideoZoom(zoom: Double): Int {
            _state.update { it.copy(videoZoom = zoom) }
            return 0
        }

        override fun setAspectOverride(aspect: String): Int {
            lastAspectOverride = aspect
            _state.update { it.copy(aspectOverride = aspect) }
            return 0
        }

        override fun setOption(name: String, value: String): Int {
            options[name] = value
            return 0
        }

        override fun setProperty(name: String, value: String): Int {
            properties[name] = value
            return 0
        }

        override fun setNetworkHeaders(
            userAgent: String?,
            referer: String?,
            headers: Map<String, String>,
        ) {
            headerCalls += HeaderCall(userAgent, referer, headers)
        }

        override fun selectAudioTrack(id: Int?): Int {
            lastAudioTrackId = id
            return 0
        }

        override fun selectSubtitleTrack(id: Int?): Int {
            lastSubtitleTrackId = id
            return 0
        }

        override fun selectSecondarySubtitleTrack(id: Int?): Int {
            lastSecondarySubtitleTrackId = id
            return 0
        }

        override fun addExternalSubtitle(fdPath: String): Int {
            addedExternalSubtitles += fdPath
            return 0
        }

        override fun setSubtitleDelay(sec: Double): Int {
            lastSubDelaySec = sec
            _state.update { it.copy(subDelaySec = sec) }
            return 0
        }

        override fun setAudioDelay(sec: Double): Int {
            lastAudioDelaySec = sec
            _state.update { it.copy(audioDelaySec = sec) }
            return 0
        }

        override fun setSubtitleScale(scale: Double): Int {
            lastSubScale = scale
            return 0
        }

        override fun setHwDec(mode: HwDecMode): Int {
            lastHwDecMode = mode
            _state.update { it.copy(hwDecMode = mode) }
            return 0
        }

        override fun selectChapter(index: Int): Int {
            lastSelectedChapter = index
            _state.update { it.copy(currentChapter = index) }
            return 0
        }

        override fun setAbLoopA(seconds: Double?): Int {
            lastAbLoopA = seconds
            return 0
        }

        override fun setAbLoopB(seconds: Double?): Int {
            lastAbLoopB = seconds
            return 0
        }

        override fun clearAbLoop(): Int {
            clearAbLoopCount++
            lastAbLoopA = null
            lastAbLoopB = null
            return 0
        }

        override fun setEqualizerProperty(name: String, value: Int): Int {
            equalizerCalls += name to value
            return 0
        }

        override fun setVolume(percent: Int): Int {
            lastVolumePercent = percent
            return 0
        }
    }
}
