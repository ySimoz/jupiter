package com.jupiter.app

import android.content.ContentResolver
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jupiter.app.core.adaptive.rememberJupiterAdaptiveInfo
import com.jupiter.app.core.designsystem.JupiterTheme
import com.jupiter.app.core.shell.ExternalMediaLaunch
import com.jupiter.app.core.shell.MainShell
import com.jupiter.app.data.local.UserPreferences
import com.jupiter.app.data.repository.SettingsRepository
import com.jupiter.app.feature.player.AndroidPipHostController
import com.jupiter.app.feature.player.LocalIsInPipMode
import com.jupiter.app.feature.player.LocalPipHostController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var pendingExternalLaunch by mutableStateOf<ExternalMediaLaunch?>(null)
    private var isInPipMode by mutableStateOf(false)
    private lateinit var pipHostController: AndroidPipHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pipHostController = AndroidPipHostController(this).apply {
            register()
        }
        isInPipMode = isInPictureInPictureMode
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            pendingExternalLaunch = extractExternalMediaLaunch(intent)
        }
        setContent {
            val prefs by settingsRepository.preferences.collectAsState(initial = UserPreferences())
            JupiterTheme(
                backgroundArgb = prefs.backgroundArgb,
                accentArgb = prefs.accentArgb,
                glassBlurEnabled = prefs.glassBlurEnabled,
                cornerRoundnessPercent = prefs.cornerRoundnessPercent,
            ) {
                val adaptiveInfo = rememberJupiterAdaptiveInfo(this)
                CompositionLocalProvider(
                    LocalIsInPipMode provides isInPipMode,
                    LocalPipHostController provides pipHostController,
                ) {
                    MainShell(
                        adaptiveInfo = adaptiveInfo,
                        pendingExternalLaunch = pendingExternalLaunch,
                        onExternalLaunchConsumed = { pendingExternalLaunch = null },
                    )
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pipHostController.onUserLeaveHint()
    }

    override fun onDestroy() {
        if (::pipHostController.isInitialized) {
            pipHostController.unregister()
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractExternalMediaLaunch(intent)?.let { launch ->
            pendingExternalLaunch = launch
        }
    }

    private fun extractExternalMediaLaunch(intent: Intent?): ExternalMediaLaunch? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        val uriString = uri.toString()
        if (uriString.isBlank()) return null

        val title = resolveDisplayName(uri)
            ?: intent.getStringExtra(Intent.EXTRA_TITLE)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: ""

        return ExternalMediaLaunch(
            uri = uriString,
            title = title,
            isNetworkStream = isNetworkScheme(uriString),
        )
    }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        return runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun isNetworkScheme(uri: String): Boolean {
        return uri.startsWith("http://", ignoreCase = true) ||
            uri.startsWith("https://", ignoreCase = true) ||
            uri.startsWith("rtsp://", ignoreCase = true) ||
            uri.startsWith("rtmp://", ignoreCase = true)
    }
}
