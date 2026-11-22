package com.jupiter.app.data.media

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface LocalMediaScanner {
    suspend fun scanVideos(): List<LocalVideoItem>
}

class MediaStoreLocalMediaScanner(
    private val context: Context,
) : LocalMediaScanner {

    override suspend fun scanVideos(): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        val items = mutableListOf<LocalVideoItem>()

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)

                if (idCol < 0) return@withContext emptyList()

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id,
                    ).toString()
                    val displayName = if (nameCol >= 0) {
                        cursor.getString(nameCol)?.takeIf { it.isNotBlank() } ?: "Video $id"
                    } else {
                        "Video $id"
                    }
                    val bucketName = if (bucketCol >= 0) {
                        cursor.getString(bucketCol)?.takeIf { it.isNotBlank() } ?: DEFAULT_BUCKET
                    } else {
                        DEFAULT_BUCKET
                    }
                    val durationMs = if (durationCol >= 0) cursor.getLong(durationCol).coerceAtLeast(0L) else 0L
                    val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                    val dateModifiedSec = if (dateCol >= 0) cursor.getLong(dateCol).coerceAtLeast(0L) else 0L
                    val width = if (widthCol >= 0) cursor.getInt(widthCol).coerceAtLeast(0) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol).coerceAtLeast(0) else 0

                    items += LocalVideoItem(
                        id = id,
                        contentUri = contentUri,
                        displayName = displayName,
                        bucketName = bucketName,
                        durationMs = durationMs,
                        sizeBytes = sizeBytes,
                        dateModifiedSec = dateModifiedSec,
                        width = width,
                        height = height,
                    )
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        }

        items
    }

    private companion object {
        const val DEFAULT_BUCKET = "Videos"
    }
}
