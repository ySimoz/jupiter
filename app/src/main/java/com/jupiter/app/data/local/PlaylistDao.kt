package com.jupiter.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAtEpochMs DESC, id DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_items ORDER BY playlistId ASC, positionOrder ASC, id ASC")
    fun observeAllItems(): Flow<List<PlaylistItemEntity>>

    @Query(
        "SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY positionOrder ASC, id ASC",
    )
    fun observeItemsForPlaylist(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query(
        "SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY positionOrder ASC, id ASC",
    )
    suspend fun getItemsForPlaylist(playlistId: Long): List<PlaylistItemEntity>

    @Query("SELECT MAX(positionOrder) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getMaxOrder(playlistId: Long): Int?

    @Query("SELECT playlistId FROM playlist_items WHERE id = :itemId LIMIT 1")
    suspend fun getPlaylistIdForItem(itemId: Long): Long?

    @Insert
    suspend fun insertPlaylist(entity: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :newName, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String, updatedAtEpochMs: Long)

    @Query("UPDATE playlists SET updatedAtEpochMs = :updatedAtEpochMs WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: Long, updatedAtEpochMs: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert
    suspend fun insertItems(items: List<PlaylistItemEntity>): List<Long>

    @Query("UPDATE playlist_items SET positionOrder = :positionOrder WHERE id = :itemId")
    suspend fun updateItemOrder(itemId: Long, positionOrder: Int)

    @Query("DELETE FROM playlist_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)
}
