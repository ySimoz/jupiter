package com.jupiter.app.data.repository

import com.jupiter.app.data.local.VideoBookmarkDao
import com.jupiter.app.data.local.VideoBookmarkEntity
import com.jupiter.app.data.media.UriHash
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoBookmarkRepositoryTest {

    @Test
    fun `DefaultVideoBookmarkRepository adds, orders by positionMs, renames, and deletes bookmarks`() = runTest {
        var now = 1_000L
        val dao = FakeVideoBookmarkDao()
        val repo = DefaultVideoBookmarkRepository(videoBookmarkDao = dao, clock = { now })
        val uriA = "content://media/external/video/media/101"
        val uriB = "content://media/external/video/media/202"

        val idLate = repo.addBookmark(uriA, positionMs = 95_000L, label = "  Climax  ")
        now = 2_000L
        val idEarly = repo.addBookmark(uriA, positionMs = 12_000L, label = "Intro")
        now = 3_000L
        val idFallback = repo.addBookmark(uriA, positionMs = 65_000L, label = "   ")
        now = 4_000L
        repo.addBookmark(uriB, positionMs = 30_000L, label = "Other video")

        val listA = repo.observeBookmarks(uriA).first()
        assertEquals(listOf(idEarly, idFallback, idLate), listA.map { it.id })
        assertEquals(listOf("Intro", "01:05", "Climax"), listA.map { it.label })
        assertEquals(UriHash.of(uriA), listA.first().uriHash)

        repo.renameBookmark(idEarly, "  Cold Open  ")
        repo.renameBookmark(idEarly, "   ") // blank rename ignored
        assertEquals("Cold Open", repo.observeBookmarks(uriA).first().first().label)

        repo.deleteBookmark(idFallback)
        val afterDelete = repo.observeBookmarks(uriA).first()
        assertEquals(listOf(idEarly, idLate), afterDelete.map { it.id })

        // Other video's bookmarks remain untouched
        assertEquals(listOf("Other video"), repo.observeBookmarks(uriB).first().map { it.label })
    }

    @Test
    fun `DefaultVideoBookmarkRepository handles blank uri and negative position safely`() = runTest {
        val dao = FakeVideoBookmarkDao()
        val repo = DefaultVideoBookmarkRepository(videoBookmarkDao = dao, clock = { 100L })

        assertEquals(0L, repo.addBookmark("   ", positionMs = 10_000L, label = "Ignored"))
        assertTrue(repo.observeBookmarks("").first().isEmpty())

        val id = repo.addBookmark("https://example.com/v.mp4", positionMs = -5_000L, label = "")
        val saved = repo.observeBookmarks("https://example.com/v.mp4").first().single()
        assertEquals(id, saved.id)
        assertEquals(0L, saved.positionMs)
        assertEquals("00:00", saved.label)

        // Invalid IDs do not mutate or throw
        repo.renameBookmark(0L, "Nope")
        repo.deleteBookmark(-1L)
        assertEquals(1, repo.observeBookmarks("https://example.com/v.mp4").first().size)
    }

    @Test
    fun `InMemoryVideoBookmarkRepository mirrors DefaultVideoBookmarkRepository behavior`() = runTest {
        var now = 500L
        val repo = InMemoryVideoBookmarkRepository(clock = { now })
        val uri = "content://media/external/video/media/77"

        val id2 = repo.addBookmark(uri, positionMs = 3_661_000L, label = "")
        now = 600L
        val id1 = repo.addBookmark(uri, positionMs = 5_000L, label = "Start")

        val observed = repo.observeBookmarks(uri).first()
        assertEquals(listOf(id1, id2), observed.map { it.id })
        assertEquals(listOf("Start", "1:01:01"), observed.map { it.label })

        repo.renameBookmark(id2, "Act 2")
        assertEquals("Act 2", repo.observeBookmarks(uri).first()[1].label)

        repo.deleteBookmark(id1)
        assertEquals(listOf(id2), repo.observeBookmarks(uri).first().map { it.id })
    }

    private class FakeVideoBookmarkDao : VideoBookmarkDao {
        private var nextId = 1L
        private val rows = MutableStateFlow<List<VideoBookmarkEntity>>(emptyList())

        override fun observeByUriHash(uriHash: String): Flow<List<VideoBookmarkEntity>> =
            rows.map { list ->
                list.filter { it.uriHash == uriHash }
                    .sortedWith(compareBy<VideoBookmarkEntity> { it.positionMs }.thenBy { it.id })
            }

        override suspend fun insert(entity: VideoBookmarkEntity): Long {
            val id = nextId++
            rows.value = rows.value + entity.copy(id = id)
            return id
        }

        override suspend fun rename(id: Long, label: String) {
            rows.value = rows.value.map { entity ->
                if (entity.id == id) entity.copy(label = label) else entity
            }
        }

        override suspend fun deleteById(id: Long) {
            rows.value = rows.value.filterNot { it.id == id }
        }
    }
}
