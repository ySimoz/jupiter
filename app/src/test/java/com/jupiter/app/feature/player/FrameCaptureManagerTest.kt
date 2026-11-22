package com.jupiter.app.feature.player

import android.view.Surface
import android.view.SurfaceView
import com.jupiter.app.core.di.PlaybackController
import com.jupiter.engine.HwDecMode
import com.jupiter.engine.MpvEvent
import com.jupiter.engine.PlaybackState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class FrameCaptureManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `NoOpFrameCaptureManager returns failed result`() = runTest {
        val result = NoOpFrameCaptureManager.captureCurrentFrame("Sample")
        assertEquals(FrameCaptureResult.Failed("Frame capture unavailable"), result)
    }

    @Test
    fun `buildCaptureFileName sanitizes special chars blanks and long titles`() {
        val dispatcher = StandardTestDispatcher()
        val manager = AndroidFrameCaptureManager(
            playbackController = FakeScreenshotController(),
            ioDispatcher = dispatcher,
            cacheDirProvider = { tempFolder.root },
            saveToMediaStore = { _, name -> FrameCaptureResult.Saved(name, "Pictures/Jupiter") },
            zoneId = ZoneOffset.UTC,
        )
        val epochMs = Instant.parse("2026-11-22T14:33:45Z").toEpochMilli()

        assertEquals(
            "Jupiter_Big_Buck_Bunny_2024_mp4_20261122_143345.png",
            manager.buildCaptureFileName("Big Buck Bunny (2024)!.mp4", epochMs),
        )
        assertEquals(
            "Jupiter_Video_20261122_143345.png",
            manager.buildCaptureFileName("   ", epochMs),
        )
        assertEquals(
            "Jupiter_Video_20261122_143345.png",
            manager.buildCaptureFileName("!!!___???", epochMs),
        )
        assertEquals(
            "Jupiter_Movie_Title_20261122_143345.png",
            manager.buildCaptureFileName("___Movie---Title___", epochMs),
        )

        val longTitle = "A".repeat(38) + "_Tail"
        val longResult = manager.buildCaptureFileName(longTitle, epochMs)
        assertEquals(
            "Jupiter_${"A".repeat(38)}_T_20261122_143345.png",
            longResult,
        )

        val boundaryUnderscore = "A".repeat(39) + " B"
        val boundaryResult = manager.buildCaptureFileName(boundaryUnderscore, epochMs)
        assertEquals(
            "Jupiter_${"A".repeat(39)}_20261122_143345.png",
            boundaryResult,
        )
    }

    @Test
    fun `captureToTempFile succeeds with subtitles without falling back to video`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = FakeScreenshotController { path, mode ->
            assertEquals("subtitles", mode)
            File(path).writeBytes(byteArrayOf(1, 2, 3, 4))
            0
        }
        val manager = createManager(controller, dispatcher)
        val target = tempFolder.newFile("sub_ok.png")

        val ok = manager.captureToTempFile(target)

        assertTrue(ok)
        assertEquals(listOf("subtitles"), controller.modesCalled)
    }

    @Test
    fun `captureToTempFile falls back to video when subtitles fails or writes empty file`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controllerOnError = FakeScreenshotController { path, mode ->
            when (mode) {
                "subtitles" -> -1
                "video" -> {
                    File(path).writeBytes(byteArrayOf(9, 8, 7))
                    0
                }
                else -> -1
            }
        }
        val managerOnError = createManager(controllerOnError, dispatcher)
        val target1 = tempFolder.newFile("fallback_rc.png")

        assertTrue(managerOnError.captureToTempFile(target1))
        assertEquals(listOf("subtitles", "video"), controllerOnError.modesCalled)

        val controllerOnEmpty = FakeScreenshotController { path, mode ->
            when (mode) {
                "subtitles" -> 0 // succeeds rc but writes 0 bytes
                "video" -> {
                    File(path).writeBytes(byteArrayOf(5, 6))
                    0
                }
                else -> -1
            }
        }
        val managerOnEmpty = createManager(controllerOnEmpty, dispatcher)
        val target2 = tempFolder.newFile("fallback_empty.png")

        assertTrue(managerOnEmpty.captureToTempFile(target2))
        assertEquals(listOf("subtitles", "video"), controllerOnEmpty.modesCalled)
    }

    @Test
    fun `captureToTempFile returns false when both modes fail or leave empty file`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val bothFail = FakeScreenshotController { _, _ -> -1 }
        val target1 = tempFolder.newFile("both_fail.png")

        assertFalse(createManager(bothFail, dispatcher).captureToTempFile(target1))
        assertEquals(listOf("subtitles", "video"), bothFail.modesCalled)

        val bothEmpty = FakeScreenshotController { _, _ -> 0 }
        val target2 = tempFolder.newFile("both_empty.png")

        assertFalse(createManager(bothEmpty, dispatcher).captureToTempFile(target2))
        assertEquals(listOf("subtitles", "video"), bothEmpty.modesCalled)
    }

    @Test
    fun `captureCurrentFrame saves png and always cleans up temp file`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cacheDir = tempFolder.newFolder("cache")
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        var savedBytes: ByteArray? = null

        val controller = FakeScreenshotController { path, mode ->
            if (mode == "subtitles") {
                File(path).writeBytes(pngBytes)
                0
            } else {
                -1
            }
        }
        val manager = AndroidFrameCaptureManager(
            playbackController = controller,
            ioDispatcher = dispatcher,
            cacheDirProvider = { cacheDir },
            saveToMediaStore = { file, displayName ->
                savedBytes = file.readBytes()
                FrameCaptureResult.Saved(displayName, "Pictures/Jupiter")
            },
            nowMs = { Instant.parse("2026-11-22T14:33:45Z").toEpochMilli() },
            zoneId = ZoneOffset.UTC,
        )

        val result = manager.captureCurrentFrame("My Clip")

        assertEquals(
            FrameCaptureResult.Saved(
                displayName = "Jupiter_My_Clip_20261122_143345.png",
                relativePath = "Pictures/Jupiter",
            ),
            result,
        )
        assertArrayEquals(pngBytes, savedBytes)
        assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `captureCurrentFrame cleans up temp file on capture failure`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cacheDir = tempFolder.newFolder("cache_fail")
        var mediaStoreCalled = false
        val controller = FakeScreenshotController { _, _ -> -1 }
        val manager = AndroidFrameCaptureManager(
            playbackController = controller,
            ioDispatcher = dispatcher,
            cacheDirProvider = { cacheDir },
            saveToMediaStore = { _, displayName ->
                mediaStoreCalled = true
                FrameCaptureResult.Saved(displayName, "Pictures/Jupiter")
            },
        )

        val result = manager.captureCurrentFrame("Clip")

        assertTrue(result is FrameCaptureResult.Failed)
        assertFalse(mediaStoreCalled)
        assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `captureCurrentFrame deletes pre-existing temp file before mpv screenshot calls`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cacheDir = tempFolder.newFolder("cache_deleted")
        val seenExistingBeforeSubtitles = mutableListOf<Boolean>()
        val seenExistingBeforeVideo = mutableListOf<Boolean>()
        val controller = FakeScreenshotController { path, mode ->
            val existsAtCall = File(path).exists()
            if (mode == "subtitles") {
                seenExistingBeforeSubtitles += existsAtCall
                -1
            } else {
                seenExistingBeforeVideo += existsAtCall
                File(path).writeBytes(byteArrayOf(1, 2, 3))
                0
            }
        }
        val manager = AndroidFrameCaptureManager(
            playbackController = controller,
            ioDispatcher = dispatcher,
            cacheDirProvider = { cacheDir },
            saveToMediaStore = { _, displayName -> FrameCaptureResult.Saved(displayName, "Pictures/Jupiter") },
        )

        val result = manager.captureCurrentFrame("Clip")

        assertTrue(result is FrameCaptureResult.Saved)
        assertEquals(listOf(false), seenExistingBeforeSubtitles)
        assertEquals(listOf(false), seenExistingBeforeVideo)
        assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `computeLetterboxCropBounds handles landscape in portrait, portrait in landscape, and exact match`() {
        val landscapeInPortrait = computeLetterboxCropBounds(
            surfaceWidth = 1080,
            surfaceHeight = 2400,
            videoWidth = 1920,
            videoHeight = 1080,
        )
        assertEquals(CropBounds(left = 0, top = 896, right = 1080, bottom = 1504), landscapeInPortrait)

        val portraitInLandscape = computeLetterboxCropBounds(
            surfaceWidth = 2400,
            surfaceHeight = 1080,
            videoWidth = 1080,
            videoHeight = 1920,
        )
        assertEquals(CropBounds(left = 896, top = 0, right = 1504, bottom = 1080), portraitInLandscape)

        assertNull(computeLetterboxCropBounds(1920, 1080, 1920, 1080))
        assertNull(computeLetterboxCropBounds(1920, 1081, 1920, 1080))
        assertNull(computeLetterboxCropBounds(0, 1080, 1920, 1080))
        assertNull(computeLetterboxCropBounds(1920, 1080, 0, 1080))
    }

    @Test
    fun `captureCurrentFrame falls back to PixelCopy provider when mpv screenshot fails`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cacheDir = tempFolder.newFolder("cache_pixelcopy")
        val controller = FakeScreenshotController { _, _ -> -1 }
        val fakeSurfaceView = allocateTestSurfaceView()
        var capturedSurfaceView: SurfaceView? = null
        var capturedVideoWidth = 0
        var capturedVideoHeight = 0
        var capturedCrop = false
        var capturedDisplayName = ""

        val manager = AndroidFrameCaptureManager(
            playbackController = controller,
            ioDispatcher = dispatcher,
            cacheDirProvider = { cacheDir },
            saveToMediaStore = { _, displayName ->
                FrameCaptureResult.Saved(displayName, "Pictures/Jupiter")
            },
            pixelCopyProvider = { sv, vw, vh, crop, displayName ->
                capturedSurfaceView = sv
                capturedVideoWidth = vw
                capturedVideoHeight = vh
                capturedCrop = crop
                capturedDisplayName = displayName
                FrameCaptureResult.Saved(displayName, "Pictures/Jupiter")
            },
            nowMs = { Instant.parse("2026-11-22T14:33:45Z").toEpochMilli() },
            zoneId = ZoneOffset.UTC,
        )

        val result = manager.captureCurrentFrame(
            title = "My Movie",
            surfaceView = fakeSurfaceView,
            videoWidth = 1920,
            videoHeight = 1080,
            cropLetterbox = true,
        )

        assertEquals(
            FrameCaptureResult.Saved(
                displayName = "Jupiter_My_Movie_20261122_143345.png",
                relativePath = "Pictures/Jupiter",
            ),
            result,
        )
        assertSame(fakeSurfaceView, capturedSurfaceView)
        assertEquals(1920, capturedVideoWidth)
        assertEquals(1080, capturedVideoHeight)
        assertTrue(capturedCrop)
        assertEquals("Jupiter_My_Movie_20261122_143345.png", capturedDisplayName)
        assertEquals(listOf("subtitles", "video"), controller.modesCalled)
        assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
    }

    private fun allocateTestSurfaceView(): SurfaceView {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocateMethod.invoke(unsafe, SurfaceView::class.java) as SurfaceView
    }

    private fun createManager(
        controller: PlaybackController,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): AndroidFrameCaptureManager = AndroidFrameCaptureManager(
        playbackController = controller,
        ioDispatcher = dispatcher,
        cacheDirProvider = { tempFolder.root },
        saveToMediaStore = { _, displayName ->
            FrameCaptureResult.Saved(displayName, "Pictures/Jupiter")
        },
        zoneId = ZoneOffset.UTC,
    )

    private class FakeScreenshotController(
        private val onScreenshot: (filePath: String, mode: String) -> Int = { _, _ -> -1 },
    ) : PlaybackController {
        val modesCalled = mutableListOf<String>()

        override val state: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState()).asStateFlow()
        override val events: SharedFlow<MpvEvent> = MutableSharedFlow<MpvEvent>().asSharedFlow()

        override fun initialize(configDir: String, extraOptions: Map<String, String>) = Unit
        override fun attachSurface(surface: Surface): Int = 0
        override fun detachSurface(): Int = 0
        override fun loadFile(uriOrFd: String, options: List<String>): Int = 0
        override fun play(): Int = 0
        override fun pause(): Int = 0
        override fun togglePlayPause(): Int = 0
        override fun seekTo(ms: Long): Int = 0
        override fun seekBy(deltaSec: Double): Int = 0
        override fun setSpeed(speed: Float): Int = 0
        override fun setVideoZoom(zoom: Double): Int = 0
        override fun setAspectOverride(aspect: String): Int = 0
        override fun setOption(name: String, value: String): Int = 0
        override fun setProperty(name: String, value: String): Int = 0
        override fun setNetworkHeaders(userAgent: String?, referer: String?, headers: Map<String, String>) = Unit
        override fun selectAudioTrack(id: Int?): Int = 0
        override fun selectSubtitleTrack(id: Int?): Int = 0
        override fun selectSecondarySubtitleTrack(id: Int?): Int = 0
        override fun addExternalSubtitle(fdPath: String): Int = 0
        override fun setSubtitleDelay(sec: Double): Int = 0
        override fun setAudioDelay(sec: Double): Int = 0
        override fun setSubtitleScale(scale: Double): Int = 0
        override fun setHwDec(mode: HwDecMode): Int = 0
        override fun selectChapter(index: Int): Int = 0
        override fun setAbLoopA(seconds: Double?): Int = 0
        override fun setAbLoopB(seconds: Double?): Int = 0
        override fun clearAbLoop(): Int = 0
        override fun setEqualizerProperty(name: String, value: Int): Int = 0

        override fun screenshotToFile(filePath: String, mode: String): Int {
            modesCalled += mode
            return onScreenshot(filePath, mode)
        }
    }
}
