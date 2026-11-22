package com.jupiter.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.jupiter.app.MainActivity
import com.jupiter.app.R
import com.jupiter.app.core.di.PlaybackController
import com.jupiter.engine.PlaybackState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

interface PlaybackServiceLauncher {
    fun startOrUpdate()
    fun stop()
}

class AndroidPlaybackServiceLauncher(
    private val context: Context,
) : PlaybackServiceLauncher {

    private val appContext: Context = context.applicationContext

    override fun startOrUpdate() {
        val intent = Intent(appContext, MediaPlaybackService::class.java)
        try {
            ContextCompat.startForegroundService(appContext, intent)
        } catch (_: IllegalStateException) {
            // Android 12+ throws ForegroundServiceStartNotAllowedException (extends IllegalStateException)
            // when the app is already backgrounded without an exemption.
        }
    }

    override fun stop() {
        appContext.stopService(Intent(appContext, MediaPlaybackService::class.java))
    }
}

@AndroidEntryPoint
class MediaPlaybackService : Service() {

    @Inject
    lateinit var playbackController: PlaybackController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var mediaSession: MediaSessionCompat
    private var notificationManager: NotificationManager? = null

    private var lastMetadataTitle: String? = null
    private var lastMetadataDurationMs: Long = -1L
    private var lastPublishedState: Int = PlaybackStateCompat.STATE_NONE
    private var lastPublishedPositionMs: Long = -1L
    private var lastPublishedSpeed: Float = -1f
    private var lastNotificationSignature: NotificationSignature? = null
    private var foregroundStarted: Boolean = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        ensureNotificationChannel()
        initMediaSession()
        observePlaybackState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val currentState = playbackController.state.value
        ensureForeground(currentState)
        publishSessionMetadataAndState(currentState)

