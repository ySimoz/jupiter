package com.jupiter.app.data

import com.jupiter.app.data.local.NetworkStreamDao
import com.jupiter.app.data.local.NetworkStreamEntity
import com.jupiter.app.data.repository.DefaultStreamRepository
import com.jupiter.app.data.repository.StreamHeaderConfig
import com.jupiter.app.data.repository.StreamUrlValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRepositoryTest {

    @Test
    fun `StreamUrlValidator accepts all 9 allowed schemes and normalizes scheme case`() {
        val urls = listOf(
            "http://example.com/video.mp4" to Triple("http", "example.com", "http://example.com/video.mp4"),
            "HTTPS://cdn.example.com/live/master.m3u8?token=AbC" to Triple("https", "cdn.example.com", "https://cdn.example.com/live/master.m3u8?token=AbC"),
            "rtsp://192.168.1.20:554/stream1" to Triple("rtsp", "192.168.1.20", "rtsp://192.168.1.20:554/stream1"),
            "rtmp://live.example.com/app/key" to Triple("rtmp", "live.example.com", "rtmp://live.example.com/app/key"),
            "mms://stream.example.com/audio" to Triple("mms", "stream.example.com", "mms://stream.example.com/audio"),
            "udp://@239.0.0.1:1234" to Triple("udp", "239.0.0.1", "udp://@239.0.0.1:1234"),
            "rtp://239.1.2.3:5004" to Triple("rtp", "239.1.2.3", "rtp://239.1.2.3:5004"),
            "ftp://files.example.com/movie.mkv" to Triple("ftp", "files.example.com", "ftp://files.example.com/movie.mkv"),
            "smb://nas.local/media/film.mkv" to Triple("smb", "nas.local", "smb://nas.local/media/film.mkv"),
        )

        assertEquals(9, StreamUrlValidator.ALLOWED_SCHEMES.size)
        for ((input, expected) in urls) {
            val result = StreamUrlValidator.validate("  $input  ")
            assertTrue("Expected valid for $input but got $result", result is StreamUrlValidator.ValidationResult.Valid)
            val valid = result as StreamUrlValidator.ValidationResult.Valid
            assertEquals(expected.first, valid.scheme)
            assertEquals(expected.second, valid.host)
            assertEquals(expected.third, valid.normalizedUrl)
        }
    }

    @Test
    fun `StreamUrlValidator rejects invalid schemes blank input whitespace and missing host`() {
        val invalidInputs = listOf(
            "",
            "   ",
            "example.com/live.m3u8",
            "file:///sdcard/video.mp4",
            "content://media/external/video/media/1",
            "javascript:alert(1)",
            "gopher://example.com/1",
            "https://",
            "http:///only/path",
            "http://:8080/nohost",
            "https://example.com/has space.mp4",
            "https://example.com/\r\nnewline",
        )

        for (input in invalidInputs) {
            val result = StreamUrlValidator.validate(input)
            assertTrue(
                "Expected invalid for '$input' but got $result",
                result is StreamUrlValidator.ValidationResult.Invalid,
            )
        }
    }

    @Test
    fun `deriveDisplayTitle extracts decoded filename or falls back to host`() {
        assertEquals(
            "Big Buck Bunny.m3u8",
            StreamUrlValidator.deriveDisplayTitle("https://cdn.example.com/hls/Big%20Buck%20Bunny.m3u8?token=123"),
        )
        assertEquals(
            "live.example.com",
            StreamUrlValidator.deriveDisplayTitle("https://live.example.com/"),
        )
        assertEquals(
            "192.168.1.50",
            StreamUrlValidator.deriveDisplayTitle("rtsp://192.168.1.50:554"),
        )
    }

    @Test
    fun `StreamHeaderConfig sanitizes CRLF skips blank keys and round-trips cleanly`() {
        assertEquals("Mozilla/5.0", StreamHeaderConfig.sanitizeHeaderValue("  Mozilla/5.0\r\nEvil: 1  ".substringBefore("Evil") + "  "))
        assertEquals("CleanValue", StreamHeaderConfig.sanitizeHeaderValue("Clean\r\nValue\n"))

        val dirty = StreamHeaderConfig(
            userAgent = "  Jupiter/1.0\r\n  ",
            referer = "https://ref.example.com/\n",
            headers = linkedMapOf(
                "Authorization\r\n" to "Bearer\n tok123",
                "   " to "ignored",
                "X-Trace" to "42",
            ),
        )
        assertTrue(dirty.hasAny())
        val json = dirty.toJsonOrNull()
        assertTrue(json != null)

        val parsed = StreamHeaderConfig.fromJson(json)
        assertEquals("Jupiter/1.0", parsed.userAgent)
        assertEquals("https://ref.example.com/", parsed.referer)
        assertEquals(mapOf("Authorization" to "Bearer tok123", "X-Trace" to "42"), parsed.headers)

        val empty = StreamHeaderConfig(userAgent = "  ", referer = null, headers = mapOf("" to "x"))
        assertFalse(empty.hasAny())
        assertNull(empty.toJsonOrNull())
        assertEquals(StreamHeaderConfig(), StreamHeaderConfig.fromJson("not-json"))

        val encodedMap = StreamHeaderConfig.encodeHeadersMap(mapOf("X-A\r\n" to "1\n2", "" to "skip"))
        assertEquals(mapOf("X-A" to "12"), StreamHeaderConfig.parseHeadersMap(encodedMap))
        assertNull(StreamHeaderConfig.encodeHeadersMap(mapOf("  " to "val")))
    }

    @Test
    fun `DefaultStreamRepository supports CRUD and sorts pinned first then recent`() = runTest {
        var now = 1_000L
        val dao = FakeNetworkStreamDao()
        val repo = DefaultStreamRepository(dao, clock = { now })

        // Invalid URL fails without inserting
        val badResult = repo.saveStream(title = "Bad", url = "file:///local.mp4")
        assertTrue(badResult.isFailure)
        assertTrue(repo.observeStreams().first().isEmpty())

        now = 1_000L
        val id1 = repo.saveStream(
            title = "",
            url = "https://cdn.example.com/a.m3u8",
            userAgent = "UA-1\r\n",
            customHeaders = mapOf("X-One" to "1"),
        ).getOrThrow()

        now = 2_000L
        val id2 = repo.saveStream(
            title = "Channel 2",
            url = "rtsp://10.0.0.5:554/live",
            isPinned = false,
        ).getOrThrow()

        now = 3_000L
        val id3 = repo.saveStream(
            title = "Pinned News",
            url = "https://cdn.example.com/news.m3u8",
            isPinned = true,
        ).getOrThrow()

        // Pinned first (id3), then most recent created/played (id2 at 2000, id1 at 1000)
        assertEquals(listOf(id3, id2, id1), repo.observeStreams().first().map { it.id })
        assertEquals("a.m3u8", repo.getStream(id1)?.title)
        assertEquals("UA-1", repo.getStream(id1)?.userAgent)

        // Playing id1 makes it more recent than id2, still below pinned id3
        now = 5_000L
        repo.recordPlayed(id1)
        assertEquals(listOf(id3, id1, id2), repo.observeStreams().first().map { it.id })

        // Pinning id2 puts it above unpinned id1 and above id3 (since id2 created/played=2000 < id3=3000, id3 stays first)
        repo.setPinned(id2, true)
        assertEquals(listOf(id3, id2, id1), repo.observeStreams().first().map { it.id })

        // Editing id1 updates title and headers while preserving createdAt/lastPlayed
        val updatedId = repo.saveStream(
            id = id1,
            title = "Updated Stream",
            url = "HTTPS://cdn.example.com/a-v2.m3u8",
            referer = "https://player.example.com",
            customHeaders = mapOf("X-Two" to "2"),
            isPinned = false,
        ).getOrThrow()
        assertEquals(id1, updatedId)
        val reloaded = repo.getStream(id1)!!
        assertEquals("Updated Stream", reloaded.title)
        assertEquals("https://cdn.example.com/a-v2.m3u8", reloaded.url)
        assertEquals(1_000L, reloaded.createdAtEpochMs)
        assertEquals(5_000L, reloaded.lastPlayedEpochMs)
        assertEquals(mapOf("X-Two" to "2"), StreamHeaderConfig.parseHeadersMap(reloaded.customHeadersJson))

        // Delete removes stream
        repo.deleteStream(id2)
        assertNull(repo.getStream(id2))
        assertEquals(listOf(id3, id1), repo.observeStreams().first().map { it.id })
    }

    private class FakeNetworkStreamDao : NetworkStreamDao {
        private var nextId = 1L
        private val items = MutableStateFlow<Map<Long, NetworkStreamEntity>>(emptyMap())

        override fun observeAll(): Flow<List<NetworkStreamEntity>> =
            items.map { it.values.toList() }

        override suspend fun getById(id: Long): NetworkStreamEntity? = items.value[id]

        override suspend fun upsert(entity: NetworkStreamEntity): Long {
            val isInsert = entity.id == 0L || !items.value.containsKey(entity.id)
            val assignedId = if (entity.id == 0L) nextId++ else entity.id
            val stored = entity.copy(id = assignedId)
            items.value = items.value + (assignedId to stored)
            return if (isInsert) assignedId else -1L
        }

        override suspend fun deleteById(id: Long) {
            items.value = items.value - id
        }

        override suspend fun updateLastPlayed(id: Long, epochMs: Long) {
            val current = items.value[id] ?: return
            items.value = items.value + (id to current.copy(lastPlayedEpochMs = epochMs))
        }

        override suspend fun setPinned(id: Long, pinned: Boolean) {
            val current = items.value[id] ?: return
            items.value = items.value + (id to current.copy(isPinned = pinned))
        }
    }
}
