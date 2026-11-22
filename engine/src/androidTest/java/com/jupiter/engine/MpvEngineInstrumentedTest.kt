package com.jupiter.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class MpvEngineInstrumentedTest {

    @Test
    fun nativeGetMpvVersion_returnsNonEmptyVersionString() {
        val version = MpvNative.nativeGetMpvVersion()
        assertTrue("Expected non-blank mpv API version, got '$version'", version.isNotBlank())
    }

    @Test
    fun initialize_propertyReadWrite_andDestroy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configDir = File(context.cacheDir, "mpv_test_cfg").apply { mkdirs() }
        val engine = MpvEngine()

        assertEquals(EngineLifecycleState.IDLE, engine.state.value.lifecycleState)
        engine.initialize(configDir.absolutePath)
        try {
            assertEquals(EngineLifecycleState.INITIALIZED, engine.state.value.lifecycleState)

            assertTrue(engine.setSpeed(1.5f) >= 0)
            assertEquals(1.5, engine.getPropertyDouble("speed") ?: -1.0, 0.01)

            assertTrue(engine.pause() >= 0)
            assertEquals(true, engine.getPropertyBoolean("pause"))

            assertTrue(engine.setSubtitleDelay(0.25) >= 0)
            assertEquals(0.25, engine.getPropertyDouble("sub-delay") ?: -1.0, 0.01)

            assertTrue(engine.setHwDec(HwDecMode.SOFTWARE) >= 0)
            assertEquals(HwDecMode.SOFTWARE, engine.state.value.hwDecMode)
        } finally {
            engine.destroy()
        }

        assertEquals(EngineLifecycleState.DESTROYED, engine.state.value.lifecycleState)
        // Post-destroy operations must fail safely without crashing
        assertEquals(-1, engine.play())
    }

    @Test
    fun loadSyntheticWav_observesEventsDurationTracks_andDestroysCleanly() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configDir = File(context.cacheDir, "mpv_test_cfg_wav").apply { mkdirs() }
        val wavFile = File(context.cacheDir, "test_tone.wav")
        writeSineWav(wavFile, durationSec = 1, sampleRate = 44100)

        val engine = MpvEngine()
        engine.initialize(
            configDir = configDir.absolutePath,
            extraOptions = mapOf("ao" to "null", "pause" to "no")
        )
        try {
            val startFileSeen = CompletableDeferred<Unit>()
            val fileLoadedSeen = CompletableDeferred<Unit>()
            val eventCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                engine.events.collect { event ->
                    when (event) {
                        MpvEvent.StartFile -> startFileSeen.complete(Unit)
                        MpvEvent.FileLoaded -> fileLoadedSeen.complete(Unit)
                        else -> Unit
                    }
                }
            }

            assertTrue(engine.loadFile(wavFile.absolutePath) >= 0)

            withTimeout(10_000L) {
                startFileSeen.await()
                fileLoadedSeen.await()
            }
            eventCollector.cancel()

            val loadedState = withTimeout(10_000L) {
                engine.state.filter { it.audioTracks.isNotEmpty() && it.durationMs > 0L }.first()
            }

            assertTrue("Expected duration > 500ms, got ${loadedState.durationMs}", loadedState.durationMs >= 500L)
            assertFalse("Expected at least one audio track", loadedState.audioTracks.isEmpty())

            // Verify time-pos / eof progresses cleanly without deadlock
            val endedOrProgressed = withTimeout(10_000L) {
                engine.state.filter { it.positionMs > 0L || it.eofReached }.first()
            }
            assertTrue(endedOrProgressed.positionMs > 0L || endedOrProgressed.eofReached)
        } finally {
            engine.destroy()
            wavFile.delete()
        }
    }

    @Test
    fun repeatedCreateDestroyCycles_doNotCrashOrLeak() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configDir = File(context.cacheDir, "mpv_test_cycles").apply { mkdirs() }
        val wavFile = File(context.cacheDir, "cycle_tone.wav")
        writeSineWav(wavFile, durationSec = 1, sampleRate = 22050)

        try {
            repeat(5) {
                val engine = MpvEngine()
                engine.initialize(
                    configDir = configDir.absolutePath,
                    extraOptions = mapOf("ao" to "null")
                )
                engine.loadFile(wavFile.absolutePath)
                engine.setSpeed(1.25f)
                engine.destroy()
                assertEquals(EngineLifecycleState.DESTROYED, engine.state.value.lifecycleState)
            }
        } finally {
            wavFile.delete()
        }
    }

    private fun writeSineWav(file: File, durationSec: Int, sampleRate: Int) {
        val numSamples = durationSec * sampleRate
        val dataSize = numSamples * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1) // PCM format
            putShort(1) // mono
            putInt(sampleRate)
            putInt(sampleRate * 2) // byte rate
            putShort(2) // block align
            putShort(16) // bits per sample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()

        val pcm = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) {
            val sample = (sin(2.0 * PI * 440.0 * i / sampleRate) * 8000.0).toInt().toShort()
            pcm.putShort(sample)
        }

        FileOutputStream(file).use { out ->
            out.write(header)
            out.write(pcm.array())
        }
    }
}
