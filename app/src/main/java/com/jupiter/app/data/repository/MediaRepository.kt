package com.jupiter.app.data.repository

import com.jupiter.app.data.local.InMemoryLibraryFolderPreferences
import com.jupiter.app.data.local.LibraryFolderPreferences
import com.jupiter.app.data.local.MediaHistoryDao
import com.jupiter.app.data.local.MediaHistoryEntity
import com.jupiter.app.data.media.LocalMediaScanner
import com.jupiter.app.data.media.LocalVideoItem
import com.jupiter.app.data.media.NoOpSafFolderScanner
import com.jupiter.app.data.media.SafFolderScanner
import com.jupiter.app.data.media.UriHash
import com.jupiter.app.data.media.fallbackDisplayNameFromTreeUri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface MediaRepository {
    suspend fun scanLocalVideos(): List<LocalVideoItem>
    suspend fun scanCustomFolderVideos(): List<LocalVideoItem> = emptyList()
    fun observeContinueWatching(limit: Int = 20): Flow<List<MediaHistoryEntity>>
    suspend fun getHistory(uri: String): MediaHistoryEntity?
    suspend fun saveProgress(
        uri: String,
        displayName: String,
        positionMs: Long,
        durationMs: Long,
        playbackSpeed: Float = 1.0f,
        isNetworkStream: Boolean = false,
    )

    suspend fun removeHistory(uri: String)
    suspend fun clearHistory()

    fun observeHiddenFolders(): Flow<Set<String>> = emptyFlow()
    fun observeRemovedFolders(): Flow<Set<String>> = flowOf(emptySet())
    fun observeCustomFolderUris(): Flow<Set<String>> = emptyFlow()
    suspend fun setFolderHidden(folderName: String, hidden: Boolean) = Unit
    suspend fun setFolderRemoved(folderName: String, removed: Boolean) {}
    suspend fun addCustomFolder(treeUri: String) = Unit
    suspend fun removeCustomFolder(treeUri: String) = Unit
    fun resolveCustomFolderName(treeUri: String): String =
        fallbackDisplayNameFromTreeUri(treeUri)
}

object ResumeRules {
    const val MIN_RESUME_POSITION_MS = 1_000L
    private const val COMPLETION_THRESHOLD_RATIO = 0.95

    fun isCompleted(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        val clamped = positionMs.coerceIn(0L, durationMs)
        return clamped.toDouble() / durationMs.toDouble() > COMPLETION_THRESHOLD_RATIO
    }

    fun normalizePositionMs(positionMs: Long, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        if (isCompleted(positionMs, durationMs)) return 0L
        if (positionMs < MIN_RESUME_POSITION_MS) return 0L
        return positionMs.coerceIn(0L, durationMs)
    }
}

class DefaultMediaRepository(
    private val scanner: LocalMediaScanner,
    private val mediaHistoryDao: MediaHistoryDao,
    private val folderPreferences: LibraryFolderPreferences = InMemoryLibraryFolderPreferences(),
    private val safScanner: SafFolderScanner = NoOpSafFolderScanner,
    private val clock: () -> Long = System::currentTimeMillis,
) : MediaRepository {

    override suspend fun scanLocalVideos(): List<LocalVideoItem> {
        val mediaStoreVideos = scanner.scanVideos()
        val safVideos = scanCustomFolderVideos()
        if (safVideos.isEmpty()) return mediaStoreVideos
        if (mediaStoreVideos.isEmpty()) return safVideos
        val mediaStoreKeys = mediaStoreVideos
            .asSequence()
            .filter { it.sizeBytes > 0L }
            .map { video ->
                Triple(
                    video.bucketName.lowercase(),
                    video.displayName.lowercase(),
                    video.sizeBytes,
                )
            }
            .toSet()
        val dedupedSafVideos = safVideos.filter { saf ->
            saf.sizeBytes <= 0L ||
                Triple(
                    saf.bucketName.lowercase(),
                    saf.displayName.lowercase(),
                    saf.sizeBytes,
                ) !in mediaStoreKeys
        }
        return (mediaStoreVideos + dedupedSafVideos).distinctBy { it.contentUri }
    }

    override suspend fun scanCustomFolderVideos(): List<LocalVideoItem> {
        val uris = folderPreferences.customFolderUris.first()
        if (uris.isEmpty()) return emptyList()
        return safScanner.scanFolders(uris).distinctBy { it.contentUri }
    }

    override fun observeHiddenFolders(): Flow<Set<String>> = folderPreferences.hiddenFolders

    override fun observeRemovedFolders(): Flow<Set<String>> = folderPreferences.observeRemovedFolders()

    override fun observeCustomFolderUris(): Flow<Set<String>> = folderPreferences.customFolderUris

    override suspend fun setFolderHidden(folderName: String, hidden: Boolean) {
        folderPreferences.setFolderHidden(folderName, hidden)
    }

    override suspend fun setFolderRemoved(folderName: String, removed: Boolean) {
        folderPreferences.setFolderRemoved(folderName, removed)
    }

    override suspend fun addCustomFolder(treeUri: String) {
        folderPreferences.addCustomFolderUri(treeUri)
    }

    override suspend fun removeCustomFolder(treeUri: String) {
        folderPreferences.removeCustomFolderUri(treeUri)
    }

    override fun resolveCustomFolderName(treeUri: String): String =
        safScanner.resolveFolderDisplayName(treeUri)

    override fun observeContinueWatching(limit: Int): Flow<List<MediaHistoryEntity>> =
        mediaHistoryDao.observeRecent(limit).map { items ->
            items.filter { it.positionMs > 0L }.take(limit)
        }

    override suspend fun getHistory(uri: String): MediaHistoryEntity? {
        if (uri.isBlank()) return null
        return mediaHistoryDao.getByHash(UriHash.of(uri))
    }

    override suspend fun saveProgress(
        uri: String,
        displayName: String,
        positionMs: Long,
        durationMs: Long,
        playbackSpeed: Float,
        isNetworkStream: Boolean,
    ) {
        if (uri.isBlank()) return
        val normalizedPosition = ResumeRules.normalizePositionMs(positionMs, durationMs)
        val hash = UriHash.of(uri)
        val existing = mediaHistoryDao.getByHash(hash)
        val resolvedPosition = if (
            !ResumeRules.isCompleted(positionMs, durationMs) &&
            positionMs < ResumeRules.MIN_RESUME_POSITION_MS &&
            existing != null &&
            existing.positionMs > 0L
        ) {
            existing.positionMs
        } else {
            normalizedPosition
        }
        val resolvedDuration = if (durationMs > 0L) durationMs else (existing?.durationMs ?: 0L)
        val resolvedName = displayName.ifBlank { existing?.displayName ?: uri }
        mediaHistoryDao.upsert(
            MediaHistoryEntity(
                uriHash = hash,
                uri = uri,
                displayName = resolvedName,
                durationMs = resolvedDuration.coerceAtLeast(0L),
                positionMs = resolvedPosition,
                lastPlayedEpochMs = clock(),
                selectedAudioTrackId = existing?.selectedAudioTrackId,
                selectedSubTrackId = existing?.selectedSubTrackId,
                playbackSpeed = playbackSpeed,
                isNetworkStream = isNetworkStream,
            ),
        )
    }

    override suspend fun removeHistory(uri: String) {
        if (uri.isBlank()) return
        mediaHistoryDao.deleteByHash(UriHash.of(uri))
    }

    override suspend fun clearHistory() {
        mediaHistoryDao.clearAll()
    }
}
