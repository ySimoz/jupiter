package com.jupiter.app.feature.streams

import com.jupiter.app.data.local.NetworkStreamEntity
import com.jupiter.app.data.repository.InMemoryPlaylistRepository
import com.jupiter.app.data.repository.StreamHeaderConfig
import com.jupiter.app.data.repository.StreamRepository
import com.jupiter.app.data.repository.StreamUrlValidator
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeStreamRepository
    private lateinit var playlistRepository: InMemoryPlaylistRepository
    private lateinit var viewModel: StreamsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeStreamRepository()
        playlistRepository = InMemoryPlaylistRepository()
        viewModel = StreamsViewModel(
            streamRepository = fakeRepository,
            playlistRepository = playlistRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submitQuickPlay validates URL and clears error on change`() = runTest {
        viewModel.onQuickUrlChange("not a url")
        val invalidResult = viewModel.submitQuickPlay()
        assertNull(invalidResult)
        assertNotNull(viewModel.uiState.value.quickUrlError)

        viewModel.onQuickUrlChange("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
        assertNull(viewModel.uiState.value.quickUrlError)

        val validResult = viewModel.submitQuickPlay()
        assertNotNull(validResult)
        assertEquals("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", validResult?.url)
        assertNull(validResult?.headersJson)
        assertNull(viewModel.uiState.value.quickUrlError)
    }

    @Test
    fun `onPasteQuickUrl trims valid URL and sets error on blank`() = runTest {
        viewModel.onPasteQuickUrl("   ")
        assertNotNull(viewModel.uiState.value.quickUrlError)

        viewModel.onPasteQuickUrl("  rtsp://stream.example.org:8554/live  ")
        assertEquals("rtsp://stream.example.org:8554/live", viewModel.uiState.value.quickUrl)
        assertNull(viewModel.uiState.value.quickUrlError)
    }

    @Test
    fun `add and edit bookmark with custom headers persists and updates state`() = runTest {
        viewModel.openAddDialog(prefillUrl = "https://cdn.example.com/live/master.m3u8")
        val initialEditor = viewModel.uiState.value.editor
        assertNotNull(initialEditor)
        assertEquals("https://cdn.example.com/live/master.m3u8", initialEditor?.url)

        viewModel.updateEditor(
            title = "Live Feed",
            userAgent = "JupiterPlayer/1.0",
            referer = "https://example.com/watch",
            isPinned = true,
        )
        viewModel.addHeaderRow()
        val addedRowId = viewModel.uiState.value.editor!!.customHeaders.single().id
        viewModel.updateHeaderRow(addedRowId, "Authorization", "Bearer token-123")
        viewModel.addHeaderRow()
        val extraRowId = viewModel.uiState.value.editor!!.customHeaders.last().id
        viewModel.removeHeaderRow(extraRowId)

        viewModel.saveEditor()
        runCurrent()

        assertNull(viewModel.uiState.value.editor)
        assertEquals(1, viewModel.uiState.value.pinnedStreams.size)
        val saved = viewModel.uiState.value.pinnedStreams.single()
        assertEquals("Live Feed", saved.title)
        assertEquals("JupiterPlayer/1.0", saved.userAgent)
        assertEquals("https://example.com/watch", saved.referer)
        assertEquals(
            mapOf("Authorization" to "Bearer token-123"),
            StreamHeaderConfig.parseHeadersMap(saved.customHeadersJson),
        )

        viewModel.openEditDialog(saved)
        val editState = viewModel.uiState.value.editor
        assertNotNull(editState)
        assertEquals(saved.id, editState?.editingId)
        assertEquals(1, editState?.customHeaders?.size)

        viewModel.updateEditor(title = "Updated Feed", url = "invalid://")
        viewModel.saveEditor()
        runCurrent()
        assertNotNull(viewModel.uiState.value.editor?.errorMessage)

        viewModel.updateEditor(url = "https://cdn.example.com/live/v2.m3u8")
        viewModel.saveEditor()
        runCurrent()
        assertNull(viewModel.uiState.value.editor)
        assertEquals("Updated Feed", viewModel.uiState.value.pinnedStreams.single().title)
        assertEquals(
            "https://cdn.example.com/live/v2.m3u8",
            viewModel.uiState.value.pinnedStreams.single().url,
        )
    }

    @Test
    fun `pin toggle and delete separate pinned and saved streams`() = runTest {
        fakeRepository.saveStream(
            title = "Pinned One",
            url = "https://example.com/one.m3u8",
            isPinned = true,
        )
        fakeRepository.saveStream(
            title = "Regular Two",
            url = "https://example.com/two.mp4",
            isPinned = false,
        )
        runCurrent()

        assertEquals(listOf("Pinned One"), viewModel.uiState.value.pinnedStreams.map { it.title })
        assertEquals(listOf("Regular Two"), viewModel.uiState.value.savedStreams.map { it.title })

        val regular = viewModel.uiState.value.savedStreams.single()
        viewModel.togglePin(regular)
        runCurrent()

        assertEquals(2, viewModel.uiState.value.pinnedStreams.size)
        assertTrue(viewModel.uiState.value.savedStreams.isEmpty())

        viewModel.togglePin(viewModel.uiState.value.pinnedStreams.first { it.id == regular.id })
        runCurrent()
        assertEquals(1, viewModel.uiState.value.pinnedStreams.size)
        assertEquals(1, viewModel.uiState.value.savedStreams.size)

        viewModel.deleteStream(regular.id)
        runCurrent()
        assertTrue(viewModel.uiState.value.savedStreams.isEmpty())
        assertEquals(1, viewModel.uiState.value.pinnedStreams.size)
    }

    @Test
    fun `prepareLaunch builds headersJson and records lastPlayed`() = runTest {
        val streamId = fakeRepository.saveStream(
            title = "Protected Stream",
            url = "https://example.com/protected.m3u8",
            userAgent = "CustomAgent/2.0",
            referer = "https://portal.example.com",
            customHeaders = mapOf("X-Token" to "abc"),
            isPinned = false,
        ).getOrThrow()
        runCurrent()

        val stream = viewModel.uiState.value.savedStreams.single { it.id == streamId }
        assertNull(stream.lastPlayedEpochMs)

        val request = viewModel.prepareLaunch(stream)
        runCurrent()

        assertEquals("https://example.com/protected.m3u8", request.url)
        assertEquals("Protected Stream", request.title)
        assertNotNull(request.headersJson)

        val decoded = StreamHeaderConfig.fromJson(request.headersJson)
        assertEquals("CustomAgent/2.0", decoded.userAgent)
        assertEquals("https://portal.example.com", decoded.referer)
        assertEquals(mapOf("X-Token" to "abc"), decoded.headers)

        assertTrue(fakeRepository.recordedPlayedIds.contains(streamId))
        val updated = viewModel.uiState.value.savedStreams.single { it.id == streamId }
        assertNotNull(updated.lastPlayedEpochMs)
        assertFalse(updated.isPinned)
    }

    @Test
    fun `addStreamToPlaylist and createPlaylistAndAddStream preserve headersJson and emit feedback`() = runTest {
        val streamId = fakeRepository.saveStream(
            title = "Live Sports",
            url = "https://cdn.example.com/sports/master.m3u8",
            userAgent = "JupiterTV/2.0",
            referer = "https://sports.example.com",
            customHeaders = mapOf("X-Key" to "secret"),
            isPinned = true,
        ).getOrThrow()
        val playlistId = playlistRepository.createPlaylist("Live TV")
        runCurrent()

        assertEquals(listOf("Live TV"), viewModel.uiState.value.playlists.map { it.name })
        val stream = viewModel.uiState.value.pinnedStreams.single { it.id == streamId }

        viewModel.addStreamToPlaylist(playlistId, stream)
        runCurrent()

        assertEquals("Added to Live TV", viewModel.uiState.value.feedbackMessage)
        val addedItem = playlistRepository.getPlaylistItems(playlistId).single()
        assertEquals("https://cdn.example.com/sports/master.m3u8", addedItem.mediaUri)
        assertEquals("Live Sports", addedItem.title)
        assertTrue(addedItem.isNetworkStream)
        val decoded = StreamHeaderConfig.fromJson(addedItem.headersJson)
        assertEquals("JupiterTV/2.0", decoded.userAgent)
        assertEquals("https://sports.example.com", decoded.referer)
        assertEquals(mapOf("X-Key" to "secret"), decoded.headers)

        viewModel.dismissFeedback()
        assertNull(viewModel.uiState.value.feedbackMessage)

        viewModel.createPlaylistAndAddStream("  News Channels  ", stream)
        runCurrent()

        assertEquals("Added to News Channels", viewModel.uiState.value.feedbackMessage)
        val createdSummary = viewModel.uiState.value.playlists.first { it.name == "News Channels" }
        assertEquals(1, createdSummary.streamCount)
        assertEquals(0, createdSummary.videoCount)
    }

    private class FakeStreamRepository : StreamRepository {
        private val streamsFlow = MutableStateFlow<List<NetworkStreamEntity>>(emptyList())
        private var nextId = 1L
        private var clockMs = 1_700_000_000_000L
        val recordedPlayedIds = mutableListOf<Long>()

        override fun observeStreams(): Flow<List<NetworkStreamEntity>> = streamsFlow.asStateFlow()

        override suspend fun getStream(id: Long): NetworkStreamEntity? {
            return streamsFlow.value.firstOrNull { it.id == id }
        }

        override suspend fun saveStream(
            id: Long,
            title: String,
            url: String,
            userAgent: String?,
            referer: String?,
            customHeaders: Map<String, String>,
            isPinned: Boolean,
        ): Result<Long> {
            val validation = StreamUrlValidator.validate(url)
            if (validation is StreamUrlValidator.ValidationResult.Invalid) {
                return Result.failure(IllegalArgumentException(validation.reason))
            }
            val validUrl = (validation as StreamUrlValidator.ValidationResult.Valid).normalizedUrl
            val cleanTitle = title.trim().ifEmpty { StreamUrlValidator.deriveDisplayTitle(validUrl) }
            val headersJson = StreamHeaderConfig.encodeHeadersMap(customHeaders)
            val existing = if (id > 0L) streamsFlow.value.firstOrNull { it.id == id } else null
            val resolvedId = existing?.id ?: nextId++
            val entity = NetworkStreamEntity(
                id = resolvedId,
                title = cleanTitle,
                url = validUrl,
                userAgent = userAgent?.trim()?.ifEmpty { null },
                referer = referer?.trim()?.ifEmpty { null },
                customHeadersJson = headersJson,
                isPinned = isPinned,
                createdAtEpochMs = existing?.createdAtEpochMs ?: ++clockMs,
                lastPlayedEpochMs = existing?.lastPlayedEpochMs,
            )
            emitSorted(streamsFlow.value.filterNot { it.id == resolvedId } + entity)
            return Result.success(resolvedId)
        }

        override suspend fun setPinned(id: Long, pinned: Boolean) {
            emitSorted(
                streamsFlow.value.map { item ->
                    if (item.id == id) item.copy(isPinned = pinned) else item
                },
            )
        }

        override suspend fun recordPlayed(id: Long) {
            recordedPlayedIds += id
            val playedAt = ++clockMs
            emitSorted(
                streamsFlow.value.map { item ->
                    if (item.id == id) item.copy(lastPlayedEpochMs = playedAt) else item
                },
            )
        }

        override suspend fun deleteStream(id: Long) {
            emitSorted(streamsFlow.value.filterNot { it.id == id })
        }

        private fun emitSorted(list: List<NetworkStreamEntity>) {
            streamsFlow.value = list.sortedWith(
                compareByDescending<NetworkStreamEntity> { it.isPinned }
                    .thenByDescending { it.lastPlayedEpochMs ?: it.createdAtEpochMs },
            )
        }
    }
}
