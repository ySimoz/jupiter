package com.jupiter.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MpvEngineHeaderTest {

    @Test
    fun `default stream options enable cache and disable ytdl`() {
        assertEquals("yes", MpvEngine.DEFAULT_STREAM_OPTIONS["cache"])
        assertEquals("128MiB", MpvEngine.DEFAULT_STREAM_OPTIONS["demuxer-max-bytes"])
        assertEquals("32MiB", MpvEngine.DEFAULT_STREAM_OPTIONS["demuxer-max-back-bytes"])
        assertEquals("no", MpvEngine.DEFAULT_STREAM_OPTIONS["ytdl"])
    }

    @Test
    fun `default subtitle options disable system font provider and keep embedded fonts`() {
        assertEquals("none", MpvEngine.DEFAULT_SUBTITLE_OPTIONS["sub-font-provider"])
        assertEquals("yes", MpvEngine.DEFAULT_SUBTITLE_OPTIONS["embeddedfonts"])
    }

    @Test
    fun `buildHeaderFieldsValue strips CRLF skips empty keys and clears on empty map`() {
        assertEquals("", MpvEngine.buildHeaderFieldsValue(emptyMap()))
        assertEquals("", MpvEngine.buildHeaderFieldsValue(mapOf("" to "val", "  \r\n " to "val2")))

        val formatted = MpvEngine.buildHeaderFieldsValue(
            linkedMapOf(
                "Authorization\r\n" to "Bearer\n token",
                "" to "ignored",
                "X-Custom:Colon" to "val:1",
            ),
        )
        assertEquals("Authorization: Bearer token,X-CustomColon: val:1", formatted)
    }
}
