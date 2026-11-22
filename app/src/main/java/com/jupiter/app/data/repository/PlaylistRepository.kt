package com.jupiter.app.data.repository

import com.jupiter.app.data.local.PlaylistDao
import com.jupiter.app.data.local.PlaylistEntity
import com.jupiter.app.data.local.PlaylistItemEntity
import com.jupiter.app.data.local.PlaylistSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class PlaylistMediaInput(
    val mediaUri: String,
    val title: String,
    val isNetworkStream: Boolean = false,
    val headersJson: String? = null,
    val durationMs: Long = 0L,
)

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<PlaylistSummary>>
    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>>
    suspend fun getPlaylistItems(playlistId: Long): List<PlaylistItemEntity>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, newName: String)
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun addItem(playlistId: Long, item: PlaylistMediaInput)
    suspend fun addItems(playlistId: Long, items: List<PlaylistMediaInput>)
    suspend fun removeItem(itemId: Long)
    suspend fun moveItem(playlistId: Long, itemId: Long, moveUp: Boolean)
}

internal fun buildPlaylistSummaries(
    playlists: List<PlaylistEntity>,
    allItems: List<PlaylistItemEntity>,
): List<PlaylistSummary> {
    val itemsByPlaylist = allItems.groupBy { it.playlistId }
    return playlists
        .sortedWith(compareByDescending<PlaylistEntity> { it.updatedAtEpochMs }.thenByDescending { it.id })
        .map { playlist ->
            val items = itemsByPlaylist[playlist.id]
                .orEmpty()
                .sortedWith(compareBy<PlaylistItemEntity> { it.positionOrder }.thenBy { it.id })
            val streamCount = items.count { it.isNetworkStream }
            val videoCount = items.count { !it.isNetworkStream }
            PlaylistSummary(
                id = playlist.id,
                name = playlist.name,
                itemCount = items.size,
                videoCount = videoCount,
                streamCount = streamCount,
                previewUri = items.firstOrNull { !it.isNetworkStream }?.mediaUri,
                updatedAtEpochMs = playlist.updatedAtEpochMs,
            )
        }
}

class DefaultPlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<PlaylistSummary>> =
        combine(
            playlistDao.observePlaylists(),
            playlistDao.observeAllItems(),
        ) { playlists, allItems ->
            buildPlaylistSummaries(playlists, allItems)
        }

    override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>> =
        playlistDao.observeItemsForPlaylist(playlistId)

    override suspend fun getPlaylistItems(playlistId: Long): List<PlaylistItemEntity> =
        playlistDao.getItemsForPlaylist(playlistId)

    override suspend fun createPlaylist(name: String): Long {
        val resolvedName = name.trim().ifBlank { DEFAULT_PLAYLIST_NAME }
        val now = clock()
        return playlistDao.insertPlaylist(
            PlaylistEntity(
                name = resolvedName,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
    }

    override suspend fun renamePlaylist(playlistId: Long, newName: String) {
        if (playlistId <= 0L) return
        val resolvedName = newName.trim()
        if (resolvedName.isEmpty()) return
        playlistDao.renamePlaylist(
            playlistId = playlistId,
            newName = resolvedName,
            updatedAtEpochMs = clock(),
        )
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        if (playlistId <= 0L) return
        playlistDao.deletePlaylist(playlistId)
    }

    override suspend fun addItem(playlistId: Long, item: PlaylistMediaInput) {
        addItems(playlistId, listOf(item))
    }

    override suspend fun addItems(playlistId: Long, items: List<PlaylistMediaInput>) {
        if (playlistId <= 0L) return
        val validItems = items.filter { it.mediaUri.isNotBlank() }
        if (validItems.isEmpty()) return

        val startOrder = (playlistDao.getMaxOrder(playlistId) ?: -1) + 1
        val now = clock()
        val entities = validItems.mapIndexed { index, input ->
            PlaylistItemEntity(
                playlistId = playlistId,
                mediaUri = input.mediaUri,
                title = input.title.trim().ifBlank { input.mediaUri },
                isNetworkStream = input.isNetworkStream,
                headersJson = input.headersJson,
                durationMs = input.durationMs.coerceAtLeast(0L),
                positionOrder = startOrder + index,
                addedAtEpochMs = now,
            )
        }
        playlistDao.insertItems(entities)
        playlistDao.touchPlaylist(playlistId, now)
    }

    override suspend fun removeItem(itemId: Long) {
        if (itemId <= 0L) return
        val playlistId = playlistDao.getPlaylistIdForItem(itemId)
        playlistDao.deleteItem(itemId)
        if (playlistId != null) {
            val remaining = playlistDao.getItemsForPlaylist(playlistId)
            remaining.forEachIndexed { index, item ->
                if (item.positionOrder != index) {
                    playlistDao.updateItemOrder(item.id, index)
                }
            }
            playlistDao.touchPlaylist(playlistId, clock())
        }
    }

    override suspend fun moveItem(playlistId: Long, itemId: Long, moveUp: Boolean) {
        if (playlistId <= 0L || itemId <= 0L) return
        val items = playlistDao.getItemsForPlaylist(playlistId).toMutableList()
        val currentIndex = items.indexOfFirst { it.id == itemId }
        if (currentIndex < 0) return

        val targetIndex = if (moveUp) currentIndex - 1 else currentIndex + 1
        if (targetIndex !in items.indices) return

        val moved = items.removeAt(currentIndex)
        items.add(targetIndex, moved)

        items.forEachIndexed { index, item ->
            if (item.positionOrder != index) {
                playlistDao.updateItemOrder(item.id, index)
            }
        }
        playlistDao.touchPlaylist(playlistId, clock())
    }

    private companion object {
        const val DEFAULT_PLAYLIST_NAME = "Untitled playlist"
    }
}

class InMemoryPlaylistRepository(
    private val clock: () -> Long = System::currentTimeMillis,
) : PlaylistRepository {

    private val lock = Any()
    private var nextPlaylistId = 1L
    private var nextItemId = 1L
    private val playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    private val items = MutableStateFlow<List<PlaylistItemEntity>>(emptyList())

    override fun observePlaylists(): Flow<List<PlaylistSummary>> =
        combine(playlists, items) { currentPlaylists, currentItems ->
            buildPlaylistSummaries(currentPlaylists, currentItems)
        }

    override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>> =
        items.map { currentItems ->
            currentItems
                .filter { it.playlistId == playlistId }
                .sortedWith(compareBy<PlaylistItemEntity> { it.positionOrder }.thenBy { it.id })
        }

    override suspend fun getPlaylistItems(playlistId: Long): List<PlaylistItemEntity> =
        items.value
            .filter { it.playlistId == playlistId }
            .sortedWith(compareBy<PlaylistItemEntity> { it.positionOrder }.thenBy { it.id })

    override suspend fun createPlaylist(name: String): Long = synchronized(lock) {
        val id = nextPlaylistId++
        val now = clock()
        val resolvedName = name.trim().ifBlank { "Untitled playlist" }
        playlists.value = playlists.value + PlaylistEntity(
            id = id,
            name = resolvedName,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        id
    }

    override suspend fun renamePlaylist(playlistId: Long, newName: String) {
        synchronized(lock) {
            val resolvedName = newName.trim()
            if (playlistId <= 0L || resolvedName.isEmpty()) return
            val now = clock()
            playlists.value = playlists.value.map { playlist ->
                if (playlist.id == playlistId) {
                    playlist.copy(name = resolvedName, updatedAtEpochMs = now)
                } else {
                    playlist
                }
            }
        }
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        synchronized(lock) {
            if (playlistId <= 0L) return
            playlists.value = playlists.value.filterNot { it.id == playlistId }
            items.value = items.value.filterNot { it.playlistId == playlistId }
        }
    }

    override suspend fun addItem(playlistId: Long, item: PlaylistMediaInput) {
        addItems(playlistId, listOf(item))
    }

    override suspend fun addItems(playlistId: Long, items: List<PlaylistMediaInput>) {
        synchronized(lock) {
            if (playlistId <= 0L) return
            if (playlists.value.none { it.id == playlistId }) return
            val validItems = items.filter { it.mediaUri.isNotBlank() }
            if (validItems.isEmpty()) return

            val startOrder = (this.items.value
                .filter { it.playlistId == playlistId }
                .maxOfOrNull { it.positionOrder } ?: -1) + 1
            val now = clock()
            val newEntities = validItems.mapIndexed { index, input ->
                PlaylistItemEntity(
                    id = nextItemId++,
                    playlistId = playlistId,
                    mediaUri = input.mediaUri,
                    title = input.title.trim().ifBlank { input.mediaUri },
                    isNetworkStream = input.isNetworkStream,
                    headersJson = input.headersJson,
                    durationMs = input.durationMs.coerceAtLeast(0L),
                    positionOrder = startOrder + index,
                    addedAtEpochMs = now,
                )
            }
            this.items.value = this.items.value + newEntities
            touchLocked(playlistId, now)
        }
    }

    override suspend fun removeItem(itemId: Long) {
        synchronized(lock) {
            val target = items.value.firstOrNull { it.id == itemId } ?: return
            val playlistId = target.playlistId
            val remainingForPlaylist = items.value
                .filter { it.playlistId == playlistId && it.id != itemId }
                .sortedWith(compareBy<PlaylistItemEntity> { it.positionOrder }.thenBy { it.id })
                .mapIndexed { index, entity -> entity.copy(positionOrder = index) }
            val otherItems = items.value.filterNot { it.playlistId == playlistId }
            items.value = otherItems + remainingForPlaylist
            touchLocked(playlistId, clock())
        }
    }

    override suspend fun moveItem(playlistId: Long, itemId: Long, moveUp: Boolean) {
        synchronized(lock) {
            if (playlistId <= 0L || itemId <= 0L) return
            val playlistItems = items.value
                .filter { it.playlistId == playlistId }
                .sortedWith(compareBy<PlaylistItemEntity> { it.positionOrder }.thenBy { it.id })
                .toMutableList()
            val currentIndex = playlistItems.indexOfFirst { it.id == itemId }
            if (currentIndex < 0) return

            val targetIndex = if (moveUp) currentIndex - 1 else currentIndex + 1
            if (targetIndex !in playlistItems.indices) return

            val moved = playlistItems.removeAt(currentIndex)
            playlistItems.add(targetIndex, moved)
            val reindexed = playlistItems.mapIndexed { index, entity ->
                entity.copy(positionOrder = index)
            }
            val otherItems = items.value.filterNot { it.playlistId == playlistId }
            items.value = otherItems + reindexed
            touchLocked(playlistId, clock())
        }
    }

    private fun touchLocked(playlistId: Long, now: Long) {
        playlists.value = playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(updatedAtEpochMs = now)
            } else {
                playlist
            }
        }
    }
}
