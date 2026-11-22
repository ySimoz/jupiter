package com.jupiter.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId")],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val playlistId: Long,
    val mediaUri: String,
    val title: String,
    val isNetworkStream: Boolean = false,
    val headersJson: String? = null,
    val durationMs: Long = 0L,
    val positionOrder: Int = 0,
    val addedAtEpochMs: Long = 0L,
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val videoCount: Int,
    val streamCount: Int,
    val previewUri: String?,
    val updatedAtEpochMs: Long,
)
