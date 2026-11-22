package com.jupiter.app.data.local

import com.jupiter.app.core.designsystem.GlassStyle
import com.jupiter.engine.HwDecMode
import java.io.File

data class UserPreferences(
    val hwDecMode: HwDecMode = HwDecMode.AUTO,
    val doubleTapSeekSec: Int = 10,
    val defaultPlaybackSpeed: Float = 1.0f,
    val rememberResumePosition: Boolean = true,
    val autoPlayNext: Boolean = true,
    val backgroundPlayEnabled: Boolean = true,
    val autoEnterPip: Boolean = true,
    val subtitleFontScale: Float = 1.0f,
    val preferredAudioLang: String = "",
    val preferredSubtitleLang: String = "",
    val backgroundArgb: Int = 0xFF090C12.toInt(),
    val accentArgb: Int = 0xFFE6ECF5.toInt(),
    val glassStyle: GlassStyle = GlassStyle.FROSTED,
    val gestureBrightnessEnabled: Boolean = true,
    val gestureVolumeEnabled: Boolean = true,
    val gestureSeekEnabled: Boolean = true,
    val customMpvConf: String = "",
    val cornerRoundnessPercent: Int = 50,
)

object MpvConfStore {
    private const val CONF_FILE_NAME = "mpv.conf"
    private const val TMP_FILE_NAME = "mpv.conf.tmp"

    fun write(configDir: String, content: String) {
        if (configDir.isBlank()) return
        val dir = File(configDir).apply { mkdirs() }
        val target = File(dir, CONF_FILE_NAME)
        val tmp = File(dir, TMP_FILE_NAME)
        if (content.isBlank()) {
            tmp.delete()
            if (target.exists()) {
                target.delete()
            }
            return
        }
        try {
            tmp.writeText(content, Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
    }

    fun read(configDir: String): String {
        if (configDir.isBlank()) return ""
        val target = File(configDir, CONF_FILE_NAME)
        if (!target.isFile) return ""
        return runCatching { target.readText(Charsets.UTF_8) }.getOrDefault("")
    }

    fun reset(configDir: String) {
        if (configDir.isBlank()) return
        val dir = File(configDir)
        File(dir, TMP_FILE_NAME).delete()
        File(dir, CONF_FILE_NAME).delete()
    }
}
