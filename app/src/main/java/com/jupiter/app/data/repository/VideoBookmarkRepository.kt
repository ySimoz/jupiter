package com.jupiter.app.data.repository

import com.jupiter.app.data.local.VideoBookmarkDao
import com.jupiter.app.data.local.VideoBookmarkEntity
import com.jupiter.app.data.media.UriHash
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Locale

interface VideoBookmarkRepository {
    fun observeBookmarks(mediaUri: String): Flow<List<VideoBookmarkEntity>>
    suspend fun addBookmark(mediaUri: String, positionMs: Long, label: String): Long
    suspend fun renameBookmark(id: Long, label: String)
    suspend fun deleteBookmark(id: Long)
}

internal fun formatBookmarkFallbackLabel(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L)) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

class DefaultVideoBookmarkRepository(
    private val videoBookmarkDao: VideoBookmarkDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : VideoBookmarkRepository {

    override fun observeBookmarks(mediaUri: String): Flow<List<VideoBookmarkEntity>> {
        if (mediaUri.isBlank()) return flowOf(emptyList())
        return videoBookmarkDao.observeByUriHash(UriHash.of(mediaUri))
    }

    override suspend fun addBookmark(mediaUri: String, positionMs: Long, label: String): Long {
        if (mediaUri.isBlank()) return 0L
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val resolvedLabel = label.trim().ifBlank { formatBookmarkFallbackLabel(safePositionMs) }
        return videoBookmarkDao.insert(
            VideoBookmarkEntity(
                uriHash = UriHash.of(mediaUri),
                mediaUri = mediaUri,
                positionMs = safePositionMs,
                label = resolvedLabel,
                createdAtEpochMs = clock(),
            ),
        )
    }

    override suspend fun renameBookmark(id: Long, label: String) {
        if (id <= 0L) return
        val resolvedLabel = label.trim()
        if (resolvedLabel.isEmpty()) return
        videoBookmarkDao.rename(id, resolvedLabel)
    }

    override suspend fun deleteBookmark(id: Long) {
        if (id <= 0L) return
        videoBookmarkDao.deleteById(id)
    }
}

class InMemoryVideoBookmarkRepository(
    private val clock: () -> Long = System::currentTimeMillis,
) : VideoBookmarkRepository {

    private val lock = Any()
    private var nextId = 1L
    private val bookmarks = MutableStateFlow<List<VideoBookmarkEntity>>(emptyList())

    override fun observeBookmarks(mediaUri: String): Flow<List<VideoBookmarkEntity>> {
        if (mediaUri.isBlank()) return flowOf(emptyList())
        val hash = UriHash.of(mediaUri)
        return bookmarks.map { list ->
            list.filter { it.uriHash == hash }
                .sortedWith(compareBy<VideoBookmarkEntity> { it.positionMs }.thenBy { it.id })
        }
    }

    override suspend fun addBookmark(mediaUri: String, positionMs: Long, label: String): Long =
        synchronized(lock) {
            if (mediaUri.isBlank()) return 0L
            val id = nextId++
            val safePositionMs = positionMs.coerceAtLeast(0L)
            val resolvedLabel = label.trim().ifBlank { formatBookmarkFallbackLabel(safePositionMs) }
            bookmarks.value = bookmarks.value + VideoBookmarkEntity(
                id = id,
                uriHash = UriHash.of(mediaUri),
                mediaUri = mediaUri,
                positionMs = safePositionMs,
                label = resolvedLabel,
                createdAtEpochMs = clock(),
            )
            id
        }

    override suspend fun renameBookmark(id: Long, label: String) {
        synchronized(lock) {
            if (id <= 0L) return
            val resolvedLabel = label.trim()
            if (resolvedLabel.isEmpty()) return
            bookmarks.value = bookmarks.value.map { entity ->
                if (entity.id == id) entity.copy(label = resolvedLabel) else entity
            }
        }
    }

    override suspend fun deleteBookmark(id: Long) {
        synchronized(lock) {
            if (id <= 0L) return
            bookmarks.value = bookmarks.value.filterNot { it.id == id }
        }
    }
}
