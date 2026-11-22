package com.jupiter.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.app.data.local.MediaHistoryEntity
import com.jupiter.app.data.local.PlaylistSummary
import com.jupiter.app.data.media.LibrarySortOption
import com.jupiter.app.data.media.LocalFolderSummary
import com.jupiter.app.data.media.LocalVideoItem
import com.jupiter.app.data.repository.InMemoryPlaylistRepository
import com.jupiter.app.data.repository.MediaRepository
import com.jupiter.app.data.repository.PlaylistMediaInput
import com.jupiter.app.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryViewMode(val label: String) {
    FOLDERS("Folders"),
    ALL_VIDEOS("Videos"),
}

data class CustomFolderEntry(
    val treeUri: String,
    val displayName: String,
)

data class LibraryUiState(
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val viewMode: LibraryViewMode = LibraryViewMode.FOLDERS,
    val selectedFolder: String? = null,
    val searchQuery: String = "",
    val sortOption: LibrarySortOption = LibrarySortOption.DATE,
    val continueWatching: List<MediaHistoryEntity> = emptyList(),
    val folders: List<LocalFolderSummary> = emptyList(),
    val allDetectedFolders: List<LocalFolderSummary> = emptyList(),
    val hiddenFolders: Set<String> = emptySet(),
    val hiddenFolderNames: List<String> = emptyList(),
    val removedFolders: Set<String> = emptySet(),
    val customFolders: List<CustomFolderEntry> = emptyList(),
    val manageFoldersOpen: Boolean = false,
    val visibleVideos: List<LocalVideoItem> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val feedbackMessage: String? = null,
)

object LibraryFilterEngine {

    fun filterVideos(
        videos: List<LocalVideoItem>,
        selectedFolder: String?,
        searchQuery: String,
        sortOption: LibrarySortOption,
        hiddenFolders: Set<String> = emptySet(),
    ): List<LocalVideoItem> {
        val query = searchQuery.trim()
        val filtered = videos.asSequence()
            .filter { video ->
                val folderName = video.bucketName.ifBlank { DEFAULT_FOLDER_NAME }
                folderName !in hiddenFolders
            }
            .filter { video ->
                selectedFolder == null || video.bucketName.ifBlank { DEFAULT_FOLDER_NAME } == selectedFolder
            }
            .filter { video ->
                query.isEmpty() || video.displayName.contains(query, ignoreCase = true)
            }
            .toList()

        return when (sortOption) {
            LibrarySortOption.DATE -> filtered.sortedWith(
                compareByDescending<LocalVideoItem> { it.dateModifiedSec }
                    .thenBy { it.displayName.lowercase() },
            )
            LibrarySortOption.NAME -> filtered.sortedWith(
                compareBy<LocalVideoItem> { it.displayName.lowercase() }
                    .thenByDescending { it.dateModifiedSec },
            )
            LibrarySortOption.DURATION -> filtered.sortedWith(
                compareByDescending<LocalVideoItem> { it.durationMs }
                    .thenBy { it.displayName.lowercase() },
            )
            LibrarySortOption.SIZE -> filtered.sortedWith(
                compareByDescending<LocalVideoItem> { it.sizeBytes }
                    .thenBy { it.displayName.lowercase() },
            )
        }
    }

    fun buildFolders(
        videos: List<LocalVideoItem>,
        searchQuery: String = "",
        hiddenFolders: Set<String> = emptySet(),
    ): List<LocalFolderSummary> {
        val query = searchQuery.trim()
        return videos
            .groupBy { it.bucketName.ifBlank { DEFAULT_FOLDER_NAME } }
            .filterKeys { folderName -> folderName !in hiddenFolders }
            .mapNotNull { (folderName, items) ->
                val matchingItems = if (query.isEmpty() || folderName.contains(query, ignoreCase = true)) {
                    items
                } else {
                    items.filter { it.displayName.contains(query, ignoreCase = true) }
                }
                if (matchingItems.isEmpty()) {
                    null
                } else {
                    val preview = matchingItems.maxByOrNull { it.dateModifiedSec } ?: matchingItems.first()
                    val customUri = items.firstNotNullOfOrNull { it.sourceTreeUri }
                    LocalFolderSummary(
                        name = folderName,
                        videoCount = matchingItems.size,
                        totalDurationMs = matchingItems.sumOf { it.durationMs.coerceAtLeast(0L) },
                        previewUri = preview.contentUri,
                        isCustom = customUri != null,
                        treeUri = customUri,
                    )
                }
            }
            .sortedWith(
                compareByDescending<LocalFolderSummary> { it.videoCount }
                    .thenBy { it.name.lowercase() },
            )
    }

