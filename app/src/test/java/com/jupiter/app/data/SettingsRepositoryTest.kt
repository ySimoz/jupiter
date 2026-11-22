package com.jupiter.app.data

import com.jupiter.app.data.local.MpvConfStore
import com.jupiter.app.data.local.UserPreferences
import com.jupiter.app.data.repository.InMemorySettingsRepository
import com.jupiter.app.data.repository.SettingsValidation
import com.jupiter.engine.HwDecMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `MpvConfStore writes atomically, reads, clears on blank, and resets`() {
        val configDir = tempFolder.newFolder("mpv-conf").absolutePath
        assertEquals("", MpvConfStore.read(configDir))

        val initialConf = "hwdec=mediacodec-copy\nprofile=fast\n"
        MpvConfStore.write(configDir, initialConf)

        val confFile = File(configDir, "mpv.conf")
        val tmpFile = File(configDir, "mpv.conf.tmp")
        assertTrue(confFile.isFile)
        assertFalse(tmpFile.exists())
        assertEquals(initialConf, MpvConfStore.read(configDir))

        val updatedConf = "alang=jpn,eng\nslang=eng\n"
        MpvConfStore.write(configDir, updatedConf)
        assertEquals(updatedConf, MpvConfStore.read(configDir))
        assertFalse(tmpFile.exists())

        MpvConfStore.write(configDir, "   \n ")
        assertFalse(confFile.exists())
        assertEquals("", MpvConfStore.read(configDir))

        MpvConfStore.write(configDir, "volume=80\n")
        assertTrue(confFile.isFile)
        MpvConfStore.reset(configDir)
        assertFalse(confFile.exists())
        assertFalse(tmpFile.exists())
        assertEquals("", MpvConfStore.read(configDir))
    }

    @Test
    fun `InMemorySettingsRepository exposes defaults and validates or clamps inputs`() = runTest {
        val configDir = tempFolder.newFolder("repo-conf").absolutePath
        val repo = InMemorySettingsRepository(configDir = configDir)

        assertEquals(UserPreferences(), repo.preferences.first())
        assertEquals(0xFF090C12.toInt(), repo.current.backgroundArgb)
        assertEquals(0xFFE6ECF5.toInt(), repo.current.accentArgb)
        assertTrue(repo.current.glassBlurEnabled)

        assertEquals(
            0xFF090C12.toInt(),
            SettingsValidation.resolveBackgroundArgb(null),
        )
        assertEquals(
            0xFF112233.toInt(),
            SettingsValidation.resolveBackgroundArgb(0x22112233),
        )

        assertEquals(
            0xFFE6ECF5.toInt(),
            SettingsValidation.resolveAccentArgb(storedAccentArgb = null, legacyAccentStyle = null),
        )
        assertEquals(
            0xFF5CE1E6.toInt(),
            SettingsValidation.resolveAccentArgb(storedAccentArgb = null, legacyAccentStyle = "ICE"),
        )
        assertEquals(
            0xFF7DA8F5.toInt(),
            SettingsValidation.resolveAccentArgb(storedAccentArgb = null, legacyAccentStyle = "COBALT"),
        )
        assertEquals(
            0xFF7BC9A6.toInt(),
            SettingsValidation.resolveAccentArgb(storedAccentArgb = null, legacyAccentStyle = "SAGE"),
        )
        assertEquals(
            0xFFE8B86D.toInt(),
            SettingsValidation.resolveAccentArgb(storedAccentArgb = null, legacyAccentStyle = "AMBER"),
        )
        assertEquals(
            0xFFE6ECF5.toInt(),
            SettingsValidation.resolveAccentArgb(storedAccentArgb = null, legacyAccentStyle = "MONOCHROME"),
        )
        assertEquals(
            0xFFABCDEF.toInt(),
            SettingsValidation.resolveAccentArgb(storedAccentArgb = 0x33ABCDEF, legacyAccentStyle = "ICE"),
        )

        assertTrue(SettingsValidation.resolveGlassBlurEnabled(null, null))
        assertFalse(SettingsValidation.resolveGlassBlurEnabled(null, true))
        assertTrue(SettingsValidation.resolveGlassBlurEnabled(null, false))
        assertTrue(SettingsValidation.resolveGlassBlurEnabled(true, true))
        assertFalse(SettingsValidation.resolveGlassBlurEnabled(false, false))

        for (valid in listOf(5, 10, 15, 30)) {
            repo.setDoubleTapSeekSec(valid)
            assertEquals(valid, repo.current.doubleTapSeekSec)
        }
        for (invalid in listOf(0, -10, 7, 12, 45)) {
            repo.setDoubleTapSeekSec(invalid)
            assertEquals(10, repo.current.doubleTapSeekSec)
        }

        repo.setDefaultPlaybackSpeed(0.1f)
        assertEquals(0.25f, repo.current.defaultPlaybackSpeed, 0.0001f)
        repo.setDefaultPlaybackSpeed(8.0f)
        assertEquals(4.0f, repo.current.defaultPlaybackSpeed, 0.0001f)
        repo.setDefaultPlaybackSpeed(Float.NaN)
        assertEquals(1.0f, repo.current.defaultPlaybackSpeed, 0.0001f)

        repo.setSubtitleFontScale(0.1f)
        assertEquals(0.5f, repo.current.subtitleFontScale, 0.0001f)
        repo.setSubtitleFontScale(4.0f)
        assertEquals(2.5f, repo.current.subtitleFontScale, 0.0001f)
        repo.setSubtitleFontScale(Float.POSITIVE_INFINITY)
        assertEquals(1.0f, repo.current.subtitleFontScale, 0.0001f)

        repo.setPreferredAudioLang(" jpn,eng \n")
        assertEquals("jpn,eng", repo.current.preferredAudioLang)
        repo.setPreferredSubtitleLang(" eng\r\n")
        assertEquals("eng", repo.current.preferredSubtitleLang)

        assertEquals(50, repo.current.cornerRoundnessPercent)
        repo.setCornerRoundnessPercent(-15)
        assertEquals(0, repo.current.cornerRoundnessPercent)
        repo.setCornerRoundnessPercent(145)
        assertEquals(100, repo.current.cornerRoundnessPercent)
        repo.setCornerRoundnessPercent(72)
        assertEquals(72, repo.current.cornerRoundnessPercent)

        repo.setBackgroundArgb(0x123456)
        assertEquals(0xFF123456.toInt(), repo.current.backgroundArgb)
        repo.setAccentArgb(0x22ABCDEF)
        assertEquals(0xFFABCDEF.toInt(), repo.current.accentArgb)
    }

    @Test
    fun `InMemorySettingsRepository updates all preference flags, custom mpv conf, and cache clear`() = runTest {
        val configDir = tempFolder.newFolder("repo-all").absolutePath
        var cacheCleared = false
        val repo = InMemorySettingsRepository(
            configDir = configDir,
            onClearCache = {
                cacheCleared = true
                true
            },
        )

        repo.setHwDecMode(HwDecMode.SOFTWARE)
        repo.setRememberResumePosition(false)
        repo.setBackgroundPlayEnabled(false)
        repo.setAutoEnterPip(false)
        repo.setBackgroundArgb(0xFF102030.toInt())
        repo.setAccentArgb(0xFF7BC9A6.toInt())
        repo.setGlassBlurEnabled(false)
        repo.setGestureBrightnessEnabled(false)
        repo.setGestureVolumeEnabled(false)
        repo.setGestureSeekEnabled(false)
        repo.saveCustomMpvConf("demuxer-max-bytes=64MiB")

        val updated = repo.preferences.first()
        assertEquals(HwDecMode.SOFTWARE, updated.hwDecMode)
        assertFalse(updated.rememberResumePosition)
        assertFalse(updated.backgroundPlayEnabled)
        assertFalse(updated.autoEnterPip)
        assertEquals(0xFF102030.toInt(), updated.backgroundArgb)
        assertEquals(0xFF7BC9A6.toInt(), updated.accentArgb)
        assertFalse(updated.glassBlurEnabled)
        assertFalse(updated.gestureBrightnessEnabled)
        assertFalse(updated.gestureVolumeEnabled)
        assertFalse(updated.gestureSeekEnabled)
        assertEquals("demuxer-max-bytes=64MiB", updated.customMpvConf)
        assertEquals("demuxer-max-bytes=64MiB", MpvConfStore.read(configDir))

        repo.resetCustomMpvConf()
        assertEquals("", repo.preferences.first().customMpvConf)
        assertEquals("", MpvConfStore.read(configDir))

        assertTrue(repo.clearAppCache())
        assertTrue(cacheCleared)
    }
}
