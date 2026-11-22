package com.jupiter.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.app.core.designsystem.GlassStyle
import com.jupiter.app.data.local.UserPreferences
import com.jupiter.app.data.repository.MediaRepository
import com.jupiter.app.data.repository.SettingsRepository
import com.jupiter.engine.HwDecMode
import com.jupiter.engine.MpvNative
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val mpvVersion: String = "",
    val clearHistoryDialogOpen: Boolean = false,
    val feedbackMessage: String? = null,
) {
    val hasCustomMpvConf: Boolean
        get() = preferences.customMpvConf.isNotBlank()

    val customMpvConfLineCount: Int
        get() = countMpvConfLines(preferences.customMpvConf)
}

@HiltViewModel
class SettingsViewModel internal constructor(
    private val settingsRepository: SettingsRepository,
    private val mediaRepository: MediaRepository,
    private val mpvVersionProvider: () -> String,
) : ViewModel() {

    @Inject
    constructor(
        settingsRepository: SettingsRepository,
        mediaRepository: MediaRepository,
    ) : this(
        settingsRepository = settingsRepository,
        mediaRepository = mediaRepository,
        mpvVersionProvider = { MpvNative.nativeGetMpvVersion() },
    )

    private val _uiState = MutableStateFlow(
        SettingsUiState(mpvVersion = resolveMpvVersion()),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.preferences.collect { preferences ->
                _uiState.update { state ->
                    state.copy(preferences = preferences)
                }
            }
        }
    }

    fun setHwDecMode(mode: HwDecMode) {
        viewModelScope.launch {
            settingsRepository.setHwDecMode(mode)
        }
    }

    fun setDoubleTapSeekSec(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setDoubleTapSeekSec(seconds)
        }
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            settingsRepository.setDefaultPlaybackSpeed(speed)
        }
    }

    fun setRememberResumePosition(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRememberResumePosition(enabled)
        }
    }

    fun setBackgroundPlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBackgroundPlayEnabled(enabled)
        }
    }

    fun setAutoEnterPip(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoEnterPip(enabled)
        }
    }

    fun setSubtitleFontScale(scale: Float) {
        viewModelScope.launch {
            settingsRepository.setSubtitleFontScale(scale)
        }
    }

    fun setPreferredAudioLang(lang: String) {
        viewModelScope.launch {
            settingsRepository.setPreferredAudioLang(lang)
        }
    }

    fun setPreferredSubtitleLang(lang: String) {
        viewModelScope.launch {
            settingsRepository.setPreferredSubtitleLang(lang)
        }
    }

    fun setBackgroundArgb(argb: Int) {
        viewModelScope.launch {
            settingsRepository.setBackgroundArgb(argb)
        }
    }

    fun setAccentArgb(argb: Int) {
        viewModelScope.launch {
            settingsRepository.setAccentArgb(argb)
        }
    }

    fun setGlassStyle(style: GlassStyle) {
        viewModelScope.launch {
            settingsRepository.setGlassStyle(style)
        }
    }

    fun setGestureBrightnessEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setGestureBrightnessEnabled(enabled)
        }
    }

    fun setGestureVolumeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setGestureVolumeEnabled(enabled)
        }
    }

    fun setGestureSeekEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setGestureSeekEnabled(enabled)
        }
    }

    fun setCornerRoundnessPercent(percent: Int) {
        viewModelScope.launch {
            settingsRepository.setCornerRoundnessPercent(percent)
        }
    }

    fun importMpvConf(content: String, fileName: String? = null) {
        if (content.isBlank()) {
            _uiState.update { it.copy(feedbackMessage = "Selected file is empty") }
            return
        }
        if ('\u0000' in content) {
            _uiState.update { it.copy(feedbackMessage = "Invalid text config file") }
            return
        }
        if (content.toByteArray(Charsets.UTF_8).size > MAX_MPV_CONF_BYTES) {
            _uiState.update { it.copy(feedbackMessage = "Config file exceeds 256 KB") }
            return
        }

        val lineCount = countMpvConfLines(content)
        val displayName = fileName?.trim()?.takeIf { it.isNotEmpty() } ?: "mpv.conf"
        viewModelScope.launch {
            settingsRepository.saveCustomMpvConf(content)
            _uiState.update {
                it.copy(feedbackMessage = "Imported $displayName (${formatLineCount(lineCount)})")
            }
        }
    }

    fun onImportMpvConfError(message: String) {
        _uiState.update {
            it.copy(feedbackMessage = message.ifBlank { "Unable to import mpv.conf" })
        }
    }

    fun resetMpvConf() {
        viewModelScope.launch {
            settingsRepository.resetCustomMpvConf()
            _uiState.update {
                it.copy(feedbackMessage = "Reset mpv.conf to defaults")
            }
        }
    }

    fun requestClearHistory() {
        _uiState.update { it.copy(clearHistoryDialogOpen = true) }
    }

    fun dismissClearHistory() {
        _uiState.update { it.copy(clearHistoryDialogOpen = false) }
    }

    fun confirmClearHistory() {
        viewModelScope.launch {
            mediaRepository.clearHistory()
            _uiState.update {
                it.copy(
                    clearHistoryDialogOpen = false,
                    feedbackMessage = "Watch history cleared",
                )
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            val cleared = settingsRepository.clearAppCache()
            _uiState.update {
                it.copy(
                    feedbackMessage = if (cleared) "Cache cleared" else "Unable to clear cache",
                )
            }
        }
    }

    fun dismissFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    private fun resolveMpvVersion(): String {
        return runCatching { mpvVersionProvider() }
            .getOrElse { "" }
            .ifBlank { "Unavailable" }
    }

    companion object {
        const val MAX_MPV_CONF_BYTES: Int = 256 * 1024
    }
}

internal fun countMpvConfLines(content: String): Int =
    content.lineSequence().count { it.isNotBlank() }

internal fun formatLineCount(count: Int): String =
    "$count ${if (count == 1) "line" else "lines"}"
