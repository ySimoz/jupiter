package com.jupiter.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoBookmarkDao {
    @Query(
        "SELECT * FROM video_bookmarks WHERE uriHash = :uriHash ORDER BY positionMs ASC, id ASC",
    )
    fun observeByUriHash(uriHash: String): Flow<List<VideoBookmarkEntity>>

    @Insert
    suspend fun insert(entity: VideoBookmarkEntity): Long

    @Query("UPDATE video_bookmarks SET label = :label WHERE id = :id")
    suspend fun rename(id: Long, label: String)

    @Query("DELETE FROM video_bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
