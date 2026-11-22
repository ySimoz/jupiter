package com.jupiter.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jupiter.app.core.di.MpvConfigDir
import com.jupiter.app.data.local.MpvConfStore
import com.jupiter.app.data.local.UserPreferences
import com.jupiter.engine.HwDecMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface SettingsRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setHwDecMode(mode: HwDecMode)
    suspend fun setDoubleTapSeekSec(seconds: Int)
    suspend fun setDefaultPlaybackSpeed(speed: Float)
    suspend fun setRememberResumePosition(enabled: Boolean)
    suspend fun setBackgroundPlayEnabled(enabled: Boolean)
    suspend fun setAutoEnterPip(enabled: Boolean)
    suspend fun setSubtitleFontScale(scale: Float)
    suspend fun setPreferredAudioLang(codes: String)
    suspend fun setPreferredSubtitleLang(codes: String)
    suspend fun setBackgroundArgb(argb: Int)
    suspend fun setAccentArgb(argb: Int)
    suspend fun setGlassBlurEnabled(enabled: Boolean)
    suspend fun setGestureBrightnessEnabled(enabled: Boolean)
    suspend fun setGestureVolumeEnabled(enabled: Boolean)
    suspend fun setGestureSeekEnabled(enabled: Boolean)
    suspend fun setCornerRoundnessPercent(percent: Int)
    suspend fun saveCustomMpvConf(content: String)
    suspend fun resetCustomMpvConf()
    suspend fun clearAppCache(): Boolean
}

internal object SettingsValidation {
    val VALID_DOUBLE_TAP_SECONDS = setOf(5, 10, 15, 30)
    const val DEFAULT_DOUBLE_TAP_SECONDS = 10
    const val MIN_PLAYBACK_SPEED = 0.25f
    const val MAX_PLAYBACK_SPEED = 4.0f
    const val DEFAULT_PLAYBACK_SPEED = 1.0f
    const val MIN_SUBTITLE_SCALE = 0.5f
    const val MAX_SUBTITLE_SCALE = 2.5f
    const val DEFAULT_SUBTITLE_SCALE = 1.0f
    const val MIN_CORNER_ROUNDNESS_PERCENT = 0
    const val MAX_CORNER_ROUNDNESS_PERCENT = 100
    const val DEFAULT_CORNER_ROUNDNESS_PERCENT = 50
    const val DEFAULT_BACKGROUND_ARGB = 0xFF090C12.toInt()
    const val DEFAULT_ACCENT_ARGB = 0xFFE6ECF5.toInt()

    private val LEGACY_ACCENT_HEX = mapOf(
        "ICE" to 0xFF5CE1E6.toInt(),
        "COBALT" to 0xFF7DA8F5.toInt(),
        "SAGE" to 0xFF7BC9A6.toInt(),
        "AMBER" to 0xFFE8B86D.toInt(),
    )

    fun normalizeDoubleTapSeekSec(seconds: Int): Int =
        if (seconds in VALID_DOUBLE_TAP_SECONDS) seconds else DEFAULT_DOUBLE_TAP_SECONDS

