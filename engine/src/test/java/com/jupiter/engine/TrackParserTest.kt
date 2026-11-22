package com.jupiter.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackParserTest {

    @Test
    fun `parseTrackList parses video audio and external subtitle tracks`() {
        val json = """
            [
              {
                "id": 1,
                "type": "video",
                "title": "Main Video",
                "lang": "und",
                "codec": "hevc",
                "selected": true,
                "default": true,
                "external": false,
                "demux-w": 3840,
                "demux-h": 2160,
                "demux-fps": 23.976
              },
              {
                "id": 1,
                "type": "audio",
                "title": "English 5.1",
                "lang": "eng",
                "codec": "eac3",
                "selected": true,
                "default": true,
                "external": false,
                "demux-channel-count": 6,
                "demux-samplerate": 48000
              },
              {
                "id": 2,
                "type": "audio",
                "title": "Japanese Stereo",
                "lang": "jpn",
                "codec": "aac",
                "selected": false,
                "default": false,
                "external": false,
                "audio-channels": 2,
                "samplerate": 44100
              },
              {
                "id": 1,
                "type": "sub",
                "title": "Signs & Songs",
                "lang": "eng",
                "codec": "ass",
                "selected": false,
                "default": true,
                "external": false
              },
              {
                "id": 2,
                "type": "sub",
                "title": "movie.srt",
                "lang": "fra",
                "codec": "subrip",
                "selected": true,
                "default": false,
                "external": true
              }
            ]
        """.trimIndent()

        val parsed = MpvJsonParser.parseTrackList(json)

        assertEquals(1, parsed.videoTracks.size)
        val video = parsed.videoTracks[0]
        assertEquals(1, video.id)
        assertEquals("Main Video", video.title)
        assertEquals("hevc", video.codec)
        assertTrue(video.isSelected)
        assertTrue(video.isDefault)
        assertFalse(video.isExternal)
        assertEquals(3840, video.width)
        assertEquals(2160, video.height)
        assertEquals(23.976, video.fps!!, 0.001)

        assertEquals(2, parsed.audioTracks.size)
        assertEquals(6, parsed.audioTracks[0].channelCount)
        assertEquals(48000, parsed.audioTracks[0].sampleRate)
        assertTrue(parsed.audioTracks[0].isSelected)
        assertEquals("jpn", parsed.audioTracks[1].lang)
        assertEquals(2, parsed.audioTracks[1].channelCount)
        assertEquals(44100, parsed.audioTracks[1].sampleRate)
        assertFalse(parsed.audioTracks[1].isSelected)

        assertEquals(2, parsed.subtitleTracks.size)
        assertFalse(parsed.subtitleTracks[0].isExternal)
        assertEquals("ass", parsed.subtitleTracks[0].codec)
        assertTrue(parsed.subtitleTracks[1].isExternal)
        assertTrue(parsed.subtitleTracks[1].isSelected)
        assertEquals("fra", parsed.subtitleTracks[1].lang)
    }

    @Test
    fun `parseTrackList handles null blank and malformed json gracefully`() {
        assertEquals(ParsedTrackList(), MpvJsonParser.parseTrackList(null))
        assertEquals(ParsedTrackList(), MpvJsonParser.parseTrackList(""))
        assertEquals(ParsedTrackList(), MpvJsonParser.parseTrackList("   "))
        assertEquals(ParsedTrackList(), MpvJsonParser.parseTrackList("null"))
        assertEquals(ParsedTrackList(), MpvJsonParser.parseTrackList("{\"not\":\"array\"}"))
        assertEquals(ParsedTrackList(), MpvJsonParser.parseTrackList("[{broken"))
    }

    @Test
    fun `parseTrackList skips malformed entries without dropping valid ones`() {
        val json = """
            [
              "not-an-object",
              {"type": "audio"},
              {"id": 3, "type": "unknown"},
              {"id": 4, "type": "sub", "title": "Valid Sub", "selected": true}
            ]
        """.trimIndent()

        val parsed = MpvJsonParser.parseTrackList(json)
        assertTrue(parsed.videoTracks.isEmpty())
        assertTrue(parsed.audioTracks.isEmpty())
        assertEquals(1, parsed.subtitleTracks.size)
        assertEquals(4, parsed.subtitleTracks[0].id)
        assertEquals("Valid Sub", parsed.subtitleTracks[0].title)
        assertTrue(parsed.subtitleTracks[0].isSelected)
    }

    @Test
    fun `parseChapterList parses chapters and handles invalid inputs`() {
        val json = """
            [
              {"title": "Intro", "time": 0.0},
              {"time": 95.5},
              {"title": "Missing time"},
              {"title": "Credits", "time": 1420.25}
            ]
        """.trimIndent()

        val chapters = MpvJsonParser.parseChapterList(json)
        assertEquals(3, chapters.size)
        assertEquals(0, chapters[0].index)
        assertEquals("Intro", chapters[0].title)
        assertEquals(0.0, chapters[0].timeSec, 0.001)

        assertEquals(1, chapters[1].index)
        assertNull(chapters[1].title)
        assertEquals(95.5, chapters[1].timeSec, 0.001)

        assertEquals(3, chapters[2].index)
        assertEquals("Credits", chapters[2].title)
        assertEquals(1420.25, chapters[2].timeSec, 0.001)

        assertTrue(MpvJsonParser.parseChapterList(null).isEmpty())
        assertTrue(MpvJsonParser.parseChapterList("bad json").isEmpty())
    }

    @Test
    fun `hwDecMode maps expected mpv strings`() {
        assertEquals("mediacodec-copy", HwDecMode.AUTO.mpvValue)
        assertEquals("mediacodec-copy", HwDecMode.MEDIACODEC_COPY.mpvValue)
        assertEquals("mediacodec", HwDecMode.MEDIACODEC.mpvValue)
        assertEquals("no", HwDecMode.SOFTWARE.mpvValue)
    }
}
