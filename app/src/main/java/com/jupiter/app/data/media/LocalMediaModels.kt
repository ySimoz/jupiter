package com.jupiter.app.data.media

import java.security.MessageDigest

data class LocalVideoItem(
    val id: Long,
    val contentUri: String,
    val displayName: String,
    val bucketName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateModifiedSec: Long,
    val width: Int,
    val height: Int,
    val sourceTreeUri: String? = null,
)

data class LocalFolderSummary(
    val name: String,
    val videoCount: Int,
    val totalDurationMs: Long,
    val previewUri: String,
    val isCustom: Boolean = false,
    val treeUri: String? = null,
)

enum class LibrarySortOption(val label: String) {
    DATE("Date"),
    NAME("Name"),
    DURATION("Duration"),
    SIZE("Size"),
}

data class MpvSource(
    val mpvPath: String,
    val displayName: String,
    val originalUri: String,
)

object UriHash {
    fun of(uri: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val hex = (byte.toInt() and 0xFF).toString(16)
                if (hex.length == 1) append('0')
                append(hex)
            }
        }
    }
}