    fun clampPlaybackSpeed(speed: Float): Float =
        if (speed.isFinite()) speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED) else DEFAULT_PLAYBACK_SPEED

    fun clampSubtitleScale(scale: Float): Float =
        if (scale.isFinite()) scale.coerceIn(MIN_SUBTITLE_SCALE, MAX_SUBTITLE_SCALE) else DEFAULT_SUBTITLE_SCALE

    fun clampCornerRoundnessPercent(percent: Int): Int =
        percent.coerceIn(MIN_CORNER_ROUNDNESS_PERCENT, MAX_CORNER_ROUNDNESS_PERCENT)

    fun normalizeLangCodes(codes: String): String =
        codes.replace("\r", "").replace("\n", "").trim()

    fun normalizeOpaqueArgb(argb: Int): Int = argb or (0xFF shl 24)

    fun resolveBackgroundArgb(storedBackgroundArgb: Int?): Int =
        storedBackgroundArgb?.let(::normalizeOpaqueArgb) ?: DEFAULT_BACKGROUND_ARGB

    fun resolveAccentArgb(
        storedAccentArgb: Int?,
        legacyAccentStyle: String? = null,
    ): Int {
        if (storedAccentArgb != null) {
            return normalizeOpaqueArgb(storedAccentArgb)
        }
        val legacyHex = legacyAccentStyle?.let { LEGACY_ACCENT_HEX[it] }
        return legacyHex ?: DEFAULT_ACCENT_ARGB
    }

    fun resolveGlassBlurEnabled(
        storedGlassBlurEnabled: Boolean?,
        legacyReducedTransparency: Boolean?,
    ): Boolean = storedGlassBlurEnabled ?: !(legacyReducedTransparency ?: false)
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @MpvConfigDir private val configDir: String,
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            val hwDecName = prefs[Keys.HW_DEC_MODE]
            val hwDec = HwDecMode.entries.firstOrNull { it.name == hwDecName } ?: HwDecMode.AUTO
            UserPreferences(
                hwDecMode = hwDec,
                doubleTapSeekSec = SettingsValidation.normalizeDoubleTapSeekSec(
                    prefs[Keys.DOUBLE_TAP_SEEK_SEC] ?: SettingsValidation.DEFAULT_DOUBLE_TAP_SECONDS,
                ),
                defaultPlaybackSpeed = SettingsValidation.clampPlaybackSpeed(
                    prefs[Keys.DEFAULT_PLAYBACK_SPEED] ?: SettingsValidation.DEFAULT_PLAYBACK_SPEED,
                ),
                rememberResumePosition = prefs[Keys.REMEMBER_RESUME_POSITION] ?: true,
                backgroundPlayEnabled = prefs[Keys.BACKGROUND_PLAY_ENABLED] ?: true,
                autoEnterPip = prefs[Keys.AUTO_ENTER_PIP] ?: true,
                subtitleFontScale = SettingsValidation.clampSubtitleScale(
                    prefs[Keys.SUBTITLE_FONT_SCALE] ?: SettingsValidation.DEFAULT_SUBTITLE_SCALE,
                ),
                preferredAudioLang = prefs[Keys.PREFERRED_AUDIO_LANG] ?: "",
                preferredSubtitleLang = prefs[Keys.PREFERRED_SUBTITLE_LANG] ?: "",
                backgroundArgb = SettingsValidation.resolveBackgroundArgb(
                    prefs[Keys.CUSTOM_BACKGROUND_ARGB],
                ),
                accentArgb = SettingsValidation.resolveAccentArgb(
                    storedAccentArgb = prefs[Keys.CUSTOM_ACCENT_ARGB],
                    legacyAccentStyle = prefs[Keys.ACCENT_STYLE],
                ),
                glassBlurEnabled = SettingsValidation.resolveGlassBlurEnabled(
                    storedGlassBlurEnabled = prefs[Keys.GLASS_BLUR_ENABLED],
                    legacyReducedTransparency = prefs[Keys.REDUCED_TRANSPARENCY],
                ),
                gestureBrightnessEnabled = prefs[Keys.GESTURE_BRIGHTNESS_ENABLED] ?: true,
                gestureVolumeEnabled = prefs[Keys.GESTURE_VOLUME_ENABLED] ?: true,
                gestureSeekEnabled = prefs[Keys.GESTURE_SEEK_ENABLED] ?: true,
                customMpvConf = prefs[Keys.CUSTOM_MPV_CONF] ?: "",
                cornerRoundnessPercent = SettingsValidation.clampCornerRoundnessPercent(
                    prefs[Keys.CORNER_ROUNDNESS_PERCENT] ?: SettingsValidation.DEFAULT_CORNER_ROUNDNESS_PERCENT,
                ),
            )
        }

    override suspend fun setHwDecMode(mode: HwDecMode) {
        dataStore.edit { it[Keys.HW_DEC_MODE] = mode.name }
    }

    override suspend fun setDoubleTapSeekSec(seconds: Int) {
        val normalized = SettingsValidation.normalizeDoubleTapSeekSec(seconds)
        dataStore.edit { it[Keys.DOUBLE_TAP_SEEK_SEC] = normalized }
    }

    override suspend fun setDefaultPlaybackSpeed(speed: Float) {
        val clamped = SettingsValidation.clampPlaybackSpeed(speed)
        dataStore.edit { it[Keys.DEFAULT_PLAYBACK_SPEED] = clamped }
    }

    override suspend fun setRememberResumePosition(enabled: Boolean) {
        dataStore.edit { it[Keys.REMEMBER_RESUME_POSITION] = enabled }
    }

    override suspend fun setBackgroundPlayEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKGROUND_PLAY_ENABLED] = enabled }
    }

    override suspend fun setAutoEnterPip(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_ENTER_PIP] = enabled }
    }

    override suspend fun setSubtitleFontScale(scale: Float) {
        val clamped = SettingsValidation.clampSubtitleScale(scale)
        dataStore.edit { it[Keys.SUBTITLE_FONT_SCALE] = clamped }
    }

    override suspend fun setPreferredAudioLang(codes: String) {
        val normalized = SettingsValidation.normalizeLangCodes(codes)
        dataStore.edit { it[Keys.PREFERRED_AUDIO_LANG] = normalized }
    }

    override suspend fun setPreferredSubtitleLang(codes: String) {
        val normalized = SettingsValidation.normalizeLangCodes(codes)
        dataStore.edit { it[Keys.PREFERRED_SUBTITLE_LANG] = normalized }
    }

    override suspend fun setBackgroundArgb(argb: Int) {
        val opaque = SettingsValidation.normalizeOpaqueArgb(argb)
        dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_BACKGROUND_ARGB] = opaque
            prefs.remove(Keys.BACKGROUND_MODE)
        }
    }

    override suspend fun setAccentArgb(argb: Int) {
        val opaque = SettingsValidation.normalizeOpaqueArgb(argb)
        dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_ACCENT_ARGB] = opaque
            prefs.remove(Keys.ACCENT_STYLE)
            prefs.remove(Keys.DYNAMIC_ACCENT_ENABLED)
        }
    }

    override suspend fun setGlassBlurEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.GLASS_BLUR_ENABLED] = enabled
            prefs.remove(Keys.REDUCED_TRANSPARENCY)
        }
    }

    override suspend fun setGestureBrightnessEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.GESTURE_BRIGHTNESS_ENABLED] = enabled }
    }

    override suspend fun setGestureVolumeEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.GESTURE_VOLUME_ENABLED] = enabled }
    }

    override suspend fun setGestureSeekEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.GESTURE_SEEK_ENABLED] = enabled }
    }

    override suspend fun setCornerRoundnessPercent(percent: Int) {
        val clamped = SettingsValidation.clampCornerRoundnessPercent(percent)
        dataStore.edit { it[Keys.CORNER_ROUNDNESS_PERCENT] = clamped }
    }

    override suspend fun saveCustomMpvConf(content: String) {
        withContext(Dispatchers.IO) {
            MpvConfStore.write(configDir, content)
        }
        dataStore.edit { prefs ->
            if (content.isBlank()) {
                prefs.remove(Keys.CUSTOM_MPV_CONF)
            } else {
                prefs[Keys.CUSTOM_MPV_CONF] = content
            }
        }
    }

    override suspend fun resetCustomMpvConf() {
        withContext(Dispatchers.IO) {
            MpvConfStore.reset(configDir)
        }
        dataStore.edit { it.remove(Keys.CUSTOM_MPV_CONF) }
    }

    override suspend fun clearAppCache(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            clearDirectoryContents(context.cacheDir)
        }.getOrDefault(false)
    }

    private fun clearDirectoryContents(dir: File): Boolean {
        if (!dir.exists()) return true
        val children = dir.listFiles() ?: return true
        var allCleared = true
        for (child in children) {
            if (!child.deleteRecursively()) {
                allCleared = false
            }
        }
        return allCleared
    }

    private object Keys {
        val HW_DEC_MODE = stringPreferencesKey("hwdec_mode")
        val DOUBLE_TAP_SEEK_SEC = intPreferencesKey("double_tap_seek_sec")
        val DEFAULT_PLAYBACK_SPEED = floatPreferencesKey("default_playback_speed")
        val REMEMBER_RESUME_POSITION = booleanPreferencesKey("remember_resume_position")
        val BACKGROUND_PLAY_ENABLED = booleanPreferencesKey("background_play_enabled")
        val AUTO_ENTER_PIP = booleanPreferencesKey("auto_enter_pip")
        val SUBTITLE_FONT_SCALE = floatPreferencesKey("subtitle_font_scale")
        val PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
        val PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")
        val BACKGROUND_MODE = stringPreferencesKey("background_mode")
        val CUSTOM_BACKGROUND_ARGB = intPreferencesKey("custom_background_argb")
        val ACCENT_STYLE = stringPreferencesKey("accent_style")
        val CUSTOM_ACCENT_ARGB = intPreferencesKey("custom_accent_argb")
        val DYNAMIC_ACCENT_ENABLED = booleanPreferencesKey("dynamic_accent_enabled")
        val REDUCED_TRANSPARENCY = booleanPreferencesKey("reduced_transparency")
        val GLASS_BLUR_ENABLED = booleanPreferencesKey("glass_blur_enabled")
        val GESTURE_BRIGHTNESS_ENABLED = booleanPreferencesKey("gesture_brightness_enabled")
        val GESTURE_VOLUME_ENABLED = booleanPreferencesKey("gesture_volume_enabled")
        val GESTURE_SEEK_ENABLED = booleanPreferencesKey("gesture_seek_enabled")
        val CUSTOM_MPV_CONF = stringPreferencesKey("custom_mpv_conf")
        val CORNER_ROUNDNESS_PERCENT = intPreferencesKey("corner_roundness_percent")
    }
}

