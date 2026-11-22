package com.jupiter.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateTest {

    @Test
    fun `reduceDoubleProperty updates timing speed aspect and ignores non-finite values`() {
        var state = PlaybackState()

        state = PlaybackStateReducer.reduceDoubleProperty(state, "time-pos", 12.345)
        state = PlaybackStateReducer.reduceDoubleProperty(state, "duration", 120.5)
        state = PlaybackStateReducer.reduceDoubleProperty(state, "speed", 1.75)
        state = PlaybackStateReducer.reduceDoubleProperty(state, "video-params/aspect", 1.777)
        state = PlaybackStateReducer.reduceDoubleProperty(state, "sub-delay", -0.2)
        state = PlaybackStateReducer.reduceDoubleProperty(state, "audio-delay", 0.15)
        state = PlaybackStateReducer.reduceDoubleProperty(state, "video-zoom", 0.5)

        assertEquals(12345L, state.positionMs)
        assertEquals(120500L, state.durationMs)
        assertEquals(1.75f, state.speed, 0.001f)
        assertEquals(1.777, state.videoAspect, 0.001)
        assertEquals(-0.2, state.subDelaySec, 0.001)
        assertEquals(0.15, state.audioDelaySec, 0.001)
        assertEquals(0.5, state.videoZoom, 0.001)

        val unchanged = PlaybackStateReducer.reduceDoubleProperty(state, "time-pos", Double.NaN)
        assertEquals(state, unchanged)
        assertEquals(state, PlaybackStateReducer.reduceDoubleProperty(state, "duration", Double.POSITIVE_INFINITY))
    }

    @Test
    fun `reduceLongProperty and reduceBooleanProperty update dimensions buffering and flags`() {
        var state = PlaybackState()

        state = PlaybackStateReducer.reduceLongProperty(state, "video-params/w", 1920L)
        state = PlaybackStateReducer.reduceLongProperty(state, "video-params/h", 1080L)
        state = PlaybackStateReducer.reduceLongProperty(state, "cache-buffering-state", 140L)
        state = PlaybackStateReducer.reduceLongProperty(state, "chapter", 2L)
        state = PlaybackStateReducer.reduceBooleanProperty(state, "pause", true)
        state = PlaybackStateReducer.reduceBooleanProperty(state, "paused-for-cache", true)
        state = PlaybackStateReducer.reduceBooleanProperty(state, "eof-reached", true)

        assertEquals(1920, state.videoWidth)
        assertEquals(1080, state.videoHeight)
        assertEquals(100, state.bufferingPercent)
        assertEquals(2, state.currentChapter)
        assertTrue(state.isPaused)
        assertTrue(state.isBuffering)
        assertTrue(state.eofReached)
    }

    @Test
    fun `reduceStringProperty updates tracks chapters title and aspect override`() {
        var state = PlaybackState()
        val trackJson = """[{"id":1,"type":"audio","lang":"eng","selected":true}]"""
        val chapterJson = """[{"title":"Part 1","time":0.0}]"""

        state = PlaybackStateReducer.reduceStringProperty(state, "track-list", trackJson)
        state = PlaybackStateReducer.reduceStringProperty(state, "chapter-list", chapterJson)
        state = PlaybackStateReducer.reduceStringProperty(state, "media-title", "Demo Clip")
        state = PlaybackStateReducer.reduceStringProperty(state, "video-aspect-override", "16:9")

        assertEquals(1, state.audioTracks.size)
        assertEquals(1, state.chapters.size)
        assertEquals("Demo Clip", state.mediaTitle)
        assertEquals("16:9", state.aspectOverride)
    }

    @Test
    fun `reduceEvent resets per-file state on StartFile and updates lifecycle on EndFile and Shutdown`() {
        var state = PlaybackState(
            lifecycleState = EngineLifecycleState.INITIALIZED,
            positionMs = 5000L,
            durationMs = 10000L,
            isBuffering = true,
            bufferingPercent = 42,
            eofReached = true,
            chapters = listOf(Chapter(0, "Opening", 0.0)),
            currentChapter = 0
        )

        state = PlaybackStateReducer.reduceEvent(state, MpvEvent.StartFile)
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
        assertFalse(state.isBuffering)
        assertEquals(0, state.bufferingPercent)
        assertFalse(state.eofReached)
        assertTrue(state.chapters.isEmpty())
        assertEquals(-1, state.currentChapter)

        state = state.copy(isBuffering = true)
        state = PlaybackStateReducer.reduceEvent(
            state,
            MpvEvent.EndFile(reason = MpvNative.END_FILE_REASON_EOF, errorCode = 0)
        )
        assertFalse(state.isBuffering)
        assertTrue(state.eofReached)

        state = PlaybackStateReducer.reduceEvent(state, MpvEvent.Shutdown)
        assertEquals(EngineLifecycleState.DESTROYED, state.lifecycleState)
    }
}
