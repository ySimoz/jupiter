package com.jupiter.app.core.di

import android.content.Context
import android.view.Surface
import com.jupiter.app.service.AndroidAudioFocusController
import com.jupiter.app.service.AndroidPlaybackServiceLauncher
import com.jupiter.app.service.AudioFocusController
import com.jupiter.app.service.PlaybackServiceLauncher
import com.jupiter.engine.HwDecMode
import com.jupiter.engine.MpvEngine
import com.jupiter.engine.MpvEvent
import com.jupiter.engine.PlaybackState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Base64
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MpvConfigDir

/**
 * Abstraction over [MpvEngine] so ViewModels can be unit-tested on the JVM without loading JNI.
 */
interface PlaybackController {
    val state: StateFlow<PlaybackState>
    val events: SharedFlow<MpvEvent>

    fun initialize(configDir: String, extraOptions: Map<String, String> = emptyMap())
    fun attachSurface(surface: Surface): Int
    fun setSurfaceSize(width: Int, height: Int): Int = 0
    fun detachSurface(): Int
    fun loadFile(uriOrFd: String, options: List<String> = emptyList()): Int
    fun play(): Int
    fun pause(): Int
    fun togglePlayPause(): Int
    fun seekTo(ms: Long): Int
    fun seekBy(deltaSec: Double): Int
    fun setSpeed(speed: Float): Int
    fun setVolume(percent: Int): Int = 0
    fun setVideoZoom(zoom: Double): Int
    fun setAspectOverride(aspect: String): Int
    fun setOption(name: String, value: String): Int
    fun setProperty(name: String, value: String): Int
    fun setNetworkHeaders(userAgent: String?, referer: String?, headers: Map<String, String>)
    fun selectAudioTrack(id: Int?): Int
    fun selectSubtitleTrack(id: Int?): Int
    fun selectSecondarySubtitleTrack(id: Int?): Int
    fun addExternalSubtitle(fdPath: String): Int
    fun setSubtitleDelay(sec: Double): Int
    fun setAudioDelay(sec: Double): Int
    fun setSubtitleScale(scale: Double): Int
    fun setHwDec(mode: HwDecMode): Int
    fun selectChapter(index: Int): Int
    fun setAbLoopA(seconds: Double?): Int
    fun setAbLoopB(seconds: Double?): Int
    fun clearAbLoop(): Int
    fun setEqualizerProperty(name: String, value: Int): Int
}

internal class MpvPlaybackController(
    private val engine: MpvEngine,
) : PlaybackController {
    override val state: StateFlow<PlaybackState> = engine.state
    override val events: SharedFlow<MpvEvent> = engine.events

    override fun initialize(configDir: String, extraOptions: Map<String, String>) {
        val resolvedOptions = LinkedHashMap<String, String>(2 + extraOptions.size)
        SubtitleFontBootstrapper.ensureSubfont(configDir)
        val caFile = AndroidCaBundle.ensureCaBundleFile(configDir)
        if (caFile != null) {
            resolvedOptions["tls-verify"] = "yes"
            resolvedOptions["tls-ca-file"] = caFile.absolutePath
        }
        resolvedOptions.putAll(extraOptions)
        engine.initialize(configDir, resolvedOptions)
    }

    override fun attachSurface(surface: Surface): Int = engine.attachSurface(surface)

    override fun setSurfaceSize(width: Int, height: Int): Int = engine.setSurfaceSize(width, height)

    override fun detachSurface(): Int = engine.detachSurface()

    override fun loadFile(uriOrFd: String, options: List<String>): Int =
        engine.loadFile(uriOrFd, options)

    override fun play(): Int = engine.play()

    override fun pause(): Int = engine.pause()

    override fun togglePlayPause(): Int = engine.togglePlayPause()

    override fun seekTo(ms: Long): Int = engine.seekTo(ms)

    override fun seekBy(deltaSec: Double): Int = engine.seekBy(deltaSec)

    override fun setSpeed(speed: Float): Int = engine.setSpeed(speed)

    override fun setVolume(percent: Int): Int = engine.setVolume(percent)

    override fun setVideoZoom(zoom: Double): Int = engine.setVideoZoom(zoom)

    override fun setAspectOverride(aspect: String): Int = engine.setAspectOverride(aspect)

    override fun setOption(name: String, value: String): Int = engine.setOption(name, value)

    override fun setProperty(name: String, value: String): Int =
        engine.command("set", name, value)

    override fun setNetworkHeaders(
        userAgent: String?,
        referer: String?,
        headers: Map<String, String>,
    ) = engine.setNetworkHeaders(userAgent, referer, headers)

    override fun selectAudioTrack(id: Int?): Int = engine.selectAudioTrack(id)

    override fun selectSubtitleTrack(id: Int?): Int = engine.selectSubtitleTrack(id)

    override fun selectSecondarySubtitleTrack(id: Int?): Int =
        engine.selectSecondarySubtitleTrack(id)

    override fun addExternalSubtitle(fdPath: String): Int = engine.addExternalSubtitle(fdPath)

    override fun setSubtitleDelay(sec: Double): Int = engine.setSubtitleDelay(sec)

    override fun setAudioDelay(sec: Double): Int = engine.setAudioDelay(sec)

    override fun setSubtitleScale(scale: Double): Int = engine.setSubtitleScale(scale)

    override fun setHwDec(mode: HwDecMode): Int = engine.setHwDec(mode)

    override fun selectChapter(index: Int): Int = engine.selectChapter(index)

    override fun setAbLoopA(seconds: Double?): Int = engine.setAbLoopA(seconds)

    override fun setAbLoopB(seconds: Double?): Int = engine.setAbLoopB(seconds)

    override fun clearAbLoop(): Int = engine.clearAbLoop()

    override fun setEqualizerProperty(name: String, value: Int): Int =
        engine.setEqualizerProperty(name, value)
}

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {
    @Provides
    @Singleton
    fun provideMpvEngine(): MpvEngine = MpvEngine()

    @Provides
    @Singleton
    fun providePlaybackController(engine: MpvEngine): PlaybackController =
        MpvPlaybackController(engine)

    @Provides
    @MpvConfigDir
    fun provideMpvConfigDir(@ApplicationContext context: Context): String =
        File(context.filesDir, "mpv").apply { mkdirs() }.absolutePath

    @Provides
    @Singleton
    fun providePlaybackServiceLauncher(
        @ApplicationContext context: Context,
    ): PlaybackServiceLauncher = AndroidPlaybackServiceLauncher(context)

    @Provides
    @Singleton
    fun provideAudioFocusController(
        @ApplicationContext context: Context,
    ): AudioFocusController = AndroidAudioFocusController(context)
}

