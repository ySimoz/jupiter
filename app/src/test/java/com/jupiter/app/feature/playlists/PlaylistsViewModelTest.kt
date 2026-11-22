package com.jupiter.app.feature.playlists

import com.jupiter.app.data.repository.InMemoryPlaylistRepository
import com.jupiter.app.data.repository.PlaylistMediaInput
import com.jupiter.app.feature.player.QueueMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: InMemoryPlaylistRepository
    private lateinit var viewModel: PlaylistsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        var clock = 1_700_000_000_000L
        repository = InMemoryPlaylistRepository(clock = { ++clock })
        viewModel = PlaylistsViewModel(playlistRepository = repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `create and rename dialog validates blank names and persists trimmed names`() = runTest {
        viewModel.openCreateDialog()
        assertNotNull(viewModel.uiState.value.dialog)

        viewModel.updateDialogName("   ")
        viewModel.submitDialog()
        assertNotNull(viewModel.uiState.value.dialog?.errorMessage)
        assertTrue(viewModel.uiState.value.playlists.isEmpty())

        viewModel.updateDialogName("  Weekend Watch  ")
        viewModel.submitDialog()
        runCurrent()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(1, viewModel.uiState.value.playlists.size)
        val created = viewModel.uiState.value.playlists.single()
        assertEquals("Weekend Watch", created.name)

        viewModel.openRenameDialog(created)
        viewModel.updateDialogName(" ")
        viewModel.submitDialog()
        assertNotNull(viewModel.uiState.value.dialog?.errorMessage)

        viewModel.updateDialogName("  Favorites  ")
        viewModel.submitDialog()
        runCurrent()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals("Favorites", viewModel.uiState.value.playlists.single().name)
    }

    @Test
    fun `first-time selectPlaylist immediately populates selectedPlaylist and selectedItems before and after items arrive`() = runTest {
        val playlistId = repository.createPlaylist("Mix")
        repository.addItems(
            playlistId,
            listOf(
                PlaylistMediaInput(
                    mediaUri = "content://media/1",
                    title = "Local Clip",
                    durationMs = 60_000L,
                ),
                PlaylistMediaInput(
                    mediaUri = "https://cdn.example.com/stream.m3u8",
                    title = "VerifyStreami",
                    isNetworkStream = true,
                ),
            ),
        )
        runCurrent()

        // First open after playlists loaded with pre-existing items
        viewModel.selectPlaylist(playlistId)
        runCurrent()

        val firstOpenState = viewModel.uiState.value
        assertEquals(playlistId, firstOpenState.selectedPlaylistId)
        assertEquals("Mix", firstOpenState.selectedPlaylist?.name)
        assertEquals(1, firstOpenState.selectedPlaylist?.videoCount)
        assertEquals(1, firstOpenState.selectedPlaylist?.streamCount)
        assertEquals(
            listOf("Local Clip", "VerifyStreami"),
            firstOpenState.selectedItems.map { it.title },
        )

        // Adding another item while selected updates both summary and items atomically
        repository.addItem(
            playlistId,
            PlaylistMediaInput(
                mediaUri = "content://media/2",
                title = "Second Clip",
                durationMs = 90_000L,
            ),
        )
        runCurrent()

        assertEquals(
            listOf("Local Clip", "VerifyStreami", "Second Clip"),
            viewModel.uiState.value.selectedItems.map { it.title },
        )
        assertEquals(3, viewModel.uiState.value.selectedPlaylist?.itemCount)

        // Closing and reopening a fresh empty playlist, then receiving items afterward
        viewModel.selectPlaylist(null)
        val freshPlaylistId = repository.createPlaylist("Fresh")
        runCurrent()

        viewModel.selectPlaylist(freshPlaylistId)
        runCurrent()
        assertEquals(freshPlaylistId, viewModel.uiState.value.selectedPlaylistId)
        assertEquals("Fresh", viewModel.uiState.value.selectedPlaylist?.name)
        assertTrue(viewModel.uiState.value.selectedItems.isEmpty())

        repository.addItem(
            freshPlaylistId,
            PlaylistMediaInput(
                mediaUri = "content://media/3",
                title = "Arrived Later",
                durationMs = 30_000L,
            ),
        )
        runCurrent()
        assertEquals(
            listOf("Arrived Later"),
            viewModel.uiState.value.selectedItems.map { it.title },
        )
    }

    @Test
    fun `selectPlaylist before initial collector emission resolves playlist and items atomically`() = runTest {
        val playlistId = repository.createPlaylist("Immediate")
        repository.addItem(
            playlistId,
            PlaylistMediaInput(
                mediaUri = "content://media/10",
                title = "Preloaded Clip",
                durationMs = 15_000L,
            ),
        )

        // Call selectPlaylist before runCurrent() starts init collectors
        viewModel.selectPlaylist(playlistId)
        assertEquals(playlistId, viewModel.uiState.value.selectedPlaylistId)

        runCurrent()
        val state = viewModel.uiState.value
        assertEquals(playlistId, state.selectedPlaylistId)
        assertEquals("Immediate", state.selectedPlaylist?.name)
        assertEquals(listOf("Preloaded Clip"), state.selectedItems.map { it.title })
    }

    @Test
    fun `selectPlaylist observes items and moveItem or removeItem updates order`() = runTest {
        val playlistId = repository.createPlaylist("Mix")
        repository.addItems(
            playlistId,
            listOf(
                PlaylistMediaInput(
                    mediaUri = "content://media/1",
                    title = "Clip One",
                    durationMs = 120_000L,
                ),
                PlaylistMediaInput(
                    mediaUri = "https://cdn.example.com/live.m3u8",
                    title = "Live Feed",
                    isNetworkStream = true,
                    headersJson = """{"userAgent":"Jupiter/1.0"}""",
                ),
                PlaylistMediaInput(
                    mediaUri = "content://media/3",
                    title = "Clip Three",
                    durationMs = 300_000L,
                ),
            ),
        )
        runCurrent()

        viewModel.selectPlaylist(playlistId)
        runCurrent()

        val selectedState = viewModel.uiState.value
        assertEquals(playlistId, selectedState.selectedPlaylistId)
        assertEquals("Mix", selectedState.selectedPlaylist?.name)
        assertEquals(listOf("Clip One", "Live Feed", "Clip Three"), selectedState.selectedItems.map { it.title })

        val secondId = selectedState.selectedItems[1].id
        viewModel.moveItem(secondId, moveUp = true)
        runCurrent()
        assertEquals(
            listOf("Live Feed", "Clip One", "Clip Three"),
            viewModel.uiState.value.selectedItems.map { it.title },
        )

        val firstId = viewModel.uiState.value.selectedItems.first().id
        viewModel.moveItem(firstId, moveUp = true)
        runCurrent()
        assertEquals(
            listOf("Live Feed", "Clip One", "Clip Three"),
            viewModel.uiState.value.selectedItems.map { it.title },
        )

        viewModel.moveItem(firstId, moveUp = false)
        runCurrent()
        assertEquals(
            listOf("Clip One", "Live Feed", "Clip Three"),
            viewModel.uiState.value.selectedItems.map { it.title },
        )

        viewModel.removeItem(secondId)
        runCurrent()
        assertEquals(
            listOf("Clip One", "Clip Three"),
            viewModel.uiState.value.selectedItems.map { it.title },
        )
        assertEquals(2, viewModel.uiState.value.selectedPlaylist?.itemCount)
    }

    @Test
    fun `deletePlaylist removes playlist and clears active selection`() = runTest {
        val playlistId = repository.createPlaylist("Temporary")
        runCurrent()
        viewModel.selectPlaylist(playlistId)
        runCurrent()
        assertEquals(playlistId, viewModel.uiState.value.selectedPlaylistId)

        viewModel.deletePlaylist(playlistId)
        runCurrent()

        assertTrue(viewModel.uiState.value.playlists.isEmpty())
        assertNull(viewModel.uiState.value.selectedPlaylistId)
        assertNull(viewModel.uiState.value.selectedPlaylist)
        assertTrue(viewModel.uiState.value.selectedItems.isEmpty())
    }

    @Test
    fun `launchPlaylist emits ordered QueueMediaItems with stream headers intact`() = runTest {
        val playlistId = repository.createPlaylist("Evening")
        repository.addItems(
            playlistId,
            listOf(
                PlaylistMediaInput(
                    mediaUri = "content://media/1",
                    title = "Local Intro",
                    durationMs = 45_000L,
                ),
                PlaylistMediaInput(
                    mediaUri = "https://stream.example.com/channel.m3u8",
                    title = "News Stream",
                    isNetworkStream = true,
                    headersJson = """{"userAgent":"CustomUA","referer":"https://stream.example.com"}""",
                ),
            ),
        )
        runCurrent()

        val summary = viewModel.uiState.value.playlists.single()
        var capturedItems: List<QueueMediaItem>? = null
        var capturedIndex = -1
        var capturedTitle = ""

        viewModel.launchPlaylist(summary) { items, startIndex, sourceTitle ->
            capturedItems = items
            capturedIndex = startIndex
            capturedTitle = sourceTitle
        }
        runCurrent()

        assertEquals(0, capturedIndex)
        assertEquals("Evening", capturedTitle)
        assertEquals(
            listOf(
                QueueMediaItem(
                    uri = "content://media/1",
                    title = "Local Intro",
                    isNetworkStream = false,
                    headersJson = null,
                    durationMs = 45_000L,
                ),
                QueueMediaItem(
                    uri = "https://stream.example.com/channel.m3u8",
                    title = "News Stream",
                    isNetworkStream = true,
                    headersJson = """{"userAgent":"CustomUA","referer":"https://stream.example.com"}""",
                    durationMs = 0L,
                ),
            ),
            capturedItems,
        )
    }
}
