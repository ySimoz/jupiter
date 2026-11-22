package com.jupiter.app.data.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SafFolderScanner {
    suspend fun scanFolders(treeUris: Set<String>): List<LocalVideoItem>
    fun resolveFolderDisplayName(treeUri: String): String
}

object NoOpSafFolderScanner : SafFolderScanner {
    override suspend fun scanFolders(treeUris: Set<String>): List<LocalVideoItem> = emptyList()

    override fun resolveFolderDisplayName(treeUri: String): String =
        fallbackDisplayNameFromTreeUri(treeUri)
}

class AndroidSafFolderScanner(
    private val context: Context,
) : SafFolderScanner {

    override suspend fun scanFolders(treeUris: Set<String>): List<LocalVideoItem> =
        withContext(Dispatchers.IO) {
            if (treeUris.isEmpty()) return@withContext emptyList()
            val items = mutableListOf<LocalVideoItem>()
            for (treeUriString in treeUris) {
                items += scanSingleTree(treeUriString)
            }
            items
        }

    override fun resolveFolderDisplayName(treeUri: String): String {
        val parsed = runCatching { Uri.parse(treeUri) }.getOrNull()
            ?: return fallbackDisplayNameFromTreeUri(treeUri)
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(parsed) }.getOrNull()
        if (treeDocId != null) {
            val docUri = runCatching {
                DocumentsContract.buildDocumentUriUsingTree(parsed, treeDocId)
            }.getOrNull()
            if (docUri != null) {
                val queried = runCatching {
                    context.contentResolver.query(
                        docUri,
                        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val col = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                            if (col >= 0) cursor.getString(col) else null
                        } else {
                            null
                        }
                    }
                }.getOrNull()
                if (!queried.isNullOrBlank()) {
                    return queried
                }
            }
        }
        return fallbackDisplayNameFromTreeUri(treeUri, treeDocId)
    }

    private fun scanSingleTree(treeUriString: String): List<LocalVideoItem> {
        return try {
            val treeUri = Uri.parse(treeUriString)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val folderName = resolveFolderDisplayName(treeUriString)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )

            val results = mutableListOf<LocalVideoItem>()
            val queue = ArrayDeque<Pair<String, Int>>()
            val visitedDirs = HashSet<String>()
            queue.add(rootDocId to 0)
            visitedDirs.add(rootDocId)

            while (queue.isNotEmpty()) {
                val (currentDocId, depth) = queue.removeFirst()
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
                context.contentResolver.query(
                    childrenUri,
                    projection,
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    if (idCol < 0) return@use

                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idCol) ?: continue
                        val displayName = if (nameCol >= 0) {
                            cursor.getString(nameCol)?.takeIf { it.isNotBlank() } ?: "Video"
                        } else {
                            "Video"
                        }
                        val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) else null

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < MAX_SCAN_DEPTH && visitedDirs.add(docId)) {
                                queue.add(docId to depth + 1)
                            }
                            continue
                        }

                        if (!isVideoDocument(mimeType, displayName)) continue

                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
                        val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                        val lastModifiedMs = if (modifiedCol >= 0) {
                            cursor.getLong(modifiedCol).coerceAtLeast(0L)
                        } else {
                            0L
                        }

                        results += LocalVideoItem(
                            id = stableNegativeId(docUri),
                            contentUri = docUri,
                            displayName = displayName,
                            bucketName = folderName,
                            durationMs = 0L,
                            sizeBytes = sizeBytes,
                            dateModifiedSec = lastModifiedMs / 1000L,
                            width = 0,
                            height = 0,
                            sourceTreeUri = treeUriString,
                        )
                    }
                }
            }
            results
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private companion object {
        const val MAX_SCAN_DEPTH = 4
    }
}

internal fun isVideoDocument(mimeType: String?, displayName: String): Boolean {
    if (!mimeType.isNullOrBlank()) {
        if (mimeType.startsWith("video/", ignoreCase = true)) return true
        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) return false
    }
    val ext = displayName.substringAfterLast('.', "").lowercase()
    return ext in VIDEO_EXTENSIONS
}

internal fun fallbackDisplayNameFromTreeUri(
    treeUri: String,
    treeDocId: String? = null,
): String {
    val raw = treeDocId ?: runCatching {
        java.net.URLDecoder.decode(treeUri.substringAfterLast("/tree/", ""), Charsets.UTF_8.name())
    }.getOrDefault(treeUri.substringAfterLast("/tree/", ""))
    val afterColon = raw.substringAfter(':', raw)
    val segment = afterColon.trimEnd('/').substringAfterLast('/')
    return segment.ifBlank { "Folder" }
}

private fun stableNegativeId(uri: String): Long {
    var hash = -3750763034362895579L
    for (ch in uri) {
        hash = hash xor ch.code.toLong()
        hash *= 1099511628211L
    }
    val positive = hash and Long.MAX_VALUE
    return -positive.coerceAtLeast(1L)
}

private val VIDEO_EXTENSIONS = setOf(
    "mp4",
    "mkv",
    "webm",
    "avi",
    "mov",
    "m4v",
    "ts",
    "m2ts",
    "mts",
    "flv",
    "wmv",
    "3gp",
    "mpg",
    "mpeg",
    "ogv",
)
