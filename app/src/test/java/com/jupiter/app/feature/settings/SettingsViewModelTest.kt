package com.jupiter.app.feature.settings

import com.jupiter.app.data.local.MediaHistoryEntity
import com.jupiter.app.data.media.LocalVideoItem
import com.jupiter.app.data.repository.InMemorySettingsRepository
import com.jupiter.app.data.repository.MediaRepository
import com.jupiter.engine.HwDecMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: InMemorySettingsRepository
    private lateinit var mediaRepository: FakeMediaRepository
    private lateinit var viewModel: SettingsViewModel
    private var cacheClearCalls = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cacheClearCalls = 0
        settingsRepository = InMemorySettingsRepository(
            onClearCache = {
                cacheClearCalls++
                true
            },
        )
        mediaRepository = FakeMediaRepository()
        viewModel = SettingsViewModel(
            settingsRepository = settingsRepository,
            mediaRepository = mediaRepository,
            mpvVersionProvider = { "2.5-test" },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initializes mpv version from provider without JNI`() = runTest {
        runCurrent()
        assertEquals("2.5-test", viewModel.uiState.value.mpvVersion)
    }

    @Test
    fun `preference updates persist to repository and reflect in uiState`() = runTest {
        runCurrent()
        assertEquals(0xFF090C12.toInt(), viewModel.uiState.value.preferences.backgroundArgb)
        assertEquals(0xFFE6ECF5.toInt(), viewModel.uiState.value.preferences.accentArgb)
        assertTrue(viewModel.uiState.value.preferences.glassBlurEnabled)

        viewModel.setHwDecMode(HwDecMode.MEDIACODEC)
        viewModel.setDoubleTapSeekSec(15)
        viewModel.setDefaultPlaybackSpeed(1.5f)
        viewModel.setRememberResumePosition(false)
        viewModel.setBackgroundPlayEnabled(false)
        viewModel.setAutoEnterPip(false)
        viewModel.setSubtitleFontScale(1.8f)
        viewModel.setPreferredAudioLang("jpn,eng")
        viewModel.setPreferredSubtitleLang("eng")
        viewModel.setBackgroundArgb(0xFF112233.toInt())
        viewModel.setAccentArgb(0xFF7DA8F5.toInt())
        viewModel.setGlassBlurEnabled(false)
        viewModel.setGestureBrightnessEnabled(false)
        viewModel.setGestureVolumeEnabled(false)
        viewModel.setGestureSeekEnabled(false)
        viewModel.setCornerRoundnessPercent(80)
        runCurrent()

        val prefs = viewModel.uiState.value.preferences
        assertEquals(HwDecMode.MEDIACODEC, prefs.hwDecMode)
        assertEquals(15, prefs.doubleTapSeekSec)
        assertEquals(1.5f, prefs.defaultPlaybackSpeed, 0.001f)
        assertFalse(prefs.rememberResumePosition)
        assertFalse(prefs.backgroundPlayEnabled)
        assertFalse(prefs.autoEnterPip)
        assertEquals(1.8f, prefs.subtitleFontScale, 0.001f)
        assertEquals("jpn,eng", prefs.preferredAudioLang)
        assertEquals("eng", prefs.preferredSubtitleLang)
        assertEquals(0xFF112233.toInt(), prefs.backgroundArgb)
        assertEquals(0xFF7DA8F5.toInt(), prefs.accentArgb)
        assertFalse(prefs.glassBlurEnabled)
        assertFalse(prefs.gestureBrightnessEnabled)
        assertFalse(prefs.gestureVolumeEnabled)
        assertFalse(prefs.gestureSeekEnabled)
        assertEquals(80, prefs.cornerRoundnessPercent)

        viewModel.setCornerRoundnessPercent(150)
        runCurrent()
        assertEquals(100, viewModel.uiState.value.preferences.cornerRoundnessPercent)

        viewModel.setCornerRoundnessPercent(-10)
        runCurrent()
        assertEquals(0, viewModel.uiState.value.preferences.cornerRoundnessPercent)
    }

    @Test
    fun `importMpvConf validates, persists config, and resetMpvConf restores defaults`() = runTest {
        runCurrent()
        assertFalse(viewModel.uiState.value.hasCustomMpvConf)
        assertEquals(0, viewModel.uiState.value.customMpvConfLineCount)

        val validConfig = "# User config\nvolume-max=150\nsub-auto=fuzzy\n"
        viewModel.importMpvConf(validConfig, fileName = "custom.conf")
        runCurrent()

        assertTrue(viewModel.uiState.value.hasCustomMpvConf)
        assertEquals(3, viewModel.uiState.value.customMpvConfLineCount)
        assertEquals(validConfig, viewModel.uiState.value.preferences.customMpvConf)
        assertEquals("Imported custom.conf (3 lines)", viewModel.uiState.value.feedbackMessage)

        viewModel.dismissFeedback()
        assertNull(viewModel.uiState.value.feedbackMessage)

        // Blank, oversized, and binary files are rejected without overwriting existing config.
        viewModel.importMpvConf("   \n\t ", fileName = "empty.conf")
        runCurrent()
        assertEquals("Selected file is empty", viewModel.uiState.value.feedbackMessage)
        assertEquals(validConfig, viewModel.uiState.value.preferences.customMpvConf)

        val oversized = "a".repeat(SettingsViewModel.MAX_MPV_CONF_BYTES + 1)
        viewModel.importMpvConf(oversized, fileName = "huge.conf")
        runCurrent()
        assertEquals("Config file exceeds 256 KB", viewModel.uiState.value.feedbackMessage)
        assertEquals(validConfig, viewModel.uiState.value.preferences.customMpvConf)

        viewModel.importMpvConf("volume=100\u0000binary", fileName = "video.mkv")
        runCurrent()
        assertEquals("Invalid text config file", viewModel.uiState.value.feedbackMessage)
        assertEquals(validConfig, viewModel.uiState.value.preferences.customMpvConf)

        viewModel.onImportMpvConfError("Unable to open selected file")
        assertEquals("Unable to open selected file", viewModel.uiState.value.feedbackMessage)

        viewModel.resetMpvConf()
        runCurrent()

        assertFalse(viewModel.uiState.value.hasCustomMpvConf)
        assertEquals(0, viewModel.uiState.value.customMpvConfLineCount)
        assertEquals("", viewModel.uiState.value.preferences.customMpvConf)
        assertEquals("Reset mpv.conf to defaults", viewModel.uiState.value.feedbackMessage)
    }

    @Test
    fun `importMpvConf uses default filename when omitted`() = runTest {
        runCurrent()
        viewModel.importMpvConf("profile=fast")
        runCurrent()

        assertTrue(viewModel.uiState.value.hasCustomMpvConf)
        assertEquals(1, viewModel.uiState.value.customMpvConfLineCount)
        assertEquals("Imported mpv.conf (1 line)", viewModel.uiState.value.feedbackMessage)
    }

    @Test
    fun `clear history requires confirmation and clears media repository on confirm`() = runTest {
        viewModel.requestClearHistory()
        assertTrue(viewModel.uiState.value.clearHistoryDialogOpen)

        viewModel.dismissClearHistory()
        assertFalse(viewModel.uiState.value.clearHistoryDialogOpen)
        assertEquals(0, mediaRepository.clearCalls)

        viewModel.requestClearHistory()
        viewModel.confirmClearHistory()
        runCurrent()

        assertFalse(viewModel.uiState.value.clearHistoryDialogOpen)
        assertEquals(1, mediaRepository.clearCalls)
        assertEquals("Watch history cleared", viewModel.uiState.value.feedbackMessage)
    }

    @Test
    fun `clearCache triggers repository cache clear and shows feedback`() = runTest {
        viewModel.clearCache()
        runCurrent()

        assertEquals(1, cacheClearCalls)
        assertEquals("Cache cleared", viewModel.uiState.value.feedbackMessage)
    }

    private class FakeMediaRepository : MediaRepository {
        private val historyFlow = MutableStateFlow<List<MediaHistoryEntity>>(emptyList())
        var clearCalls = 0
            private set

        override suspend fun scanLocalVideos(): List<LocalVideoItem> = emptyList()

        override fun observeContinueWatching(limit: Int): Flow<List<MediaHistoryEntity>> {
            return historyFlow.asStateFlow()
        }

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

        override suspend fun clearHistory() {
            clearCalls++
            historyFlow.value = emptyList()
        }
    }
}
