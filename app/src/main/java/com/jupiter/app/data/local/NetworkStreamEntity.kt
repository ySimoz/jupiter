package com.jupiter.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_streams")
data class NetworkStreamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val url: String,
    val userAgent: String? = null,
    val referer: String? = null,
    val customHeadersJson: String? = null,
    val isPinned: Boolean = false,
    val createdAtEpochMs: Long,
    val lastPlayedEpochMs: Long? = null,
)
