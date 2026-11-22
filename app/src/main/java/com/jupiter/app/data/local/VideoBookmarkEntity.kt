package com.jupiter.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_bookmarks",
    indices = [Index("uriHash")],
)
data class VideoBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uriHash: String,
    val mediaUri: String,
    val positionMs: Long,
    val label: String,
    val createdAtEpochMs: Long,
)
