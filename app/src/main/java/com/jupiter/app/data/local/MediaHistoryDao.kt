package com.jupiter.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaHistoryDao {
    @Query(
        "SELECT * FROM media_history WHERE positionMs > 0 ORDER BY lastPlayedEpochMs DESC LIMIT :limit",
    )
    fun observeRecent(limit: Int = 20): Flow<List<MediaHistoryEntity>>

    @Query("SELECT * FROM media_history ORDER BY lastPlayedEpochMs DESC")
    fun observeAll(): Flow<List<MediaHistoryEntity>>

    @Query("SELECT * FROM media_history WHERE uriHash = :uriHash LIMIT 1")
    suspend fun getByHash(uriHash: String): MediaHistoryEntity?

    @Upsert
    suspend fun upsert(entity: MediaHistoryEntity)

    @Query("DELETE FROM media_history WHERE uriHash = :uriHash")
    suspend fun deleteByHash(uriHash: String)

    @Query("DELETE FROM media_history")
    suspend fun clearAll()
}
