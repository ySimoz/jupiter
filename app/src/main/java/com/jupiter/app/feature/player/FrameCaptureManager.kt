package com.jupiter.app.feature.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import com.jupiter.app.core.di.PlaybackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

sealed interface FrameCaptureResult {
    data class Saved(
        val displayName: String,
        val relativePath: String,
    ) : FrameCaptureResult

    data class Failed(
        val message: String,
    ) : FrameCaptureResult
}

internal data class CropBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

internal fun computeLetterboxCropBounds(
    surfaceWidth: Int,
    surfaceHeight: Int,
    videoWidth: Int,
    videoHeight: Int,
): CropBounds? {
    if (surfaceWidth <= 0 || surfaceHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
        return null
    }
    val videoCross = videoWidth.toLong() * surfaceHeight.toLong()
    val surfaceCross = surfaceWidth.toLong() * videoHeight.toLong()
    val fittedWidth: Int
    val fittedHeight: Int
    if (videoCross > surfaceCross) {
        fittedWidth = surfaceWidth
        fittedHeight = (surfaceWidth.toDouble() * videoHeight.toDouble() / videoWidth.toDouble())
            .roundToInt()
            .coerceIn(1, surfaceHeight)
    } else {
        fittedHeight = surfaceHeight
        fittedWidth = (surfaceHeight.toDouble() * videoWidth.toDouble() / videoHeight.toDouble())
            .roundToInt()
            .coerceIn(1, surfaceWidth)
    }
    val left = (surfaceWidth - fittedWidth) / 2
    val top = (surfaceHeight - fittedHeight) / 2
    val right = left + fittedWidth
    val bottom = top + fittedHeight
    if (left <= 1 && top <= 1 && (surfaceWidth - right) <= 1 && (surfaceHeight - bottom) <= 1) {
        return null
    }
    return CropBounds(left = left, top = top, right = right, bottom = bottom)
}

interface FrameCaptureManager {
    suspend fun captureCurrentFrame(
        title: String,
        surfaceView: SurfaceView? = null,
        videoWidth: Int = 0,
        videoHeight: Int = 0,
        cropLetterbox: Boolean = false,
    ): FrameCaptureResult
}

object NoOpFrameCaptureManager : FrameCaptureManager {
    override suspend fun captureCurrentFrame(
        title: String,
        surfaceView: SurfaceView?,
        videoWidth: Int,
        videoHeight: Int,
        cropLetterbox: Boolean,
    ): FrameCaptureResult = FrameCaptureResult.Failed("Frame capture unavailable")
}

