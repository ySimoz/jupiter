package com.jupiter.app.data.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.net.URLDecoder

interface UriResolver {
    fun resolveToMpvPath(uriString: String, fallbackTitle: String = ""): Result<MpvSource>
}

class AndroidUriResolver(
    private val context: Context,
) : UriResolver {

    override fun resolveToMpvPath(uriString: String, fallbackTitle: String): Result<MpvSource> {
        if (uriString.isBlank()) {
            return Result.failure(IllegalArgumentException("Media URI is empty"))
        }

        val parsed = runCatching { Uri.parse(uriString) }.getOrElse { error ->
            return Result.failure(error)
        }
        val scheme = parsed.scheme?.lowercase()

        return when (scheme) {
            "content" -> resolveContentUri(parsed, uriString, fallbackTitle)
            "file" -> resolveFileUri(parsed, uriString, fallbackTitle)
            else -> {
                val resolvedName = fallbackTitle.ifBlank { extractTitleFromUri(uriString) }
                Result.success(
                    MpvSource(
                        mpvPath = uriString,
                        displayName = resolvedName,
                        originalUri = uriString,
                    ),
                )
            }
        }
    }

    private fun resolveContentUri(
        uri: Uri,
        uriString: String,
        fallbackTitle: String,
    ): Result<MpvSource> = runCatching {
        val displayName = queryDisplayName(uri)
            ?.takeIf { it.isNotBlank() }
            ?: fallbackTitle.ifBlank { extractTitleFromUri(uriString) }

        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Unable to open file descriptor for $uriString")
        val fd = pfd.detachFd()

        MpvSource(
            mpvPath = "fdclose://$fd",
            displayName = displayName,
            originalUri = uriString,
        )
    }

    private fun resolveFileUri(
        uri: Uri,
        uriString: String,
        fallbackTitle: String,
    ): Result<MpvSource> {
        val filePath = uri.path
        if (filePath.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Invalid file URI: $uriString"))
        }
        val displayName = fallbackTitle.ifBlank {
            File(filePath).name.ifBlank { extractTitleFromUri(uriString) }
        }
        return Result.success(
            MpvSource(
                mpvPath = filePath,
                displayName = displayName,
                originalUri = uriString,
            ),
        )
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (col >= 0 && cursor.moveToFirst()) {
                cursor.getString(col)
            } else {
                null
            }
        }
    }.getOrNull()

    companion object {
        fun extractTitleFromUri(uri: String): String {
            val clean = uri.substringBefore('?').substringBefore('#').trimEnd('/')
            val segment = clean.substringAfterLast('/')
            if (segment.isBlank()) return uri
            return runCatching {
                URLDecoder.decode(segment.replace("+", "%2B"), Charsets.UTF_8.name())
            }.getOrDefault(segment).ifBlank { uri }
        }
    }
}
