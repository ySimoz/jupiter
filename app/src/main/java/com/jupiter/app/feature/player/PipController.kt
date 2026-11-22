package com.jupiter.app.feature.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import com.jupiter.app.R

object PipAspectRatio {
    const val MIN_NUMERATOR = 100
    const val MIN_DENOMINATOR = 239
    const val MAX_NUMERATOR = 239
    const val MAX_DENOMINATOR = 100

    private const val MIN_RATIO = MIN_NUMERATOR.toDouble() / MIN_DENOMINATOR.toDouble()
    private const val MAX_RATIO = MAX_NUMERATOR.toDouble() / MAX_DENOMINATOR.toDouble()

    fun clampDimensions(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 16 to 9
        val ratio = width.toDouble() / height.toDouble()
        return when {
            ratio < MIN_RATIO -> MIN_NUMERATOR to MIN_DENOMINATOR
            ratio > MAX_RATIO -> MAX_NUMERATOR to MAX_DENOMINATOR
            else -> width to height
        }
    }

    fun toRational(width: Int, height: Int): Rational {
        val (w, h) = clampDimensions(width, height)
        return Rational(w, h)
    }
}

enum class PipAction {
    SEEK_BACK,
    PLAY_PAUSE,
    SEEK_FORWARD,
}

data class PipPlaybackSnapshot(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val autoEnterPip: Boolean = true,
)

interface PipHostController {
    fun updatePlayback(snapshot: PipPlaybackSnapshot)
    fun enterPip(): Boolean
    fun setActionHandler(handler: ((PipAction) -> Unit)?)
}

class AndroidPipHostController(
    private val activity: ComponentActivity,
) : PipHostController {

    private var currentSnapshot = PipPlaybackSnapshot()
    private var hasAppliedParams = false
    private var actionHandler: ((PipAction) -> Unit)? = null
    private var isReceiverRegistered = false

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = when (intent?.action) {
                ACTION_SEEK_BACK -> PipAction.SEEK_BACK
                ACTION_PLAY_PAUSE -> PipAction.PLAY_PAUSE
                ACTION_SEEK_FORWARD -> PipAction.SEEK_FORWARD
                else -> return
            }
            actionHandler?.invoke(action)
        }
    }

    fun register() {
        if (isReceiverRegistered || !isPipSupported()) return
        val filter = IntentFilter().apply {
            addAction(ACTION_SEEK_BACK)
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_SEEK_FORWARD)
        }
        ContextCompat.registerReceiver(
            activity,
            actionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        isReceiverRegistered = true
    }

    fun unregister() {
        if (!isReceiverRegistered) return
        isReceiverRegistered = false
        runCatching { activity.unregisterReceiver(actionReceiver) }
    }

    override fun setActionHandler(handler: ((PipAction) -> Unit)?) {
        actionHandler = handler
    }

    override fun updatePlayback(snapshot: PipPlaybackSnapshot) {
        if (!isPipSupported()) {
            currentSnapshot = snapshot
            return
        }
        if (hasAppliedParams && snapshot == currentSnapshot) return
        currentSnapshot = snapshot
        hasAppliedParams = true
        runCatching {
            activity.setPictureInPictureParams(buildParams(snapshot))
        }
    }

    override fun enterPip(): Boolean {
        if (!isPipSupported()) return false
        if (activity.isInPictureInPictureMode) return true
        return runCatching {
            activity.enterPictureInPictureMode(buildParams(currentSnapshot))
        }.getOrDefault(false)
    }

    fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            currentSnapshot.isActive &&
            currentSnapshot.isPlaying &&
            currentSnapshot.autoEnterPip
        ) {
            enterPip()
        }
    }

    private fun isPipSupported(): Boolean =
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun buildParams(snapshot: PipPlaybackSnapshot): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(PipAspectRatio.toRational(snapshot.videoWidth, snapshot.videoHeight))
            .setActions(buildRemoteActions(snapshot.isPlaying))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder
                .setAutoEnterEnabled(snapshot.isActive && snapshot.isPlaying && snapshot.autoEnterPip)
                .setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun buildRemoteActions(isPlaying: Boolean): List<RemoteAction> {
        val playPauseIcon = if (isPlaying) R.drawable.ic_pip_pause else R.drawable.ic_pip_play
        val playPauseLabel = if (isPlaying) "Pause" else "Play"

        return listOf(
            createRemoteAction(
                iconRes = R.drawable.ic_pip_replay_10,
                title = "Rewind 10s",
                contentDescription = "Rewind 10 seconds",
                broadcastAction = ACTION_SEEK_BACK,
                requestCode = REQUEST_CODE_SEEK_BACK,
            ),
            createRemoteAction(
                iconRes = playPauseIcon,
                title = playPauseLabel,
                contentDescription = playPauseLabel,
                broadcastAction = ACTION_PLAY_PAUSE,
                requestCode = REQUEST_CODE_PLAY_PAUSE,
            ),
            createRemoteAction(
                iconRes = R.drawable.ic_pip_forward_10,
                title = "Forward 10s",
                contentDescription = "Forward 10 seconds",
                broadcastAction = ACTION_SEEK_FORWARD,
                requestCode = REQUEST_CODE_SEEK_FORWARD,
            ),
        )
    }

    private fun createRemoteAction(
        @DrawableRes iconRes: Int,
        title: String,
        contentDescription: String,
        broadcastAction: String,
        requestCode: Int,
    ): RemoteAction {
        val intent = Intent(broadcastAction).setPackage(activity.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            activity,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(
            Icon.createWithResource(activity, iconRes),
            title,
            contentDescription,
            pendingIntent,
        )
    }

    companion object {
        private const val ACTION_SEEK_BACK = "com.jupiter.app.pip.SEEK_BACK"
        private const val ACTION_PLAY_PAUSE = "com.jupiter.app.pip.PLAY_PAUSE"
        private const val ACTION_SEEK_FORWARD = "com.jupiter.app.pip.SEEK_FORWARD"
        private const val REQUEST_CODE_SEEK_BACK = 101
        private const val REQUEST_CODE_PLAY_PAUSE = 102
        private const val REQUEST_CODE_SEEK_FORWARD = 103
    }
}

val LocalIsInPipMode = staticCompositionLocalOf { false }
val LocalPipHostController = staticCompositionLocalOf<PipHostController?> { null }
