package com.jupiter.app.data.repository

import com.jupiter.app.data.local.InMemoryLibraryFolderPreferences
import com.jupiter.app.data.local.MediaHistoryDao
import com.jupiter.app.data.local.MediaHistoryEntity
import com.jupiter.app.data.media.LocalMediaScanner
import com.jupiter.app.data.media.LocalVideoItem
import com.jupiter.app.data.media.SafFolderScanner
import com.jupiter.app.data.media.UriHash
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaRepositoryTest {

    @Test
    fun `UriHash produces deterministic 64-char sha256 hex`() {
        val hashA1 = UriHash.of("content://media/external/video/media/101")
        val hashA2 = UriHash.of("content://media/external/video/media/101")
        val hashB = UriHash.of("content://media/external/video/media/102")

        assertEquals(64, hashA1.length)
        assertTrue(hashA1.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(hashA1, hashA2)
        assertNotEquals(hashA1, hashB)
    }

    @Test
    fun `ResumeRules normalizes invalid, early, mid-video, and near-completion positions`() {
        assertEquals(0L, ResumeRules.normalizePositionMs(10_000L, 0L))
        assertEquals(0L, ResumeRules.normalizePositionMs(10_000L, -5L))
        assertEquals(0L, ResumeRules.normalizePositionMs(0L, 100_000L))
        assertEquals(0L, ResumeRules.normalizePositionMs(999L, 100_000L))
        assertEquals(1_000L, ResumeRules.normalizePositionMs(1_000L, 100_000L))
        assertEquals(3_000L, ResumeRules.normalizePositionMs(3_000L, 100_000L))
        assertEquals(45_000L, ResumeRules.normalizePositionMs(45_000L, 100_000L))
        assertEquals(95_000L, ResumeRules.normalizePositionMs(95_000L, 100_000L))
        assertEquals(0L, ResumeRules.normalizePositionMs(95_001L, 100_000L))
        assertEquals(0L, ResumeRules.normalizePositionMs(100_000L, 100_000L))
    }

    @Test
    fun `saveProgress preserves existing resume position on sub-1s save unless completed`() = runTest {
        val fakeDao = FakeMediaHistoryDao()
        var now = 1_000L
        val repo = DefaultMediaRepository(
            scanner = FakeLocalMediaScanner(emptyList()),
            mediaHistoryDao = fakeDao,
            clock = { now },
        )
        val uri = "content://media/external/video/media/42"

        repo.saveProgress(
            uri = uri,
            displayName = "Episode.mkv",
            positionMs = 45_000L,
            durationMs = 120_000L,
        )
        assertEquals(45_000L, repo.getHistory(uri)?.positionMs)

        // Sub-1s save (e.g. exiting before start seek settled) must preserve 45_000L
        now = 2_000L
        repo.saveProgress(
            uri = uri,
            displayName = "Episode.mkv",
            positionMs = 0L,
            durationMs = 120_000L,
        )
        assertEquals(45_000L, repo.getHistory(uri)?.positionMs)
        assertEquals(listOf("Episode.mkv"), repo.observeContinueWatching().first().map { it.displayName })

        now = 3_000L
        repo.saveProgress(
            uri = uri,
            displayName = "Episode.mkv",
            positionMs = 800L,
            durationMs = 120_000L,
        )
        assertEquals(45_000L, repo.getHistory(uri)?.positionMs)

        // >95% completion must still reset to 0L
        now = 4_000L
        repo.saveProgress(
            uri = uri,
            displayName = "Episode.mkv",
            positionMs = 116_000L,
            durationMs = 120_000L,
        )
        assertEquals(0L, repo.getHistory(uri)?.positionMs)
        assertTrue(repo.observeContinueWatching().first().isEmpty())
    }

    @Test
    fun `DefaultMediaRepository scans local videos and manages watch history`() = runTest {
        val sampleVideo = LocalVideoItem(
            id = 7L,
            contentUri = "content://media/external/video/media/7",
            displayName = "Clip.mp4",
            bucketName = "Camera",
            durationMs = 120_000L,
            sizeBytes = 10_485_760L,
            dateModifiedSec = 1_700_000_000L,
            width = 1920,
            height = 1080,
        )
        val fakeDao = FakeMediaHistoryDao()
        var now = 1_000L
        val repo = DefaultMediaRepository(
            scanner = FakeLocalMediaScanner(listOf(sampleVideo)),
            mediaHistoryDao = fakeDao,
            clock = { now },
        )

        assertEquals(listOf(sampleVideo), repo.scanLocalVideos())

        repo.saveProgress(
            uri = "content://media/external/video/media/7",
            displayName = "Clip.mp4",
            positionMs = 30_000L,
            durationMs = 120_000L,
            playbackSpeed = 1.25f,
        )
        now = 2_000L
        repo.saveProgress(
            uri = "https://example.com/live.m3u8",
            displayName = "Live",
            positionMs = 15_000L,
            durationMs = 60_000L,
            isNetworkStream = true,
        )

        val saved = repo.getHistory("content://media/external/video/media/7")
        assertEquals(30_000L, saved?.positionMs)
        assertEquals(1.25f, saved?.playbackSpeed ?: 0f, 0.001f)

        val recent = repo.observeContinueWatching(limit = 10).first()
        assertEquals(listOf("Live", "Clip.mp4"), recent.map { it.displayName })

        // Watching past 95% resets resume position to 0 and hides it from Continue Watching
        now = 3_000L
        repo.saveProgress(
            uri = "content://media/external/video/media/7",
            displayName = "Clip.mp4",
            positionMs = 116_000L,
            durationMs = 120_000L,
        )
        assertEquals(0L, repo.getHistory("content://media/external/video/media/7")?.positionMs)
        assertEquals(listOf("Live"), repo.observeContinueWatching().first().map { it.displayName })

        repo.removeHistory("https://example.com/live.m3u8")
        assertNull(repo.getHistory("https://example.com/live.m3u8"))

        repo.clearHistory()
        assertTrue(fakeDao.observeAll().first().isEmpty())
    }

    @Test
    fun `DefaultMediaRepository merges MediaStore and SAF videos deduplicating by URI and manages folder prefs`() = runTest {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AAnime"
        val mediaStoreVideo = LocalVideoItem(
            id = 1L,
            contentUri = "content://media/external/video/media/1",
            displayName = "Movie.mkv",
            bucketName = "Movies",
            durationMs = 600_000L,
            sizeBytes = 800_000_000L,
            dateModifiedSec = 1_700_000_100L,
            width = 1920,
            height = 1080,
        )
        val duplicateSafVideo = mediaStoreVideo.copy(
            id = -1L,
            sourceTreeUri = treeUri,
        )
        val uniqueSafVideo = LocalVideoItem(
            id = -2L,
            contentUri = "$treeUri/document/primary%3AAnime%2Fep1.mkv",
            displayName = "ep1.mkv",
            bucketName = "Anime",
            durationMs = 0L,
            sizeBytes = 350_000_000L,
            dateModifiedSec = 1_700_000_200L,
            width = 0,
            height = 0,
            sourceTreeUri = treeUri,
        )
        val folderPrefs = InMemoryLibraryFolderPreferences()
        val safScanner = FakeSafFolderScanner(
            videosByTree = mapOf(treeUri to listOf(duplicateSafVideo, uniqueSafVideo)),
            namesByTree = mapOf(treeUri to "Anime"),
        )
        val repo = DefaultMediaRepository(
            scanner = FakeLocalMediaScanner(listOf(mediaStoreVideo)),
            mediaHistoryDao = FakeMediaHistoryDao(),
            folderPreferences = folderPrefs,
            safScanner = safScanner,
        )

        repo.addCustomFolder(treeUri)
        assertEquals(setOf(treeUri), repo.observeCustomFolderUris().first())
        assertEquals("Anime", repo.resolveCustomFolderName(treeUri))

        val merged = repo.scanLocalVideos()
        assertEquals(listOf(mediaStoreVideo, uniqueSafVideo), merged)
        assertEquals(listOf(duplicateSafVideo, uniqueSafVideo), repo.scanCustomFolderVideos())

        repo.setFolderHidden("Movies", true)
        assertEquals(setOf("Movies"), repo.observeHiddenFolders().first())

        repo.setFolderHidden("Movies", false)
        assertTrue(repo.observeHiddenFolders().first().isEmpty())

        repo.setFolderRemoved("Movies", true)
        assertEquals(setOf("Movies"), repo.observeRemovedFolders().first())

        repo.setFolderRemoved("Movies", false)
        assertTrue(repo.observeRemovedFolders().first().isEmpty())

        repo.removeCustomFolder(treeUri)
        assertTrue(repo.observeCustomFolderUris().first().isEmpty())
    }

    @Test
    fun `scanLocalVideos deduplicates overlapping MediaStore and SAF items by bucket displayName and size`() = runTest {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMovies"
        val mediaStoreMovie = LocalVideoItem(
            id = 10L,
            contentUri = "content://media/external/video/media/10",
            displayName = "Feature.mkv",
            bucketName = "Movies",
            durationMs = 540_000L,
            sizeBytes = 1_500_000_000L,
            dateModifiedSec = 1_700_000_100L,
            width = 1920,
            height = 1080,
        )
        val overlappingSafMovie = LocalVideoItem(
            id = -10L,
            contentUri = "$treeUri/document/primary%3AMovies%2FFeature.mkv",
            displayName = "feature.mkv",
            bucketName = "movies",
            durationMs = 0L,
            sizeBytes = 1_500_000_000L,
            dateModifiedSec = 1_700_000_100L,
            width = 0,
            height = 0,
            sourceTreeUri = treeUri,
        )
        val safOnlyMovie = LocalVideoItem(
            id = -11L,
            contentUri = "$treeUri/document/primary%3AMovies%2FExtras.mkv",
            displayName = "Extras.mkv",
            bucketName = "Movies",
            durationMs = 0L,
            sizeBytes = 250_000_000L,
            dateModifiedSec = 1_700_000_200L,
            width = 0,
            height = 0,
            sourceTreeUri = treeUri,
        )
        val folderPrefs = InMemoryLibraryFolderPreferences()
        val safScanner = FakeSafFolderScanner(
            videosByTree = mapOf(treeUri to listOf(overlappingSafMovie, safOnlyMovie)),
            namesByTree = mapOf(treeUri to "Movies"),
        )
        val repo = DefaultMediaRepository(
            scanner = FakeLocalMediaScanner(listOf(mediaStoreMovie)),
            mediaHistoryDao = FakeMediaHistoryDao(),
            folderPreferences = folderPrefs,
            safScanner = safScanner,
        )

        repo.addCustomFolder(treeUri)

        val scanned = repo.scanLocalVideos()
        assertEquals(listOf(mediaStoreMovie, safOnlyMovie), scanned)
    }

    private class FakeLocalMediaScanner(
        private val items: List<LocalVideoItem>,
    ) : LocalMediaScanner {
        override suspend fun scanVideos(): List<LocalVideoItem> = items
    }

    private class FakeSafFolderScanner(
        private val videosByTree: Map<String, List<LocalVideoItem>>,
        private val namesByTree: Map<String, String>,
    ) : SafFolderScanner {
        override suspend fun scanFolders(treeUris: Set<String>): List<LocalVideoItem> =
            treeUris.flatMap { videosByTree[it].orEmpty() }

        override fun resolveFolderDisplayName(treeUri: String): String =
            namesByTree[treeUri] ?: "Folder"
    }

    private class FakeMediaHistoryDao : MediaHistoryDao {
        private val rows = MutableStateFlow<Map<String, MediaHistoryEntity>>(emptyMap())

        override fun observeRecent(limit: Int): Flow<List<MediaHistoryEntity>> =
            rows.map { map ->
                map.values
                    .filter { it.positionMs > 0L }
                    .sortedByDescending { it.lastPlayedEpochMs }
                    .take(limit)
            }

        override fun observeAll(): Flow<List<MediaHistoryEntity>> =
            rows.map { it.values.sortedByDescending { entity -> entity.lastPlayedEpochMs } }

        override suspend fun getByHash(uriHash: String): MediaHistoryEntity? =
            rows.value[uriHash]

        override suspend fun upsert(entity: MediaHistoryEntity) {
            rows.value = rows.value + (entity.uriHash to entity)
        }

        override suspend fun deleteByHash(uriHash: String) {
            rows.value = rows.value - uriHash
        }

        override suspend fun clearAll() {
            rows.value = emptyMap()
        }
    }
}