class InMemorySettingsRepository(
    initial: UserPreferences = UserPreferences(),
    private val configDir: String? = null,
    private val onClearCache: () -> Boolean = { true },
) : SettingsRepository {
    private val _preferences = MutableStateFlow(initial)
    override val preferences: Flow<UserPreferences> = _preferences.asStateFlow()

    val current: UserPreferences
        get() = _preferences.value

    override suspend fun setHwDecMode(mode: HwDecMode) {
        _preferences.update { it.copy(hwDecMode = mode) }
    }

    override suspend fun setDoubleTapSeekSec(seconds: Int) {
        val normalized = SettingsValidation.normalizeDoubleTapSeekSec(seconds)
        _preferences.update { it.copy(doubleTapSeekSec = normalized) }
    }

    override suspend fun setDefaultPlaybackSpeed(speed: Float) {
        val clamped = SettingsValidation.clampPlaybackSpeed(speed)
        _preferences.update { it.copy(defaultPlaybackSpeed = clamped) }
    }

    override suspend fun setRememberResumePosition(enabled: Boolean) {
        _preferences.update { it.copy(rememberResumePosition = enabled) }
    }

    override suspend fun setBackgroundPlayEnabled(enabled: Boolean) {
        _preferences.update { it.copy(backgroundPlayEnabled = enabled) }
    }

    override suspend fun setAutoEnterPip(enabled: Boolean) {
        _preferences.update { it.copy(autoEnterPip = enabled) }
    }

    override suspend fun setSubtitleFontScale(scale: Float) {
        val clamped = SettingsValidation.clampSubtitleScale(scale)
        _preferences.update { it.copy(subtitleFontScale = clamped) }
    }

    override suspend fun setPreferredAudioLang(codes: String) {
        val normalized = SettingsValidation.normalizeLangCodes(codes)
        _preferences.update { it.copy(preferredAudioLang = normalized) }
    }

    override suspend fun setPreferredSubtitleLang(codes: String) {
        val normalized = SettingsValidation.normalizeLangCodes(codes)
        _preferences.update { it.copy(preferredSubtitleLang = normalized) }
    }

    override suspend fun setBackgroundArgb(argb: Int) {
        val opaque = SettingsValidation.normalizeOpaqueArgb(argb)
        _preferences.update { it.copy(backgroundArgb = opaque) }
    }

    override suspend fun setAccentArgb(argb: Int) {
        val opaque = SettingsValidation.normalizeOpaqueArgb(argb)
        _preferences.update { it.copy(accentArgb = opaque) }
    }

    override suspend fun setGlassBlurEnabled(enabled: Boolean) {
        _preferences.update { it.copy(glassBlurEnabled = enabled) }
    }

    override suspend fun setGestureBrightnessEnabled(enabled: Boolean) {
        _preferences.update { it.copy(gestureBrightnessEnabled = enabled) }
    }

    override suspend fun setGestureVolumeEnabled(enabled: Boolean) {
        _preferences.update { it.copy(gestureVolumeEnabled = enabled) }
    }

    override suspend fun setGestureSeekEnabled(enabled: Boolean) {
        _preferences.update { it.copy(gestureSeekEnabled = enabled) }
    }

    override suspend fun setCornerRoundnessPercent(percent: Int) {
        val clamped = SettingsValidation.clampCornerRoundnessPercent(percent)
        _preferences.update { it.copy(cornerRoundnessPercent = clamped) }
    }

    override suspend fun saveCustomMpvConf(content: String) {
        configDir?.let { MpvConfStore.write(it, content) }
        _preferences.update { it.copy(customMpvConf = if (content.isBlank()) "" else content) }
    }

    override suspend fun resetCustomMpvConf() {
        configDir?.let { MpvConfStore.reset(it) }
        _preferences.update { it.copy(customMpvConf = "") }
    }

    override suspend fun clearAppCache(): Boolean = onClearCache()
}
