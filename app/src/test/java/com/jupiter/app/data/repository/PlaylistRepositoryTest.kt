package com.jupiter.app.data.repository

import com.jupiter.app.data.local.PlaylistDao
import com.jupiter.app.data.local.PlaylistEntity
import com.jupiter.app.data.local.PlaylistItemEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistRepositoryTest {

    @Test
    fun `DefaultPlaylistRepository creates, renames, deletes playlists and computes summaries`() = runTest {
        var now = 1_000L
        val dao = FakePlaylistDao()
        val repo = DefaultPlaylistRepository(playlistDao = dao, clock = { now })

        val p1 = repo.createPlaylist("  Watch Later  ")
        now = 2_000L
        val p2 = repo.createPlaylist("Streams Only")
        now = 3_000L
        val p3 = repo.createPlaylist("   ")

        val initialSummaries = repo.observePlaylists().first()
        assertEquals(listOf("Untitled playlist", "Streams Only", "Watch Later"), initialSummaries.map { it.name })
        assertTrue(initialSummaries.all { it.itemCount == 0 && it.previewUri == null })

        now = 4_000L
        repo.renamePlaylist(p1, "  Favorites  ")
        repo.renamePlaylist(p1, "   ") // blank rename is ignored
        repo.deletePlaylist(p3)

        val updated = repo.observePlaylists().first()
        assertEquals(listOf(p1, p2), updated.map { it.id })
        assertEquals("Favorites", updated.first().name)
        assertEquals(4_000L, updated.first().updatedAtEpochMs)
    }

    @Test
    fun `DefaultPlaylistRepository summary counts videos vs streams and sets previewUri from first non-stream`() = runTest {
        var now = 10_000L
        val dao = FakePlaylistDao()
        val repo = DefaultPlaylistRepository(playlistDao = dao, clock = { now })

        val mixedId = repo.createPlaylist("Mixed")
        now = 11_000L
        repo.addItems(
            mixedId,
            listOf(
                PlaylistMediaInput(
                    mediaUri = "https://example.com/live.m3u8",
                    title = "Live Stream",
                    isNetworkStream = true,
                    headersJson = """{"userAgent":"Jupiter"}""",
                ),
                PlaylistMediaInput(
                    mediaUri = "content://media/external/video/media/10",
                    title = "Episode 1",
                    isNetworkStream = false,
                    durationMs = 120_000L,
                ),
                PlaylistMediaInput(
                    mediaUri = "content://media/external/video/media/11",
                    title = "Episode 2",
                    isNetworkStream = false,
                    durationMs = 95_000L,
                ),
            ),
        )

        val streamsOnlyId = repo.createPlaylist("Radio")
        now = 12_000L
        repo.addItem(
            streamsOnlyId,
            PlaylistMediaInput(
                mediaUri = "https://example.com/stream.ts",
                title = "News",
                isNetworkStream = true,
            ),
        )

        val summaries = repo.observePlaylists().first().associateBy { it.id }
        val mixed = summaries.getValue(mixedId)
        assertEquals(3, mixed.itemCount)
        assertEquals(2, mixed.videoCount)
        assertEquals(1, mixed.streamCount)
        assertEquals("content://media/external/video/media/10", mixed.previewUri)

        val streamsOnly = summaries.getValue(streamsOnlyId)
        assertEquals(1, streamsOnly.itemCount)
        assertEquals(0, streamsOnly.videoCount)
        assertEquals(1, streamsOnly.streamCount)
        assertNull(streamsOnly.previewUri)
    }

    @Test
    fun `DefaultPlaylistRepository keeps positionOrder contiguous after add, remove, and moveItem`() = runTest {
        var now = 1_000L
        val dao = FakePlaylistDao()
        val repo = DefaultPlaylistRepository(playlistDao = dao, clock = { now })

        val pid = repo.createPlaylist("Queue")
        repo.addItems(
            pid,
            listOf(
                PlaylistMediaInput("content://video/1", "One"),
                PlaylistMediaInput("content://video/2", "Two"),
                PlaylistMediaInput("content://video/3", "Three"),
            ),
        )
        now = 2_000L
        repo.addItem(pid, PlaylistMediaInput("content://video/4", "Four"))

        var items = repo.getPlaylistItems(pid)
        assertEquals(listOf("One", "Two", "Three", "Four"), items.map { it.title })
        assertEquals(listOf(0, 1, 2, 3), items.map { it.positionOrder })

        // Boundary moves should be no-ops
        val firstId = items.first().id
        val lastId = items.last().id
        repo.moveItem(pid, firstId, moveUp = true)
        repo.moveItem(pid, lastId, moveUp = false)
        assertEquals(listOf("One", "Two", "Three", "Four"), repo.getPlaylistItems(pid).map { it.title })

        // Move "Three" up, then "One" down
        val thirdId = items[2].id
        now = 3_000L
        repo.moveItem(pid, thirdId, moveUp = true)
        assertEquals(listOf("One", "Three", "Two", "Four"), repo.observePlaylistItems(pid).first().map { it.title })
        assertEquals(listOf(0, 1, 2, 3), repo.getPlaylistItems(pid).map { it.positionOrder })

        now = 4_000L
        repo.moveItem(pid, firstId, moveUp = false)
        assertEquals(listOf("Three", "One", "Two", "Four"), repo.getPlaylistItems(pid).map { it.title })

        // Remove middle item and verify contiguous 0..n-1 reindexing
        now = 5_000L
        repo.removeItem(firstId) // removes "One"
        items = repo.getPlaylistItems(pid)
        assertEquals(listOf("Three", "Two", "Four"), items.map { it.title })
        assertEquals(listOf(0, 1, 2), items.map { it.positionOrder })
        assertEquals(5_000L, repo.observePlaylists().first().single().updatedAtEpochMs)
    }

    @Test
    fun `DefaultPlaylistRepository ignores blank mediaUri and invalid ids`() = runTest {
        val dao = FakePlaylistDao()
        val repo = DefaultPlaylistRepository(playlistDao = dao, clock = { 500L })

        val pid = repo.createPlaylist("Valid")
        repo.addItems(
            pid,
            listOf(
                PlaylistMediaInput("   ", "Blank"),
                PlaylistMediaInput("content://video/9", "   "),
            ),
        )
        val items = repo.getPlaylistItems(pid)
        assertEquals(1, items.size)
        assertEquals("content://video/9", items.single().title)

        // Invalid IDs do not crash or mutate
        repo.addItem(0L, PlaylistMediaInput("content://video/10", "Ignored"))
        repo.renamePlaylist(-1L, "Nope")
        repo.deletePlaylist(0L)
        repo.removeItem(999L)
        repo.moveItem(pid, 999L, moveUp = true)
        assertEquals(1, repo.getPlaylistItems(pid).size)
    }

    @Test
    fun `InMemoryPlaylistRepository mirrors DefaultPlaylistRepository behavior`() = runTest {
        var now = 100L
        val repo = InMemoryPlaylistRepository(clock = { now })

        val p1 = repo.createPlaylist("Trip")
        now = 200L
        repo.addItems(
            p1,
            listOf(
                PlaylistMediaInput("https://stream.test/a.m3u8", "Stream A", isNetworkStream = true),
                PlaylistMediaInput("content://video/a", "Clip A", durationMs = 5_000L),
                PlaylistMediaInput("content://video/b", "Clip B", durationMs = 8_000L),
            ),
        )
        var summary = repo.observePlaylists().first().single()
        assertEquals(3, summary.itemCount)
        assertEquals(2, summary.videoCount)
        assertEquals(1, summary.streamCount)
        assertEquals("content://video/a", summary.previewUri)

        val items = repo.getPlaylistItems(p1)
        now = 300L
        repo.moveItem(p1, items[2].id, moveUp = true)
        assertEquals(listOf("Stream A", "Clip B", "Clip A"), repo.observePlaylistItems(p1).first().map { it.title })

        now = 400L
        repo.removeItem(items[1].id) // removes Clip A
        val remaining = repo.getPlaylistItems(p1)
        assertEquals(listOf("Stream A", "Clip B"), remaining.map { it.title })
        assertEquals(listOf(0, 1), remaining.map { it.positionOrder })

        now = 500L
        repo.renamePlaylist(p1, "Road Trip")
        summary = repo.observePlaylists().first().single()
        assertEquals("Road Trip", summary.name)
        assertEquals(500L, summary.updatedAtEpochMs)

        repo.deletePlaylist(p1)
        assertTrue(repo.observePlaylists().first().isEmpty())
        assertTrue(repo.getPlaylistItems(p1).isEmpty())
    }

    private class FakePlaylistDao : PlaylistDao {
        private var nextPlaylistId = 1L
        private var nextItemId = 1L
        private val playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
        private val items = MutableStateFlow<List<PlaylistItemEntity>>(emptyList())

        override fun observePlaylists(): Flow<List<PlaylistEntity>> =
            playlists.map { list ->
                list.sortedWith(compareByDescending<PlaylistEntity> { it.updatedAtEpochMs }.thenByDescending { it.id })
            }

        override fun observeAllItems(): Flow<List<PlaylistItemEntity>> =
            items.map { list ->
                list.sortedWith(
                    compareBy<PlaylistItemEntity> { it.playlistId }
                        .thenBy { it.positionOrder }
                        .thenBy { it.id },
                )
            }

        override fun observeItemsForPlaylist(playlistId: Long): Flow<List<PlaylistItemEntity>> =
            items.map { list ->
                list.filter { it.playlistId == playlistId }
                    .sortedWith(compareBy<PlaylistItemEntity> { it.positionOrder }.thenBy { it.id })
            }

        override suspend fun getItemsForPlaylist(playlistId: Long): List<PlaylistItemEntity> =
            items.value
                .filter { it.playlistId == playlistId }
                .sortedWith(compareBy<PlaylistItemEntity> { it.positionOrder }.thenBy { it.id })

        override suspend fun getMaxOrder(playlistId: Long): Int? =
            items.value.filter { it.playlistId == playlistId }.maxOfOrNull { it.positionOrder }

        override suspend fun getPlaylistIdForItem(itemId: Long): Long? =
            items.value.firstOrNull { it.id == itemId }?.playlistId

        override suspend fun insertPlaylist(entity: PlaylistEntity): Long {
            val id = nextPlaylistId++
            playlists.value = playlists.value + entity.copy(id = id)
            return id
        }

        override suspend fun renamePlaylist(playlistId: Long, newName: String, updatedAtEpochMs: Long) {
            playlists.value = playlists.value.map { playlist ->
                if (playlist.id == playlistId) {
                    playlist.copy(name = newName, updatedAtEpochMs = updatedAtEpochMs)
                } else {
                    playlist
                }
            }
        }

        override suspend fun touchPlaylist(playlistId: Long, updatedAtEpochMs: Long) {
            playlists.value = playlists.value.map { playlist ->
                if (playlist.id == playlistId) {
                    playlist.copy(updatedAtEpochMs = updatedAtEpochMs)
                } else {
                    playlist
                }
            }
        }

        override suspend fun deletePlaylist(playlistId: Long) {
            playlists.value = playlists.value.filterNot { it.id == playlistId }
            items.value = items.value.filterNot { it.playlistId == playlistId }
        }

        override suspend fun insertItems(items: List<PlaylistItemEntity>): List<Long> {
            val ids = mutableListOf<Long>()
            val inserted = items.map { entity ->
                val id = nextItemId++
                ids += id
                entity.copy(id = id)
            }
            this.items.value = this.items.value + inserted
            return ids
        }

        override suspend fun updateItemOrder(itemId: Long, positionOrder: Int) {
            items.value = items.value.map { item ->
                if (item.id == itemId) item.copy(positionOrder = positionOrder) else item
            }
        }

        override suspend fun deleteItem(itemId: Long) {
            items.value = items.value.filterNot { it.id == itemId }
        }
    }
}
