package com.jupiter.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkStreamDao {
    @Query(
        "SELECT * FROM network_streams ORDER BY isPinned DESC, COALESCE(lastPlayedEpochMs, createdAtEpochMs) DESC",
    )
    fun observeAll(): Flow<List<NetworkStreamEntity>>

    @Query("SELECT * FROM network_streams WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NetworkStreamEntity?

    @Upsert
    suspend fun upsert(entity: NetworkStreamEntity): Long

    @Query("DELETE FROM network_streams WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE network_streams SET lastPlayedEpochMs = :epochMs WHERE id = :id")
    suspend fun updateLastPlayed(id: Long, epochMs: Long)

    @Query("UPDATE network_streams SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)
}