    private const val DEFAULT_FOLDER_NAME = "Videos"
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    internal constructor(
        mediaRepository: MediaRepository,
    ) : this(
        mediaRepository = mediaRepository,
        playlistRepository = InMemoryPlaylistRepository(),
    )

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var cachedVideos: List<LocalVideoItem> = emptyList()
    private var customFolderUris: Set<String> = emptySet()
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            playlistRepository.observePlaylists().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
        viewModelScope.launch {
            mediaRepository.observeContinueWatching(limit = 20).collect { history ->
                _uiState.update { it.copy(continueWatching = history) }
            }
        }
        viewModelScope.launch {
            mediaRepository.observeHiddenFolders().collect { hidden ->
                _uiState.update { state -> state.copy(hiddenFolders = hidden) }
                recomputeDerivedState()
            }
        }
        viewModelScope.launch {
            mediaRepository.observeRemovedFolders().collect { removed ->
                _uiState.update { state -> state.copy(removedFolders = removed) }
                recomputeDerivedState()
            }
        }
        viewModelScope.launch {
            mediaRepository.observeCustomFolderUris().collect { uris ->
                val entries = uris
                    .map { uri ->
                        CustomFolderEntry(
                            treeUri = uri,
                            displayName = mediaRepository.resolveCustomFolderName(uri),
                        )
                    }
                    .sortedBy { it.displayName.lowercase() }
                val changed = uris != customFolderUris
                customFolderUris = uris
                _uiState.update { it.copy(customFolders = entries) }
                if (changed) {
                    refresh()
                }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        val previouslyGranted = _uiState.value.hasPermission
        _uiState.update { state ->
            state.copy(hasPermission = granted)
        }
        if (granted) {
            if (!previouslyGranted || cachedVideos.isEmpty()) {
                refresh()
            }
        } else if (customFolderUris.isEmpty()) {
            scanJob?.cancel()
            cachedVideos = emptyList()
            recomputeDerivedState(isLoading = false)
        } else {
            refresh()
        }
    }

    fun refresh() {
        val hasPermission = _uiState.value.hasPermission
        if (!hasPermission && customFolderUris.isEmpty()) {
            scanJob?.cancel()
            cachedVideos = emptyList()
            recomputeDerivedState(isLoading = false)
            return
        }
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val scanned = runCatching {
                if (hasPermission) {
                    mediaRepository.scanLocalVideos()
                } else {
                    mediaRepository.scanCustomFolderVideos()
                }
            }.getOrDefault(emptyList())
            cachedVideos = scanned
            recomputeDerivedState(isLoading = false)
        }
    }

    fun setViewMode(mode: LibraryViewMode) {
        _uiState.update { state ->
            state.copy(
                viewMode = mode,
                selectedFolder = if (mode == LibraryViewMode.ALL_VIDEOS) null else state.selectedFolder,
            )
        }
        recomputeDerivedState()
    }

    fun selectFolder(folderName: String?) {
        _uiState.update { state ->
            state.copy(
                viewMode = if (folderName != null) LibraryViewMode.FOLDERS else state.viewMode,
                selectedFolder = folderName,
            )
        }
        recomputeDerivedState()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        recomputeDerivedState()
    }

    fun setSortOption(option: LibrarySortOption) {
        _uiState.update { it.copy(sortOption = option) }
        recomputeDerivedState()
    }

    fun setFolderHidden(folderName: String, hidden: Boolean) {
        viewModelScope.launch {
            mediaRepository.setFolderHidden(folderName, hidden)
        }
    }

    fun addCustomFolder(treeUri: String) {
        if (treeUri.isBlank()) return
        viewModelScope.launch {
            val displayName = mediaRepository.resolveCustomFolderName(treeUri)
            if (displayName.isNotBlank()) {
                _uiState.value.removedFolders
                    .filter { it.equals(displayName, ignoreCase = true) }
                    .forEach { match -> mediaRepository.setFolderRemoved(match, false) }
                _uiState.value.hiddenFolders
                    .filter { it.equals(displayName, ignoreCase = true) }
                    .forEach { match -> mediaRepository.setFolderHidden(match, false) }
            }
            mediaRepository.addCustomFolder(treeUri)
        }
    }

    fun removeCustomFolder(treeUri: String) {
        viewModelScope.launch {
            removeCustomFolderInternal(treeUri = treeUri)
        }
    }

    fun removeFolder(folder: LocalFolderSummary) {
        if (_uiState.value.selectedFolder == folder.name) {
            _uiState.update { it.copy(selectedFolder = null) }
        }
        val treeUrisToRemove = buildSet {
            if (folder.treeUri != null) {
                add(folder.treeUri)
            }
            _uiState.value.customFolders
                .filter { it.displayName.equals(folder.name, ignoreCase = true) }
                .forEach { add(it.treeUri) }
        }
        val hasMediaStoreVideos = !folder.isCustom || cachedVideos.any { video ->
            video.sourceTreeUri == null &&
                video.bucketName.ifBlank { "Videos" }.equals(folder.name, ignoreCase = true)
        }
        viewModelScope.launch {
            treeUrisToRemove.forEach { treeUri ->
                removeCustomFolderInternal(treeUri = treeUri, folderName = folder.name)
            }
            if (hasMediaStoreVideos) {
                mediaRepository.setFolderRemoved(folder.name, true)
                mediaRepository.setFolderHidden(folder.name, false)
            }
            if (treeUrisToRemove.isNotEmpty()) {
                refresh()
            }
        }
    }

