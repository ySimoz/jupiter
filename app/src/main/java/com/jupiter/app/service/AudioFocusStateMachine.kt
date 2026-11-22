package com.jupiter.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

class AudioFocusStateMachine {

    enum class FocusChange {
        GAIN,
        LOSS,
        LOSS_TRANSIENT,
        LOSS_TRANSIENT_CAN_DUCK,
    }

    data class FocusDecision(
        val shouldPause: Boolean = false,
        val shouldResume: Boolean = false,
        val targetVolumePercent: Int? = null,
    )

    private var resumeOnGain: Boolean = false
    private var isDucked: Boolean = false

    fun onFocusChange(change: FocusChange, isCurrentlyPlaying: Boolean): FocusDecision {
        return when (change) {
            FocusChange.LOSS -> {
                resumeOnGain = false
                val restoreVolume = if (isDucked) FULL_VOLUME_PERCENT else null
                isDucked = false
                FocusDecision(
                    shouldPause = isCurrentlyPlaying,
                    shouldResume = false,
                    targetVolumePercent = restoreVolume,
                )
            }

            FocusChange.LOSS_TRANSIENT -> {
                if (isCurrentlyPlaying) {
                    resumeOnGain = true
                }
                FocusDecision(
                    shouldPause = isCurrentlyPlaying,
                    shouldResume = false,
                    targetVolumePercent = null,
                )
            }

            FocusChange.LOSS_TRANSIENT_CAN_DUCK -> {
                if (!isCurrentlyPlaying) {
                    FocusDecision()
                } else {
                    isDucked = true
                    FocusDecision(
                        shouldPause = false,
                        shouldResume = false,
                        targetVolumePercent = DUCK_VOLUME_PERCENT,
                    )
                }
            }

            FocusChange.GAIN -> {
                val shouldResume = resumeOnGain && !isCurrentlyPlaying
                resumeOnGain = false
                val restoreVolume = if (isDucked) FULL_VOLUME_PERCENT else null
                isDucked = false
                FocusDecision(
                    shouldPause = false,
                    shouldResume = shouldResume,
                    targetVolumePercent = restoreVolume,
                )
            }
        }
    }

    fun onBecomingNoisy(isCurrentlyPlaying: Boolean): FocusDecision {
        resumeOnGain = false
        val restoreVolume = if (isDucked) FULL_VOLUME_PERCENT else null
        isDucked = false
        return FocusDecision(
            shouldPause = isCurrentlyPlaying,
            shouldResume = false,
            targetVolumePercent = restoreVolume,
        )
    }

    fun reset() {
        resumeOnGain = false
        isDucked = false
    }

    companion object {
        const val DUCK_VOLUME_PERCENT = 30
        const val FULL_VOLUME_PERCENT = 100
    }
}

interface AudioFocusController {
    fun requestFocus(
        onPause: () -> Unit,
        onResume: () -> Unit,
        onVolumeChange: (Int) -> Unit,
    ): Boolean

    fun abandonFocus()
}

class AndroidAudioFocusController(
    private val context: Context,
) : AudioFocusController {

    private val appContext: Context = context.applicationContext
    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateMachine = AudioFocusStateMachine()

    private var activeFocusRequest: AudioFocusRequest? = null
    private var noisyReceiverRegistered: Boolean = false
    private var assumedPlaying: Boolean = false

    private var onPauseCallback: (() -> Unit)? = null
    private var onResumeCallback: (() -> Unit)? = null
    private var onVolumeChangeCallback: ((Int) -> Unit)? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val mapped = when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> AudioFocusStateMachine.FocusChange.GAIN
            AudioManager.AUDIOFOCUS_LOSS -> AudioFocusStateMachine.FocusChange.LOSS
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
                AudioFocusStateMachine.FocusChange.LOSS_TRANSIENT
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                AudioFocusStateMachine.FocusChange.LOSS_TRANSIENT_CAN_DUCK
            else -> return@OnAudioFocusChangeListener
        }
        val decision = stateMachine.onFocusChange(mapped, isCurrentlyPlaying = assumedPlaying)
        applyDecision(decision)
        if (mapped == AudioFocusStateMachine.FocusChange.LOSS) {
            unregisterNoisyReceiver()
            activeFocusRequest = null
            assumedPlaying = false
            clearCallbacks()
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            val decision = stateMachine.onBecomingNoisy(isCurrentlyPlaying = assumedPlaying)
            applyDecision(decision)
        }
    }

    override fun requestFocus(
        onPause: () -> Unit,
        onResume: () -> Unit,
        onVolumeChange: (Int) -> Unit,
    ): Boolean {
        val manager = audioManager ?: return false
        abandonFocus()

        onPauseCallback = onPause
        onResumeCallback = onResume
        onVolumeChangeCallback = onVolumeChange

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
            .build()

        val result = manager.requestAudioFocus(focusRequest)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            activeFocusRequest = focusRequest
            assumedPlaying = true
            registerNoisyReceiver()
            return true
        }

        clearCallbacks()
        return false
    }

    override fun abandonFocus() {
        unregisterNoisyReceiver()
        activeFocusRequest?.let { request ->
            audioManager?.abandonAudioFocusRequest(request)
        }
        activeFocusRequest = null
        assumedPlaying = false
        stateMachine.reset()
        clearCallbacks()
    }

    private fun applyDecision(decision: AudioFocusStateMachine.FocusDecision) {
        decision.targetVolumePercent?.let { volume ->
            onVolumeChangeCallback?.invoke(volume)
        }
        if (decision.shouldPause) {
            assumedPlaying = false
            onPauseCallback?.invoke()
        } else if (decision.shouldResume) {
            assumedPlaying = true
            onResumeCallback?.invoke()
        }
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        ContextCompat.registerReceiver(
            appContext,
            noisyReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        runCatching { appContext.unregisterReceiver(noisyReceiver) }
        noisyReceiverRegistered = false
    }

    private fun clearCallbacks() {
        onPauseCallback = null
        onResumeCallback = null
        onVolumeChangeCallback = null
    }
}
