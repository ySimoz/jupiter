package com.jupiter.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_history")
data class MediaHistoryEntity(
    @PrimaryKey val uriHash: String,
    val uri: String,
    val displayName: String,
    val durationMs: Long,
    val positionMs: Long,
    val lastPlayedEpochMs: Long,
    val selectedAudioTrackId: Int? = null,
    val selectedSubTrackId: Int? = null,
    val playbackSpeed: Float = 1.0f,
    val isNetworkStream: Boolean = false,
)
