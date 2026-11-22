package com.jupiter.app.service

import com.jupiter.app.service.AudioFocusStateMachine.FocusChange
import com.jupiter.app.service.AudioFocusStateMachine.FocusDecision
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AudioFocusStateMachineTest {

    private lateinit var stateMachine: AudioFocusStateMachine

    @Before
    fun setUp() {
        stateMachine = AudioFocusStateMachine()
    }

    @Test
    fun `permanent focus loss pauses when playing and does not auto resume on later gain`() {
        val lossDecision = stateMachine.onFocusChange(FocusChange.LOSS, isCurrentlyPlaying = true)
        assertEquals(FocusDecision(shouldPause = true, shouldResume = false), lossDecision)

        val gainDecision = stateMachine.onFocusChange(FocusChange.GAIN, isCurrentlyPlaying = false)
        assertEquals(FocusDecision(shouldPause = false, shouldResume = false), gainDecision)
    }

    @Test
    fun `transient focus loss pauses playing media and auto resumes on gain`() {
        val transientDecision =
            stateMachine.onFocusChange(FocusChange.LOSS_TRANSIENT, isCurrentlyPlaying = true)
        assertEquals(FocusDecision(shouldPause = true, shouldResume = false), transientDecision)

        val gainDecision = stateMachine.onFocusChange(FocusChange.GAIN, isCurrentlyPlaying = false)
        assertEquals(FocusDecision(shouldPause = false, shouldResume = true), gainDecision)

        val secondGain = stateMachine.onFocusChange(FocusChange.GAIN, isCurrentlyPlaying = true)
        assertEquals(FocusDecision(), secondGain)
    }

    @Test
    fun `transient focus loss when already paused stays paused on gain`() {
        val transientDecision =
            stateMachine.onFocusChange(FocusChange.LOSS_TRANSIENT, isCurrentlyPlaying = false)
        assertEquals(FocusDecision(shouldPause = false, shouldResume = false), transientDecision)

        val gainDecision = stateMachine.onFocusChange(FocusChange.GAIN, isCurrentlyPlaying = false)
        assertEquals(FocusDecision(shouldPause = false, shouldResume = false), gainDecision)
    }

    @Test
    fun `can duck lowers volume to 30 percent and restores to 100 percent on gain`() {
        val duckDecision = stateMachine.onFocusChange(
            FocusChange.LOSS_TRANSIENT_CAN_DUCK,
            isCurrentlyPlaying = true,
        )
        assertEquals(
            FocusDecision(shouldPause = false, shouldResume = false, targetVolumePercent = 30),
            duckDecision,
        )

        val gainDecision = stateMachine.onFocusChange(FocusChange.GAIN, isCurrentlyPlaying = true)
        assertEquals(
            FocusDecision(shouldPause = false, shouldResume = false, targetVolumePercent = 100),
            gainDecision,
        )
    }

    @Test
    fun `becoming noisy pauses playback and clears pending transient auto resume`() {
        stateMachine.onFocusChange(FocusChange.LOSS_TRANSIENT, isCurrentlyPlaying = true)

        val noisyWhilePaused = stateMachine.onBecomingNoisy(isCurrentlyPlaying = false)
        assertEquals(FocusDecision(shouldPause = false, shouldResume = false), noisyWhilePaused)

        val gainAfterNoisy = stateMachine.onFocusChange(FocusChange.GAIN, isCurrentlyPlaying = false)
        assertEquals(FocusDecision(shouldPause = false, shouldResume = false), gainAfterNoisy)

        val noisyWhilePlaying = stateMachine.onBecomingNoisy(isCurrentlyPlaying = true)
        assertEquals(FocusDecision(shouldPause = true, shouldResume = false), noisyWhilePlaying)
    }

    @Test
    fun `reset clears pending resume and duck state`() {
        stateMachine.onFocusChange(FocusChange.LOSS_TRANSIENT_CAN_DUCK, isCurrentlyPlaying = true)
        stateMachine.onFocusChange(FocusChange.LOSS_TRANSIENT, isCurrentlyPlaying = true)
        stateMachine.reset()

        val gainDecision = stateMachine.onFocusChange(FocusChange.GAIN, isCurrentlyPlaying = false)
        assertEquals(FocusDecision(), gainDecision)
    }
}