class AndroidFrameCaptureManager internal constructor(
    private val playbackController: PlaybackController,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cacheDirProvider: () -> File?,
    private val saveToMediaStore: (tempFile: File, displayName: String) -> FrameCaptureResult,
    private val pixelCopyProvider: (suspend (
        surfaceView: SurfaceView,
        videoWidth: Int,
        videoHeight: Int,
        cropLetterbox: Boolean,
        displayName: String,
    ) -> FrameCaptureResult)? = null,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : FrameCaptureManager {

    constructor(
        context: Context,
        playbackController: PlaybackController,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        playbackController = playbackController,
        ioDispatcher = ioDispatcher,
        cacheDirProvider = { (context.applicationContext ?: context).cacheDir },
        saveToMediaStore = { tempFile, displayName ->
            writePngToMediaStore(
                context = context.applicationContext ?: context,
                tempFile = tempFile,
                displayName = displayName,
            )
        },
        pixelCopyProvider = { surfaceView, videoWidth, videoHeight, cropLetterbox, displayName ->
            captureSurfaceWithPixelCopy(
                context = context.applicationContext ?: context,
                surfaceView = surfaceView,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                cropLetterbox = cropLetterbox,
                displayName = displayName,
                ioDispatcher = ioDispatcher,
            )
        },
    )

    override suspend fun captureCurrentFrame(
        title: String,
        surfaceView: SurfaceView?,
        videoWidth: Int,
        videoHeight: Int,
        cropLetterbox: Boolean,
    ): FrameCaptureResult = withContext(ioDispatcher) {
        val displayName = buildCaptureFileName(title = title, epochMs = nowMs())
        var mpvSetupError: String? = null

        val cacheDir = cacheDirProvider()
        if (cacheDir == null) {
            mpvSetupError = "Cache directory unavailable"
        } else if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            mpvSetupError = "Unable to prepare temporary capture directory"
        } else {
            val tempFile = try {
                File.createTempFile("jupiter_frame_", ".png", cacheDir)
            } catch (t: Throwable) {
                mpvSetupError = t.message ?: "Failed to create temporary capture file"
                null
            }
            if (tempFile != null) {
                try {
                    if (captureToTempFile(tempFile)) {
                        return@withContext saveToMediaStore(tempFile, displayName)
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    if (surfaceView == null || pixelCopyProvider == null) {
                        return@withContext FrameCaptureResult.Failed(
                            t.message ?: "Failed to save frame capture",
                        )
                    }
                } finally {
                    runCatching { tempFile.delete() }
                }
            }
        }

        if (surfaceView != null && pixelCopyProvider != null) {
            return@withContext try {
                pixelCopyProvider.invoke(
                    surfaceView,
                    videoWidth,
                    videoHeight,
                    cropLetterbox,
                    displayName,
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                FrameCaptureResult.Failed(t.message ?: "Failed to save frame capture")
            }
        }

        FrameCaptureResult.Failed(mpvSetupError ?: "Failed to capture current video frame")
    }

    internal suspend fun captureToTempFile(tempFile: File): Boolean = withContext(ioDispatcher) {
        tempFile.delete()
        val subtitlesRc = playbackController.screenshotToFile(tempFile.absolutePath, "subtitles")
        if (subtitlesRc < 0) {
            runCatching { Log.w(TAG, "mpv screenshot-to-file subtitles failed rc=$subtitlesRc") }
        } else if (tempFile.isFile && tempFile.length() > 0L) {
            return@withContext true
        }

        tempFile.delete()
        val videoRc = playbackController.screenshotToFile(tempFile.absolutePath, "video")
        if (videoRc < 0) {
            runCatching { Log.w(TAG, "mpv screenshot-to-file video failed rc=$videoRc") }
            tempFile.delete()
            return@withContext false
        }
        tempFile.isFile && tempFile.length() > 0L
    }

    internal fun buildCaptureFileName(title: String, epochMs: Long): String {
        val sanitizedTitle = sanitizeTitle(title)
        val timestamp = TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(epochMs).atZone(zoneId))
        return "Jupiter_${sanitizedTitle}_${timestamp}.png"
    }

    private companion object {
        private const val TAG = "FrameCaptureManager"
        private const val PIXEL_COPY_TIMEOUT_MS = 5_000L
        private const val PIXEL_COPY_INVALID_SURFACE = -1000
        private const val MAX_TITLE_LENGTH = 40
        private const val FALLBACK_TITLE = "Video"
        private const val RELATIVE_CAPTURE_DIR = "Pictures/Jupiter"
        private val NON_ALNUM_RUN = Regex("[^A-Za-z0-9]+")
        private val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)

        private fun sanitizeTitle(rawTitle: String): String {
            val normalized = NON_ALNUM_RUN.replace(rawTitle.trim(), "_").trim('_')
            val bounded = normalized.take(MAX_TITLE_LENGTH).trimEnd('_')
            return bounded.ifEmpty { FALLBACK_TITLE }
        }

        private suspend fun captureSurfaceWithPixelCopy(
            context: Context,
            surfaceView: SurfaceView,
            videoWidth: Int,
            videoHeight: Int,
            cropLetterbox: Boolean,
            displayName: String,
            ioDispatcher: CoroutineDispatcher,
        ): FrameCaptureResult {
            val mainHandler = Handler(Looper.getMainLooper())
            var capturedBitmap: Bitmap? = null
            try {
                val copyResult: Int? = withTimeoutOrNull(PIXEL_COPY_TIMEOUT_MS) {
                    suspendCancellableCoroutine { continuation ->
                        mainHandler.post {
                            if (!continuation.isActive) return@post
                            val holderSurface = surfaceView.holder?.surface
                            val sw = surfaceView.width
                            val sh = surfaceView.height
                            if (holderSurface == null || !holderSurface.isValid || sw <= 0 || sh <= 0) {
                                continuation.resume(PIXEL_COPY_INVALID_SURFACE)
                                return@post
                            }
                            val cropBounds = if (cropLetterbox) {
                                computeLetterboxCropBounds(sw, sh, videoWidth, videoHeight)
                            } else {
                                null
                            }
                            val targetWidth = cropBounds?.width ?: sw
                            val targetHeight = cropBounds?.height ?: sh
                            val bitmap = try {
                                Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            } catch (t: Throwable) {
                                runCatching { Log.w(TAG, "Bitmap allocation failed: ${t.message}") }
                                continuation.resume(PixelCopy.ERROR_UNKNOWN)
                                return@post
                            }
                            capturedBitmap = bitmap
                            val srcRect = cropBounds?.let { Rect(it.left, it.top, it.right, it.bottom) }
                            try {
                                PixelCopy.request(
                                    surfaceView,
                                    srcRect,
                                    bitmap,
                                    { result ->
                                        if (continuation.isActive) {
                                            continuation.resume(result)
                                        }
                                    },
                                    mainHandler,
                                )
                            } catch (t: Throwable) {
                                runCatching { Log.w(TAG, "PixelCopy.request failed: ${t.message}") }
                                if (continuation.isActive) {
                                    continuation.resume(PixelCopy.ERROR_UNKNOWN)
                                }
                            }
                        }
                    }
                }
                if (copyResult == null) {
                    return FrameCaptureResult.Failed("Timed out capturing current video frame")
                }
                if (copyResult == PIXEL_COPY_INVALID_SURFACE) {
                    return FrameCaptureResult.Failed("Failed to capture current video frame")
                }
                if (copyResult != PixelCopy.SUCCESS) {
                    runCatching { Log.w(TAG, "PixelCopy failed copy=$copyResult") }
                    return FrameCaptureResult.Failed("Failed to capture current video frame (copy=$copyResult)")
                }
                val bitmap = capturedBitmap
                    ?: return FrameCaptureResult.Failed("Failed to capture current video frame")
                return withContext(ioDispatcher) {
                    writeBitmapToMediaStore(
                        context = context,
                        bitmap = bitmap,
                        displayName = displayName,
                    )
                }
            } finally {
                capturedBitmap?.let { bmp ->
                    if (!bmp.isRecycled) {
                        bmp.recycle()
                    }
                }
            }
        }

        private fun writePngToMediaStore(
            context: Context,
            tempFile: File,
            displayName: String,
        ): FrameCaptureResult = writePngStreamToMediaStore(context, displayName) { out ->
            tempFile.inputStream().use { input ->
                input.copyTo(out)
            }
        }

        private fun writeBitmapToMediaStore(
            context: Context,
            bitmap: Bitmap,
            displayName: String,
        ): FrameCaptureResult = writePngStreamToMediaStore(context, displayName) { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw IOException("Failed to encode PNG frame")
            }
        }

        private inline fun writePngStreamToMediaStore(
            context: Context,
            displayName: String,
            writeBlock: (OutputStream) -> Unit,
        ): FrameCaptureResult {
            val relativePath = "${Environment.DIRECTORY_PICTURES}/Jupiter"
            val resolver = context.contentResolver
            val pendingValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, pendingValues)
                ?: return FrameCaptureResult.Failed("Failed to create MediaStore entry")

            return try {
                val outputStream = resolver.openOutputStream(uri)
                    ?: throw IOException("Failed to open MediaStore output stream")
                outputStream.use { out ->
                    writeBlock(out)
                    out.flush()
                }
                val publishValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, publishValues, null, null)
                FrameCaptureResult.Saved(
                    displayName = displayName,
                    relativePath = RELATIVE_CAPTURE_DIR,
                )
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                if (t is CancellationException) throw t
                FrameCaptureResult.Failed(t.message ?: "Failed to write frame capture")
            }
        }
    }
}
