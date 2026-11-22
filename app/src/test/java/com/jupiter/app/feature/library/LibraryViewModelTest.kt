package com.jupiter.app.feature.library

import com.jupiter.app.data.local.MediaHistoryEntity
import com.jupiter.app.data.media.LibrarySortOption
import com.jupiter.app.data.media.LocalFolderSummary
import com.jupiter.app.data.media.LocalVideoItem
import com.jupiter.app.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeMediaRepository
    private lateinit var viewModel: LibraryViewModel

    private val sampleVideos = listOf(
        LocalVideoItem(
            id = 1L,
            contentUri = "content://media/external/video/media/1",
            displayName = "Big Buck Bunny.mkv",
            bucketName = "Movies",
            durationMs = 596_000L,
            sizeBytes = 1_200_000_000L,
            dateModifiedSec = 1_700_000_200L,
            width = 1920,
            height = 1080,
        ),
        LocalVideoItem(
            id = 2L,
            contentUri = "content://media/external/video/media/2",
            displayName = "Zulu Trailer.mp4",
            bucketName = "Downloads",
            durationMs = 120_000L,
            sizeBytes = 95_000_000L,
            dateModifiedSec = 1_700_000_300L,
            width = 1280,
            height = 720,
        ),
        LocalVideoItem(
            id = 3L,
            contentUri = "content://media/external/video/media/3",
            displayName = "Alpha Sintel.mkv",
            bucketName = "Movies",
            durationMs = 888_000L,
            sizeBytes = 900_000_000L,
            dateModifiedSec = 1_700_000_100L,
            width = 3840,
            height = 2160,
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeMediaRepository(videos = sampleVideos)
        viewModel = LibraryViewModel(mediaRepository = fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onPermissionResult granted scans videos and groups folders by count then name`() = runTest {
        viewModel.onPermissionResult(true)
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.hasPermission)
        assertFalse(state.isLoading)
        assertEquals(2, state.folders.size)
        assertEquals("Movies", state.folders[0].name)
        assertEquals(2, state.folders[0].videoCount)
        assertEquals("content://media/external/video/media/1", state.folders[0].previewUri)
        assertEquals("Downloads", state.folders[1].name)
        assertEquals(1, state.folders[1].videoCount)
    }

    @Test
    fun `selectFolder filters visibleVideos and clearing returns to root folders`() = runTest {
        viewModel.onPermissionResult(true)
        runCurrent()

        viewModel.selectFolder("Movies")
        assertEquals("Movies", viewModel.uiState.value.selectedFolder)
        assertEquals(listOf(1L, 3L), viewModel.uiState.value.visibleVideos.map { it.id })

        viewModel.selectFolder(null)
        assertNull(viewModel.uiState.value.selectedFolder)
        assertEquals(listOf(2L, 1L, 3L), viewModel.uiState.value.visibleVideos.map { it.id })
    }

    @Test
    fun `onSearchQueryChange filters case-insensitively across videos and folders`() = runTest {
        viewModel.onPermissionResult(true)
        runCurrent()
        viewModel.setViewMode(LibraryViewMode.ALL_VIDEOS)

        viewModel.onSearchQueryChange("  sInTeL ")
        val state = viewModel.uiState.value
        assertEquals(listOf(3L), state.visibleVideos.map { it.id })
        assertEquals(1, state.folders.size)
        assertEquals("Movies", state.folders.first().name)
        assertEquals(1, state.folders.first().videoCount)
    }

    @Test
    fun `setSortOption sorts visibleVideos by DATE NAME DURATION and SIZE`() = runTest {
        viewModel.onPermissionResult(true)
        runCurrent()
        viewModel.setViewMode(LibraryViewMode.ALL_VIDEOS)

        // DATE (descending dateModifiedSec): 2 (300), 1 (200), 3 (100)
        viewModel.setSortOption(LibrarySortOption.DATE)
        assertEquals(listOf(2L, 1L, 3L), viewModel.uiState.value.visibleVideos.map { it.id })

        // NAME (ascending alpha): Alpha Sintel (3), Big Buck Bunny (1), Zulu Trailer (2)
        viewModel.setSortOption(LibrarySortOption.NAME)
        assertEquals(listOf(3L, 1L, 2L), viewModel.uiState.value.visibleVideos.map { it.id })

        // DURATION (descending): Alpha Sintel (888s -> 3), Big Buck Bunny (596s -> 1), Zulu (120s -> 2)
        viewModel.setSortOption(LibrarySortOption.DURATION)
        assertEquals(listOf(3L, 1L, 2L), viewModel.uiState.value.visibleVideos.map { it.id })

        // SIZE (descending): Big Buck Bunny (1200MB -> 1), Alpha Sintel (900MB -> 3), Zulu (95MB -> 2)
        viewModel.setSortOption(LibrarySortOption.SIZE)
        assertEquals(listOf(1L, 3L, 2L), viewModel.uiState.value.visibleVideos.map { it.id })
    }

    @Test
    fun `continueWatching updates from repository and removeFromHistory delegates to repository`() = runTest {
        val entry = MediaHistoryEntity(
            uriHash = "hash-1",
            uri = "content://media/external/video/media/1",
            displayName = "Big Buck Bunny.mkv",
            durationMs = 596_000L,
            positionMs = 120_000L,
            lastPlayedEpochMs = 1_700_000_500L,
        )
        fakeRepository.emitContinueWatching(listOf(entry))
        runCurrent()

        assertEquals(listOf(entry), viewModel.uiState.value.continueWatching)

        viewModel.removeFromHistory(entry.uri)
        runCurrent()
        assertEquals(listOf(entry.uri), fakeRepository.removedUris)
        assertTrue(viewModel.uiState.value.continueWatching.isEmpty())
    }

    @Test
    fun `setFolderHidden excludes folder from folders and visibleVideos while keeping allDetectedFolders`() = runTest {
        viewModel.onPermissionResult(true)
        runCurrent()
        viewModel.selectFolder("Movies")

        viewModel.setFolderHidden("Movies", true)
        runCurrent()

        val hiddenState = viewModel.uiState.value
        assertTrue("Movies" in hiddenState.hiddenFolders)
        assertNull(hiddenState.selectedFolder)
        assertEquals(listOf("Downloads"), hiddenState.folders.map { it.name })
        assertEquals(listOf(2L), hiddenState.visibleVideos.map { it.id })
        assertEquals(setOf("Movies", "Downloads"), hiddenState.allDetectedFolders.map { it.name }.toSet())

        viewModel.setFolderHidden("Movies", false)
        runCurrent()

        val restoredState = viewModel.uiState.value
        assertFalse("Movies" in restoredState.hiddenFolders)
        assertEquals(listOf("Movies", "Downloads"), restoredState.folders.map { it.name })
        assertEquals(listOf(2L, 1L, 3L), restoredState.visibleVideos.map { it.id })
    }

    @Test
    fun `addCustomFolder and removeCustomFolder work even without MediaStore permission`() = runTest {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AAnime"
        fakeRepository.customVideosByTree[treeUri] = listOf(
            LocalVideoItem(
                id = -101L,
                contentUri = "$treeUri/document/primary%3AAnime%2Fep1.mkv",
                displayName = "ep1.mkv",
                bucketName = "Anime",
                durationMs = 0L,
                sizeBytes = 500_000_000L,
                dateModifiedSec = 1_700_000_400L,
                width = 0,
                height = 0,
                sourceTreeUri = treeUri,
            ),
        )

        viewModel.onPermissionResult(false)
        runCurrent()
        assertTrue(viewModel.uiState.value.folders.isEmpty())

        viewModel.addCustomFolder(treeUri)
        runCurrent()

        val addedState = viewModel.uiState.value
        assertEquals(1, addedState.customFolders.size)
        assertEquals("Anime", addedState.customFolders.first().displayName)
        assertEquals(listOf("Anime"), addedState.folders.map { it.name })
        assertTrue(addedState.folders.first().isCustom)
        assertEquals(treeUri, addedState.folders.first().treeUri)
        assertEquals(listOf(-101L), addedState.visibleVideos.map { it.id })

        viewModel.removeCustomFolder(treeUri)
        runCurrent()

        val removedState = viewModel.uiState.value
        assertTrue(removedState.customFolders.isEmpty())
        assertTrue(removedState.folders.isEmpty())
        assertTrue(removedState.visibleVideos.isEmpty())
    }

    @Test
    fun `hiding unhiding and removing custom SAF folder updates hiddenFolderNames and customFolders`() = runTest {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AAnime"
        fakeRepository.customVideosByTree[treeUri] = listOf(
            LocalVideoItem(
                id = -101L,
                contentUri = "$treeUri/document/primary%3AAnime%2Fep1.mkv",
                displayName = "ep1.mkv",
                bucketName = "Anime",
                durationMs = 0L,
                sizeBytes = 500_000_000L,
                dateModifiedSec = 1_700_000_400L,
                width = 0,
                height = 0,
                sourceTreeUri = treeUri,
            ),
        )

        viewModel.onPermissionResult(true)
        viewModel.addCustomFolder(treeUri)
        runCurrent()

        assertEquals(listOf("Anime"), viewModel.uiState.value.customFolders.map { it.displayName })
        assertTrue("Anime" in viewModel.uiState.value.folders.map { it.name })

        // Hide custom SAF folder: stays in customFolders, appears in hiddenFolderNames, excluded from folders & visibleVideos
        viewModel.setFolderHidden("Anime", true)
        runCurrent()

        val hiddenState = viewModel.uiState.value
        assertEquals(listOf("Anime"), hiddenState.customFolders.map { it.displayName })
        assertEquals(listOf("Anime"), hiddenState.hiddenFolderNames)
        assertFalse("Anime" in hiddenState.folders.map { it.name })
        assertFalse(hiddenState.visibleVideos.any { it.bucketName == "Anime" })

        // Unhide custom SAF folder: removed from hiddenFolderNames, reappears in folders
        viewModel.setFolderHidden("Anime", false)
        runCurrent()

        val unhiddenState = viewModel.uiState.value
        assertTrue(unhiddenState.hiddenFolderNames.isEmpty())
        assertTrue("Anime" in unhiddenState.folders.map { it.name })
        assertTrue(unhiddenState.visibleVideos.any { it.bucketName == "Anime" })

        // Hide again, then remove custom folder: removed from both customFolders and hiddenFolderNames
        viewModel.setFolderHidden("Anime", true)
        runCurrent()
        assertEquals(listOf("Anime"), viewModel.uiState.value.hiddenFolderNames)

        viewModel.removeCustomFolder(treeUri)
        runCurrent()

        val removedState = viewModel.uiState.value
        assertTrue(removedState.customFolders.isEmpty())
        assertTrue(removedState.hiddenFolderNames.isEmpty())
        assertFalse("Anime" in removedState.hiddenFolders)
        assertFalse("Anime" in removedState.folders.map { it.name })
    }

    @Test
    fun `removing device folder excludes it from folders visibleVideos and hiddenFolderNames and restore brings it back`() = runTest {
        viewModel.onPermissionResult(true)
        runCurrent()
        viewModel.selectFolder("Movies")
        viewModel.setFolderHidden("Movies", true)
        runCurrent()
        assertEquals(listOf("Movies"), viewModel.uiState.value.hiddenFolderNames)

        val moviesFolder = viewModel.uiState.value.allDetectedFolders.first { it.name == "Movies" }
        viewModel.removeFolder(moviesFolder)
        runCurrent()

        val removedState = viewModel.uiState.value
        assertNull(removedState.selectedFolder)
        assertEquals(listOf("Movies"), removedState.removedFolderNames)
        assertTrue(removedState.hiddenFolderNames.isEmpty())
        assertFalse("Movies" in removedState.hiddenFolders)
        assertEquals(listOf("Downloads"), removedState.folders.map { it.name })
        assertEquals(listOf(2L), removedState.visibleVideos.map { it.id })

        viewModel.restoreRemovedFolder("Movies")
        runCurrent()

        val restoredState = viewModel.uiState.value
        assertTrue(restoredState.removedFolderNames.isEmpty())
        assertEquals(listOf("Movies", "Downloads"), restoredState.folders.map { it.name })
        assertEquals(listOf(2L, 1L, 3L), restoredState.visibleVideos.map { it.id })
    }

    @Test
    fun `removeFolder on custom SAF folder removes SAF URI without adding to removedFolderNames`() = runTest {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AAnime"
        fakeRepository.customVideosByTree[treeUri] = listOf(
            LocalVideoItem(
                id = -101L,
                contentUri = "$treeUri/document/primary%3AAnime%2Fep1.mkv",
                displayName = "ep1.mkv",
                bucketName = "Anime",
                durationMs = 0L,
                sizeBytes = 500_000_000L,
                dateModifiedSec = 1_700_000_400L,
                width = 0,
                height = 0,
                sourceTreeUri = treeUri,
            ),
        )

        viewModel.onPermissionResult(true)
        viewModel.addCustomFolder(treeUri)
        runCurrent()

        val animeFolder = viewModel.uiState.value.folders.first { it.name == "Anime" }
        assertTrue(animeFolder.isCustom)
        viewModel.selectFolder("Anime")
        viewModel.removeFolder(animeFolder)
        runCurrent()

        val removedState = viewModel.uiState.value
        assertNull(removedState.selectedFolder)
        assertTrue(removedState.customFolders.isEmpty())
        assertTrue(removedState.removedFolderNames.isEmpty())
        assertFalse("Anime" in removedState.folders.map { it.name })
    }

    @Test
    fun `openManageFolders and dismissManageFolders toggle dialog state`() = runTest {
        assertFalse(viewModel.uiState.value.manageFoldersOpen)
        viewModel.openManageFolders()
        assertTrue(viewModel.uiState.value.manageFoldersOpen)
        viewModel.dismissManageFolders()
        assertFalse(viewModel.uiState.value.manageFoldersOpen)
    }

    private class FakeMediaRepository(
        private val videos: List<LocalVideoItem>,
    ) : MediaRepository {
        private val continueWatchingFlow = MutableStateFlow<List<MediaHistoryEntity>>(emptyList())
        private val hiddenFoldersFlow = MutableStateFlow<Set<String>>(emptySet())
        private val removedFoldersFlow = MutableStateFlow<Set<String>>(emptySet())
        private val customFolderUrisFlow = MutableStateFlow<Set<String>>(emptySet())
        val customVideosByTree = mutableMapOf<String, List<LocalVideoItem>>()
        val removedUris = mutableListOf<String>()

        fun emitContinueWatching(items: List<MediaHistoryEntity>) {
            continueWatchingFlow.value = items
        }

        override suspend fun scanLocalVideos(): List<LocalVideoItem> =
            (videos + scanCustomFolderVideos()).distinctBy { it.contentUri }

        override suspend fun scanCustomFolderVideos(): List<LocalVideoItem> =
            customFolderUrisFlow.value.flatMap { uri -> customVideosByTree[uri].orEmpty() }

        override fun observeContinueWatching(limit: Int): Flow<List<MediaHistoryEntity>> {
            return continueWatchingFlow.asStateFlow()
        }

        override fun observeHiddenFolders(): Flow<Set<String>> = hiddenFoldersFlow.asStateFlow()

        override fun observeRemovedFolders(): Flow<Set<String>> = removedFoldersFlow.asStateFlow()

        override fun observeCustomFolderUris(): Flow<Set<String>> = customFolderUrisFlow.asStateFlow()

        override suspend fun setFolderHidden(folderName: String, hidden: Boolean) {
            hiddenFoldersFlow.value = if (hidden) {
                hiddenFoldersFlow.value + folderName
            } else {
                hiddenFoldersFlow.value - folderName
            }
        }

        override suspend fun setFolderRemoved(folderName: String, removed: Boolean) {
            removedFoldersFlow.value = if (removed) {
                removedFoldersFlow.value + folderName
            } else {
                removedFoldersFlow.value - folderName
            }
        }

        override suspend fun addCustomFolder(treeUri: String) {
            customFolderUrisFlow.value = customFolderUrisFlow.value + treeUri
        }

        override suspend fun removeCustomFolder(treeUri: String) {
            customFolderUrisFlow.value = customFolderUrisFlow.value - treeUri
        }

        override suspend fun getHistory(uri: String): MediaHistoryEntity? {
            return continueWatchingFlow.value.firstOrNull { it.uri == uri }
        }

        override suspend fun saveProgress(
            uri: String,
            displayName: String,
            positionMs: Long,
            durationMs: Long,
            playbackSpeed: Float,
            isNetworkStream: Boolean,
        ) = Unit

        override suspend fun removeHistory(uri: String) {
            removedUris += uri
            continueWatchingFlow.value = continueWatchingFlow.value.filterNot { it.uri == uri }
        }

        override suspend fun clearHistory() {
            continueWatchingFlow.value = emptyList()
        }
    }
}