    private suspend fun removeCustomFolderInternal(
        treeUri: String,
        folderName: String? = null,
    ) {
        val displayName = _uiState.value.customFolders
            .firstOrNull { it.treeUri == treeUri }
            ?.displayName
            ?: mediaRepository.resolveCustomFolderName(treeUri)
        if (_uiState.value.selectedFolder == displayName || _uiState.value.selectedFolder == folderName) {
            _uiState.update { it.copy(selectedFolder = null) }
        }
        mediaRepository.removeCustomFolder(treeUri)
        _uiState.value.hiddenFolders
            .filter { match ->
                (displayName.isNotBlank() && match.equals(displayName, ignoreCase = true)) ||
                    (!folderName.isNullOrBlank() && match.equals(folderName, ignoreCase = true))
            }
            .forEach { match ->
                mediaRepository.setFolderHidden(match, false)
            }
    }

    fun openManageFolders() {
        _uiState.update { it.copy(manageFoldersOpen = true) }
    }

    fun dismissManageFolders() {
        _uiState.update { it.copy(manageFoldersOpen = false) }
    }

    fun removeFromHistory(uri: String) {
        viewModelScope.launch {
            mediaRepository.removeHistory(uri)
        }
    }

    fun addVideoToPlaylist(playlistId: Long, video: LocalVideoItem) {
        viewModelScope.launch {
            playlistRepository.addItem(playlistId, video.toPlaylistMediaInput())
            val playlistName = _uiState.value.playlists.firstOrNull { it.id == playlistId }?.name ?: "playlist"
            _uiState.update { it.copy(feedbackMessage = "Added to $playlistName") }
        }
    }

    fun addFolderToPlaylist(playlistId: Long, folderName: String) {
        val state = _uiState.value
        val videos = LibraryFilterEngine.filterVideos(
            videos = cachedVideos,
            selectedFolder = folderName,
            searchQuery = state.searchQuery,
            sortOption = state.sortOption,
            hiddenFolders = state.hiddenFolders + state.removedFolders,
        )
        if (videos.isEmpty()) return
        viewModelScope.launch {
            playlistRepository.addItems(playlistId, videos.map { it.toPlaylistMediaInput() })
            val playlistName = _uiState.value.playlists.firstOrNull { it.id == playlistId }?.name ?: "playlist"
            _uiState.update { it.copy(feedbackMessage = "Added to $playlistName") }
        }
    }

    fun createPlaylistAndAddVideo(name: String, video: LocalVideoItem) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val playlistId = playlistRepository.createPlaylist(trimmed)
            playlistRepository.addItem(playlistId, video.toPlaylistMediaInput())
            _uiState.update { it.copy(feedbackMessage = "Added to $trimmed") }
        }
    }

    fun createPlaylistAndAddFolder(name: String, folderName: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val state = _uiState.value
        val videos = LibraryFilterEngine.filterVideos(
            videos = cachedVideos,
            selectedFolder = folderName,
            searchQuery = state.searchQuery,
            sortOption = state.sortOption,
            hiddenFolders = state.hiddenFolders + state.removedFolders,
        )
        if (videos.isEmpty()) return
        viewModelScope.launch {
            val playlistId = playlistRepository.createPlaylist(trimmed)
            playlistRepository.addItems(playlistId, videos.map { it.toPlaylistMediaInput() })
            _uiState.update { it.copy(feedbackMessage = "Added to $trimmed") }
        }
    }

    fun dismissFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    private fun LocalVideoItem.toPlaylistMediaInput(): PlaylistMediaInput = PlaylistMediaInput(
        mediaUri = contentUri,
        title = displayName,
        isNetworkStream = false,
        headersJson = null,
        durationMs = durationMs,
    )

    private fun recomputeDerivedState(isLoading: Boolean? = null) {
        _uiState.update { state ->
            val excludedFolders = state.hiddenFolders + state.removedFolders
            val nextSelected = state.selectedFolder?.takeIf { it !in excludedFolders }
            val effectiveFolder = if (state.viewMode == LibraryViewMode.FOLDERS) {
                nextSelected
            } else {
                null
            }
            val hiddenFolderNames = (state.hiddenFolders - state.removedFolders).sortedCaseInsensitive()
            val allDetectedFolders = LibraryFilterEngine.buildFolders(
                videos = cachedVideos,
                searchQuery = "",
                hiddenFolders = emptySet(),
            )
            val folders = LibraryFilterEngine.buildFolders(
                videos = cachedVideos,
                searchQuery = state.searchQuery,
                hiddenFolders = excludedFolders,
            )
            val visibleVideos = LibraryFilterEngine.filterVideos(
                videos = cachedVideos,
                selectedFolder = effectiveFolder,
                searchQuery = state.searchQuery,
                sortOption = state.sortOption,
                hiddenFolders = excludedFolders,
            )
            state.copy(
                isLoading = isLoading ?: state.isLoading,
                selectedFolder = nextSelected,
                hiddenFolderNames = hiddenFolderNames,
                allDetectedFolders = allDetectedFolders,
                folders = folders,
                visibleVideos = visibleVideos,
            )
        }
    }

    private fun Set<String>.sortedCaseInsensitive(): List<String> =
        sortedWith(compareBy<String> { it.lowercase() }.thenBy { it })
}
