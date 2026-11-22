package com.jupiter.app.core.di

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SubtitleFontBootstrapperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `ensureSubfont copies first readable non-empty candidate atomically`() {
        val configDir = tempFolder.newFolder("mpv-config")
        val missingPath = File(tempFolder.root, "missing.ttf").absolutePath
        val emptyFile = tempFolder.newFile("empty.ttf")
        val fallbackBytes = byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x42, 0x43)
        val validFile = tempFolder.newFile("Roboto-Regular.ttf").apply {
            writeBytes(fallbackBytes)
        }
        val secondaryFile = tempFolder.newFile("DroidSans.ttf").apply {
            writeBytes(byteArrayOf(0x09, 0x09))
        }

        val result = SubtitleFontBootstrapper.ensureSubfont(
            configDir = configDir.absolutePath,
            candidatePaths = listOf(
                missingPath,
                emptyFile.absolutePath,
                validFile.absolutePath,
                secondaryFile.absolutePath,
            ),
        )

        assertNotNull(result)
        val expectedTarget = File(configDir, "subfont.ttf")
        assertEquals(expectedTarget.absolutePath, result!!.absolutePath)
        assertArrayEquals(fallbackBytes, result.readBytes())
        assertFalse(File(configDir, "subfont.ttf.tmp").exists())
    }

    @Test
    fun `ensureSubfont reuses existing non-empty subfont and replaces empty one`() {
        val configDir = tempFolder.newFolder("mpv-reuse")
        val target = File(configDir, "subfont.ttf")
        val existingBytes = byteArrayOf(0x11, 0x22, 0x33)
        target.writeBytes(existingBytes)

        val newSource = tempFolder.newFile("NotoSans-Regular.ttf").apply {
            writeBytes(byteArrayOf(0x44, 0x55, 0x66, 0x77))
        }

        val reused = SubtitleFontBootstrapper.ensureSubfont(
            configDir = configDir.absolutePath,
            candidatePaths = listOf(newSource.absolutePath),
        )

        assertNotNull(reused)
        assertArrayEquals(existingBytes, reused!!.readBytes())

        // Empty existing target should be replaced.
        target.writeBytes(byteArrayOf())
        val replaced = SubtitleFontBootstrapper.ensureSubfont(
            configDir = configDir.absolutePath,
            candidatePaths = listOf(newSource.absolutePath),
        )

        assertNotNull(replaced)
        assertArrayEquals(newSource.readBytes(), replaced!!.readBytes())
    }

    @Test
    fun `ensureSubfont returns null when no candidate exists or configDir is blank`() {
        val configDir = tempFolder.newFolder("mpv-empty")
        val missing = File(tempFolder.root, "does-not-exist.ttf").absolutePath

        val result = SubtitleFontBootstrapper.ensureSubfont(
            configDir = configDir.absolutePath,
            candidatePaths = listOf(missing),
        )

        assertNull(result)
        assertFalse(File(configDir, "subfont.ttf").exists())
        assertNull(SubtitleFontBootstrapper.ensureSubfont(configDir = "   "))
        assertTrue(SubtitleFontBootstrapper.DEFAULT_CANDIDATES.contains("/system/fonts/Roboto-Regular.ttf"))
    }
}