        when (intent?.action) {
            ACTION_REWIND -> playbackController.seekBy(-SEEK_STEP_SECONDS)
            ACTION_PLAY_PAUSE -> playbackController.togglePlayPause()
            ACTION_FAST_FORWARD -> playbackController.seekBy(SEEK_STEP_SECONDS)
            ACTION_STOP -> {
                playbackController.pause()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        foregroundStarted = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, SESSION_TAG).apply {
            setSessionActivity(createContentPendingIntent())
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        playbackController.play()
                    }

                    override fun onPause() {
                        playbackController.pause()
                    }

                    override fun onSeekTo(pos: Long) {
                        playbackController.seekTo(pos.coerceAtLeast(0L))
                    }

                    override fun onRewind() {
                        playbackController.seekBy(-SEEK_STEP_SECONDS)
                    }

                    override fun onFastForward() {
                        playbackController.seekBy(SEEK_STEP_SECONDS)
                    }

                    override fun onStop() {
                        playbackController.pause()
                        ServiceCompat.stopForeground(
                            this@MediaPlaybackService,
                            ServiceCompat.STOP_FOREGROUND_REMOVE,
                        )
                        foregroundStarted = false
                        stopSelf()
                    }
                },
            )
            isActive = true
        }
    }

    private fun observePlaybackState() {
        serviceScope.launch {
            playbackController.state.collect { state ->
                publishSessionMetadataAndState(state)
                updateNotificationIfNeeded(state)
            }
        }
    }

    private fun publishSessionMetadataAndState(state: PlaybackState) {
        val title = resolveTitle(state)
        val durationMs = state.durationMs.coerceAtLeast(0L)
        if (title != lastMetadataTitle || durationMs != lastMetadataDurationMs) {
            lastMetadataTitle = title
            lastMetadataDurationMs = durationMs
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
                .build()
            mediaSession.setMetadata(metadata)
        }

        val sessionState = when {
            state.isBuffering -> PlaybackStateCompat.STATE_BUFFERING
            state.isPaused -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_PLAYING
        }
        val effectiveSpeed = if (state.isPaused || state.isBuffering) 0f else state.speed
        val positionDeltaMs = abs(state.positionMs - lastPublishedPositionMs)
        val shouldPublishState =
            sessionState != lastPublishedState ||
                effectiveSpeed != lastPublishedSpeed ||
                positionDeltaMs >= POSITION_SYNC_THRESHOLD_MS

        if (shouldPublishState) {
            lastPublishedState = sessionState
            lastPublishedPositionMs = state.positionMs
            lastPublishedSpeed = effectiveSpeed

            val playbackState = PlaybackStateCompat.Builder()
                .setActions(SESSION_ACTIONS)
                .setState(sessionState, state.positionMs, effectiveSpeed)
                .build()
            mediaSession.setPlaybackState(playbackState)
        }
    }

    private fun ensureForeground(state: PlaybackState) {
        val signature = signatureFor(state)
        if (!foregroundStarted) {
            lastNotificationSignature = signature
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(state, signature.title),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            foregroundStarted = true
        }
    }

    private fun updateNotificationIfNeeded(state: PlaybackState) {
        val signature = signatureFor(state)
        if (signature == lastNotificationSignature) return
        lastNotificationSignature = signature

        val notification = buildNotification(state, signature.title)
        if (!foregroundStarted) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            foregroundStarted = true
        } else {
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun signatureFor(state: PlaybackState): NotificationSignature {
        return NotificationSignature(
            title = resolveTitle(state),
            isPaused = state.isPaused,
            isBuffering = state.isBuffering,
            durationMs = state.durationMs.coerceAtLeast(0L),
        )
    }

    private fun resolveTitle(state: PlaybackState): String {
        return state.mediaTitle?.takeIf { it.isNotBlank() } ?: getString(R.string.app_name)
    }

    private fun buildNotification(state: PlaybackState, title: String): Notification {
        val contentText = when {
            state.isBuffering -> "Buffering"
            state.isPaused -> "Paused"
            else -> "Playing"
        }

        val rewindAction = NotificationCompat.Action.Builder(
            R.drawable.ic_pip_replay_10,
            "Rewind 10s",
            createServiceActionPendingIntent(ACTION_REWIND, REQUEST_REWIND),
        ).build()

        val playPauseIcon = if (state.isPaused) R.drawable.ic_pip_play else R.drawable.ic_pip_pause
        val playPauseLabel = if (state.isPaused) "Play" else "Pause"
        val playPauseAction = NotificationCompat.Action.Builder(
            playPauseIcon,
            playPauseLabel,
            createServiceActionPendingIntent(ACTION_PLAY_PAUSE, REQUEST_PLAY_PAUSE),
        ).build()

        val forwardAction = NotificationCompat.Action.Builder(
            R.drawable.ic_pip_forward_10,
            "Forward 10s",
            createServiceActionPendingIntent(ACTION_FAST_FORWARD, REQUEST_FAST_FORWARD),
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_jupiter)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(createContentPendingIntent())
            .setDeleteIntent(createServiceActionPendingIntent(ACTION_STOP, REQUEST_STOP))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(!state.isPaused)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .addAction(rewindAction)
            .addAction(playPauseAction)
            .addAction(forwardAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun createContentPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_CONTENT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createServiceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MediaPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureNotificationChannel() {
        val manager = notificationManager ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Media playback controls"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private data class NotificationSignature(
        val title: String,
        val isPaused: Boolean,
        val isBuffering: Boolean,
        val durationMs: Long,
    )

    companion object {
        const val CHANNEL_ID = "jupiter_playback"
        const val CHANNEL_NAME = "Media playback"
        const val NOTIFICATION_ID = 7001

        private const val SESSION_TAG = "JupiterMediaSession"
        private const val SEEK_STEP_SECONDS = 10.0
        private const val POSITION_SYNC_THRESHOLD_MS = 1_000L

        private const val ACTION_REWIND = "com.jupiter.app.action.MEDIA_REWIND"
        private const val ACTION_PLAY_PAUSE = "com.jupiter.app.action.MEDIA_PLAY_PAUSE"
        private const val ACTION_FAST_FORWARD = "com.jupiter.app.action.MEDIA_FAST_FORWARD"
        private const val ACTION_STOP = "com.jupiter.app.action.MEDIA_STOP"

        private const val REQUEST_CONTENT = 100
        private const val REQUEST_REWIND = 101
        private const val REQUEST_PLAY_PAUSE = 102
        private const val REQUEST_FAST_FORWARD = 103
        private const val REQUEST_STOP = 104

        private const val SESSION_ACTIONS =
            PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_REWIND or
                PlaybackStateCompat.ACTION_FAST_FORWARD or
                PlaybackStateCompat.ACTION_STOP
    }
}