internal object AndroidCaBundle {
    private const val CA_BUNDLE_FILE_NAME = "cacert.pem"
    private val PEM_LINE_SEPARATOR = byteArrayOf('\n'.code.toByte())

    fun ensureCaBundleFile(
        configDir: String,
        certificatesProvider: () -> List<X509Certificate> = ::loadAndroidSystemCertificates,
    ): File? = runCatching {
        if (configDir.isBlank()) return@runCatching null
        val dir = File(configDir).apply { mkdirs() }
        val caFile = File(dir, CA_BUNDLE_FILE_NAME)
        if (caFile.isFile && caFile.length() > 0L) {
            return@runCatching caFile
        }
        val certs = certificatesProvider()
        if (certs.isEmpty()) return@runCatching null
        val pem = formatPemBundle(certs)
        if (pem.isBlank()) return@runCatching null

        val tmpFile = File(dir, "$CA_BUNDLE_FILE_NAME.tmp")
        tmpFile.writeText(pem, Charsets.UTF_8)
        if (!tmpFile.renameTo(caFile)) {
            caFile.writeText(pem, Charsets.UTF_8)
            tmpFile.delete()
        }
        caFile.takeIf { it.isFile && it.length() > 0L }
    }.getOrNull()

    fun formatPemBundle(certificates: Iterable<X509Certificate>): String {
        val encoder = Base64.getMimeEncoder(64, PEM_LINE_SEPARATOR)
        val sb = StringBuilder()
        for (cert in certificates) {
            val der = cert.encoded ?: continue
            if (der.isEmpty()) continue
            sb.append("-----BEGIN CERTIFICATE-----\n")
            sb.append(encoder.encodeToString(der))
            sb.append("\n-----END CERTIFICATE-----\n")
        }
        return sb.toString()
    }

    private fun loadAndroidSystemCertificates(): List<X509Certificate> {
        val keyStore = KeyStore.getInstance("AndroidCAStore")
        keyStore.load(null)
        val certs = mutableListOf<X509Certificate>()
        val aliases = keyStore.aliases()
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            val cert = keyStore.getCertificate(alias) as? X509Certificate
            if (cert != null) {
                certs += cert
            }
        }
        return certs
    }
}

object SubtitleFontBootstrapper {
    private const val SUBFONT_FILE_NAME = "subfont.ttf"

    val DEFAULT_CANDIDATES: List<String> = listOf(
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/NotoSans-Regular.ttf",
        "/system/fonts/DroidSans.ttf",
    )

    fun ensureSubfont(
        configDir: String,
        candidatePaths: List<String> = DEFAULT_CANDIDATES,
    ): File? = runCatching {
        if (configDir.isBlank()) return@runCatching null
        val dir = File(configDir).apply { mkdirs() }
        val target = File(dir, SUBFONT_FILE_NAME)
        if (target.isFile && target.length() > 0L) {
            return@runCatching target
        }

        val source = candidatePaths
            .asSequence()
            .map(::File)
            .firstOrNull { it.isFile && it.canRead() && it.length() > 0L }
            ?: return@runCatching null

        val tmpFile = File(dir, "$SUBFONT_FILE_NAME.tmp")
        try {
            source.inputStream().use { input ->
                tmpFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!tmpFile.renameTo(target)) {
                tmpFile.copyTo(target, overwrite = true)
                tmpFile.delete()
            }
        } catch (t: Throwable) {
            tmpFile.delete()
            throw t
        }
        target.takeIf { it.isFile && it.length() > 0L }
    }.getOrNull()
}
