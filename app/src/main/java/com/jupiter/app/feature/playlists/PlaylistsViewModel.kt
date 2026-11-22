package com.jupiter.app.feature.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.app.data.local.PlaylistItemEntity
import com.jupiter.app.data.local.PlaylistSummary
import com.jupiter.app.data.repository.PlaylistRepository
import com.jupiter.app.feature.player.QueueMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDialogState(
    val editingPlaylistId: Long? = null,
    val name: String = "",
    val errorMessage: String? = null,
)

data class PlaylistsUiState(
    val playlists: List<PlaylistSummary> = emptyList(),
    val selectedPlaylistId: Long? = null,
    val selectedPlaylist: PlaylistSummary? = null,
    val selectedItems: List<PlaylistItemEntity> = emptyList(),
    val dialog: PlaylistDialogState? = null,
)

fun PlaylistItemEntity.toQueueMediaItem(): QueueMediaItem = QueueMediaItem(
    uri = mediaUri,
    title = title,
    isNetworkStream = isNetworkStream,
    headersJson = headersJson,
    durationMs = durationMs,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val selectedPlaylistIdFlow = MutableStateFlow<Long?>(null)

    private val selectedPlaylistItemsFlow = selectedPlaylistIdFlow.flatMapLatest { playlistId ->
        if (playlistId == null) {
            flowOf(emptyList())
        } else {
            playlistRepository.observePlaylistItems(playlistId)
        }
    }

    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                playlistRepository.observePlaylists(),
                selectedPlaylistIdFlow,
                selectedPlaylistItemsFlow,
            ) { playlists, selectedId, items ->
                Triple(playlists, selectedId, items)
            }.collect { (playlists, selectedId, items) ->
                val matchingPlaylist = selectedId?.let { id -> playlists.firstOrNull { it.id == id } }
                if (selectedId != null && matchingPlaylist == null) {
                    selectedPlaylistIdFlow.value = null
                }
                val resolvedSelectedId = if (selectedId != null && matchingPlaylist != null) {
                    selectedId
                } else {
                    null
                }
                val resolvedItems = if (resolvedSelectedId == null) {
                    emptyList()
                } else {
                    items.filter { it.playlistId == resolvedSelectedId }
                }
                _uiState.update { state ->
                    state.copy(
                        playlists = playlists,
                        selectedPlaylistId = resolvedSelectedId,
                        selectedPlaylist = matchingPlaylist,
                        selectedItems = resolvedItems,
                    )
                }
            }
        }
    }

    fun selectPlaylist(playlistId: Long?) {
        _uiState.update { state ->
            val playlist = playlistId?.let { id -> state.playlists.firstOrNull { it.id == id } }
            val keepCurrentItems = playlistId != null && state.selectedPlaylistId == playlistId
            state.copy(
                selectedPlaylistId = playlistId,
                selectedPlaylist = playlist,
                selectedItems = if (keepCurrentItems) state.selectedItems else emptyList(),
            )
        }
        selectedPlaylistIdFlow.value = playlistId
    }

    fun openCreateDialog() {
        _uiState.update { state ->
            state.copy(
                dialog = PlaylistDialogState(
                    editingPlaylistId = null,
                    name = "",
                    errorMessage = null,
                ),
            )
        }
    }

    fun openRenameDialog(playlist: PlaylistSummary) {
        _uiState.update { state ->
            state.copy(
                dialog = PlaylistDialogState(
                    editingPlaylistId = playlist.id,
                    name = playlist.name,
                    errorMessage = null,
                ),
            )
        }
    }

    fun updateDialogName(name: String) {
        _uiState.update { state ->
            val current = state.dialog ?: return@update state
            state.copy(
                dialog = current.copy(
                    name = name,
                    errorMessage = null,
                ),
            )
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialog = null) }
    }

    fun submitDialog() {
        val current = _uiState.value.dialog ?: return
        val trimmed = current.name.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { state ->
                val dialog = state.dialog ?: return@update state
                state.copy(
                    dialog = dialog.copy(errorMessage = "Enter a playlist name"),
                )
            }
            return
        }

        _uiState.update { it.copy(dialog = null) }
        viewModelScope.launch {
            val targetId = current.editingPlaylistId
            if (targetId == null) {
                playlistRepository.createPlaylist(trimmed)
            } else {
                playlistRepository.renamePlaylist(targetId, trimmed)
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        if (selectedPlaylistIdFlow.value == playlistId) {
            selectPlaylist(null)
        }
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    fun removeItem(itemId: Long) {
        viewModelScope.launch {
            playlistRepository.removeItem(itemId)
        }
    }

    fun moveItem(itemId: Long, moveUp: Boolean) {
        val playlistId = _uiState.value.selectedPlaylistId ?: return
        viewModelScope.launch {
            playlistRepository.moveItem(playlistId, itemId, moveUp)
        }
    }

    fun launchPlaylist(
        playlist: PlaylistSummary,
        onOpenQueue: (items: List<QueueMediaItem>, startIndex: Int, sourceTitle: String) -> Unit,
    ) {
        viewModelScope.launch {
            val items = playlistRepository.getPlaylistItems(playlist.id)
                .map { it.toQueueMediaItem() }
            if (items.isNotEmpty()) {
                onOpenQueue(items, 0, playlist.name)
            }
        }
    }
}
