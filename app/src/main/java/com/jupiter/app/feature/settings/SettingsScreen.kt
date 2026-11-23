package com.jupiter.app.feature.settings

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jupiter.app.core.adaptive.LocalJupiterAdaptiveInfo
import com.jupiter.app.core.adaptive.NavigationLayout
import com.jupiter.app.core.adaptive.computeAdaptiveInfo
import com.jupiter.app.core.designsystem.AccentColorPresets
import com.jupiter.app.core.designsystem.AmbientGlassBackground
import com.jupiter.app.core.designsystem.BackgroundColorPresets
import com.jupiter.app.core.designsystem.ColorWheelDialog
import com.jupiter.app.core.designsystem.ColorWheelMath
import com.jupiter.app.core.designsystem.GlassDialog
import com.jupiter.app.core.designsystem.GlassPill
import com.jupiter.app.core.designsystem.GlassStyle
import com.jupiter.app.core.designsystem.GlassSurface
import com.jupiter.app.core.designsystem.JupiterTheme
import com.jupiter.app.core.designsystem.LocalJupiterPalette
import com.jupiter.app.core.designsystem.LocalJupiterShapes
import com.jupiter.app.core.designsystem.TextColorPresets
import com.jupiter.app.core.designsystem.defaultTextArgbForCanvas
import com.jupiter.app.data.local.UserPreferences
import com.jupiter.engine.HwDecMode
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val HW_DEC_OPTIONS = listOf(
    "Auto" to HwDecMode.AUTO,
    "HW" to HwDecMode.MEDIACODEC_COPY,
    "HW+" to HwDecMode.MEDIACODEC,
    "SW" to HwDecMode.SOFTWARE,
)

private val SPEED_OPTIONS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val DOUBLE_TAP_SEEK_OPTIONS = listOf(5, 10, 15, 30)
private val TabularStyle = TextStyle(fontFeatureSettings = "tnum")
private const val DEFAULT_BACKGROUND_ARGB = 0xFF090C12.toInt()
private const val DEFAULT_ACCENT_ARGB = 0xFFE6ECF5.toInt()

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mpvConfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                readMpvConfFromUri(context.contentResolver, uri)
            }
            result.fold(
                onSuccess = { (text, displayName) ->
                    viewModel.importMpvConf(text, displayName)
                },
                onFailure = { error ->
                    viewModel.onImportMpvConfError(
                        error.message ?: "Unable to read selected file",
                    )
                },
            )
        }
    }

    SettingsContent(
        uiState = uiState,
        contentPadding = contentPadding,
        onSelectHwDecMode = viewModel::setHwDecMode,
        onSelectDefaultSpeed = viewModel::setDefaultPlaybackSpeed,
        onRememberResumeChange = viewModel::setRememberResumePosition,
        onAutoPlayNextChange = viewModel::setAutoPlayNext,
        onBackgroundPlayChange = viewModel::setBackgroundPlayEnabled,
        onAutoEnterPipChange = viewModel::setAutoEnterPip,
        onGestureBrightnessChange = viewModel::setGestureBrightnessEnabled,
        onGestureVolumeChange = viewModel::setGestureVolumeEnabled,
        onGestureSeekChange = viewModel::setGestureSeekEnabled,
        onSelectDoubleTapSeek = viewModel::setDoubleTapSeekSec,
        onSubtitleScaleChange = viewModel::setSubtitleFontScale,
        onPreferredAudioLangChange = viewModel::setPreferredAudioLang,
        onPreferredSubtitleLangChange = viewModel::setPreferredSubtitleLang,
        onSelectBackgroundArgb = viewModel::setBackgroundArgb,
        onSelectAccentArgb = viewModel::setAccentArgb,
        onSelectTextArgb = viewModel::setTextArgb,
        onSelectGlassStyle = viewModel::setGlassStyle,
        onCornerRoundnessChange = viewModel::setCornerRoundnessPercent,
        onShowNavLabelsChange = viewModel::setShowNavLabels,
        onImportMpvConf = {
            mpvConfPickerLauncher.launch(
                arrayOf("text/*", "application/octet-stream", "*/*"),
            )
        },
        onResetMpvConf = viewModel::resetMpvConf,
        onRequestClearHistory = viewModel::requestClearHistory,
        onDismissClearHistory = viewModel::dismissClearHistory,
        onConfirmClearHistory = viewModel::confirmClearHistory,
        onClearCache = viewModel::clearCache,
        onDismissFeedback = viewModel::dismissFeedback,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onSelectHwDecMode: (HwDecMode) -> Unit,
    onSelectDefaultSpeed: (Float) -> Unit,
    onRememberResumeChange: (Boolean) -> Unit,
    onAutoPlayNextChange: (Boolean) -> Unit,
    onBackgroundPlayChange: (Boolean) -> Unit,
    onAutoEnterPipChange: (Boolean) -> Unit,
    onGestureBrightnessChange: (Boolean) -> Unit,
    onGestureVolumeChange: (Boolean) -> Unit,
    onGestureSeekChange: (Boolean) -> Unit,
    onSelectDoubleTapSeek: (Int) -> Unit,
    onSubtitleScaleChange: (Float) -> Unit,
    onPreferredAudioLangChange: (String) -> Unit,
    onPreferredSubtitleLangChange: (String) -> Unit,
    onSelectBackgroundArgb: (Int) -> Unit,
    onSelectAccentArgb: (Int) -> Unit,
    onSelectTextArgb: (Int?) -> Unit,
    onSelectGlassStyle: (GlassStyle) -> Unit,
    onCornerRoundnessChange: (Int) -> Unit,
    onShowNavLabelsChange: (Boolean) -> Unit,
    onImportMpvConf: () -> Unit,
    onResetMpvConf: () -> Unit,
    onRequestClearHistory: () -> Unit,
    onDismissClearHistory: () -> Unit,
    onConfirmClearHistory: () -> Unit,
    onClearCache: () -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalJupiterAdaptiveInfo.current
    val palette = LocalJupiterPalette.current
    val isTwoColumn = adaptiveInfo.widthSizeClass != WindowWidthSizeClass.Compact
    val columns = StaggeredGridCells.Fixed(if (isTwoColumn) 2 else 1)
    val prefs = uiState.preferences

    var showBackgroundWheel by remember { mutableStateOf(false) }
    var showAccentWheel by remember { mutableStateOf(false) }
    var showTextWheel by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.feedbackMessage) {
        if (uiState.feedbackMessage != null) {
            delay(3000)
            onDismissFeedback()
        }
    }

    val screenHazeState = rememberHazeState()

    Box(modifier = modifier.fillMaxSize()) {
        // MainShell passes contentPadding with statusBars, navigationBars, and nav rail/bar clearance.
        LazyVerticalStaggeredGrid(
            columns = columns,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = screenHazeState),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp,
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            item {
                PlaybackSettingsCard(
                    preferences = prefs,
                    onSelectHwDecMode = onSelectHwDecMode,
                    onSelectDefaultSpeed = onSelectDefaultSpeed,
                    onRememberResumeChange = onRememberResumeChange,
                    onAutoPlayNextChange = onAutoPlayNextChange,
                    onBackgroundPlayChange = onBackgroundPlayChange,
                    onAutoEnterPipChange = onAutoEnterPipChange,
                )
            }

            item {
                GestureSettingsCard(
                    preferences = prefs,
                    onGestureBrightnessChange = onGestureBrightnessChange,
                    onGestureVolumeChange = onGestureVolumeChange,
                    onGestureSeekChange = onGestureSeekChange,
                    onSelectDoubleTapSeek = onSelectDoubleTapSeek,
                )
            }

            item {
                SubtitlesAudioSettingsCard(
                    preferences = prefs,
                    onSubtitleScaleChange = onSubtitleScaleChange,
                    onPreferredAudioLangChange = onPreferredAudioLangChange,
                    onPreferredSubtitleLangChange = onPreferredSubtitleLangChange,
                )
            }

            item {
                AppearanceSettingsCard(
                    preferences = prefs,
                    onOpenBackgroundColorPicker = { showBackgroundWheel = true },
                    onOpenAccentColorPicker = { showAccentWheel = true },
                    onOpenTextColorPicker = { showTextWheel = true },
                    onSelectGlassStyle = onSelectGlassStyle,
                    onCornerRoundnessChange = onCornerRoundnessChange,
                    onShowNavLabelsChange = onShowNavLabelsChange,
                )
            }

            item {
                AdvancedSettingsCard(
                    mpvVersion = uiState.mpvVersion,
                    hasCustomMpvConf = uiState.hasCustomMpvConf,
                    customMpvConfLineCount = uiState.customMpvConfLineCount,
                    onImportMpvConf = onImportMpvConf,
                    onResetMpvConf = onResetMpvConf,
                    onRequestClearHistory = onRequestClearHistory,
                    onClearCache = onClearCache,
                )
            }
        }

        val bannerBottomPadding = if (adaptiveInfo.navigationLayout == NavigationLayout.BOTTOM_BAR) {
            96.dp
        } else {
            20.dp
        }
        AnimatedVisibility(
            visible = uiState.feedbackMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = bannerBottomPadding),
        ) {
            val message = uiState.feedbackMessage.orEmpty()
            GlassPill(
                hazeState = screenHazeState,
                elevated = true,
                modifier = Modifier.clickable(role = Role.Button, onClick = onDismissFeedback),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                    )
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Dismiss",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    if (showBackgroundWheel) {
        ColorWheelDialog(
            title = "Background color",
            initialArgb = prefs.backgroundArgb,
            defaultArgb = DEFAULT_BACKGROUND_ARGB,
            presets = BackgroundColorPresets,
            onDismissRequest = { showBackgroundWheel = false },
            onColorSelected = { argb ->
                onSelectBackgroundArgb(argb)
                showBackgroundWheel = false
            },
        )
    }

    if (showAccentWheel) {
        ColorWheelDialog(
            title = "Accent color",
            initialArgb = prefs.accentArgb,
            defaultArgb = DEFAULT_ACCENT_ARGB,
            presets = AccentColorPresets,
            onDismissRequest = { showAccentWheel = false },
            onColorSelected = { argb ->
                onSelectAccentArgb(argb)
                showAccentWheel = false
            },
        )
    }

    if (showTextWheel) {
        val defaultTextArgb = defaultTextArgbForCanvas(prefs.backgroundArgb)
        ColorWheelDialog(
            title = "Text color",
            initialArgb = prefs.textArgb ?: defaultTextArgb,
            defaultArgb = defaultTextArgb,
            presets = TextColorPresets,
            onDismissRequest = { showTextWheel = false },
            onColorSelected = { argb ->
                val resolved = if ((argb and 0xFFFFFF) == (defaultTextArgb and 0xFFFFFF)) null else argb
                onSelectTextArgb(resolved)
                showTextWheel = false
            },
        )
    }

    if (uiState.clearHistoryDialogOpen) {
        ClearHistoryConfirmationDialog(
            onDismiss = onDismissClearHistory,
            onConfirm = onConfirmClearHistory,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackSettingsCard(
    preferences: UserPreferences,
    onSelectHwDecMode: (HwDecMode) -> Unit,
    onSelectDefaultSpeed: (Float) -> Unit,
    onRememberResumeChange: (Boolean) -> Unit,
    onAutoPlayNextChange: (Boolean) -> Unit,
    onBackgroundPlayChange: (Boolean) -> Unit,
    onAutoEnterPipChange: (Boolean) -> Unit,
) {
    SettingsCard(
        title = "Playback",
        icon = Icons.Outlined.PlayCircle,
    ) {
        SettingsChipSection(label = "Hardware decoder") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HW_DEC_OPTIONS.forEach { (label, mode) ->
                    SettingsChoiceChip(
                        label = label,
                        selected = preferences.hwDecMode == mode,
                        onClick = { onSelectHwDecMode(mode) },
                    )
                }
            }
        }

        SettingsChipSection(label = "Default playback speed") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SPEED_OPTIONS.forEach { speed ->
                    SettingsChoiceChip(
                        label = formatSpeed(speed),
                        selected = abs(preferences.defaultPlaybackSpeed - speed) < 0.01f,
                        onClick = { onSelectDefaultSpeed(speed) },
                        tabular = true,
                    )
                }
            }
        }

        SettingsSwitchRow(
            label = "Remember resume position",
            checked = preferences.rememberResumePosition,
            onCheckedChange = onRememberResumeChange,
        )

        SettingsSwitchRow(
            label = "Auto-play next",
            checked = preferences.autoPlayNext,
            onCheckedChange = onAutoPlayNextChange,
        )

        SettingsSwitchRow(
            label = "Background playback",
            checked = preferences.backgroundPlayEnabled,
            onCheckedChange = onBackgroundPlayChange,
        )

        SettingsSwitchRow(
            label = "Auto-enter PiP",
            checked = preferences.autoEnterPip,
            onCheckedChange = onAutoEnterPipChange,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GestureSettingsCard(
    preferences: UserPreferences,
    onGestureBrightnessChange: (Boolean) -> Unit,
    onGestureVolumeChange: (Boolean) -> Unit,
    onGestureSeekChange: (Boolean) -> Unit,
    onSelectDoubleTapSeek: (Int) -> Unit,
) {
    SettingsCard(
        title = "Gestures",
        icon = Icons.Outlined.TouchApp,
    ) {
        SettingsSwitchRow(
            label = "Brightness drag",
            checked = preferences.gestureBrightnessEnabled,
            onCheckedChange = onGestureBrightnessChange,
        )

        SettingsSwitchRow(
            label = "Volume drag",
            checked = preferences.gestureVolumeEnabled,
            onCheckedChange = onGestureVolumeChange,
        )

        SettingsSwitchRow(
            label = "Horizontal seek",
            checked = preferences.gestureSeekEnabled,
            onCheckedChange = onGestureSeekChange,
        )

        SettingsChipSection(label = "Double-tap seek") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DOUBLE_TAP_SEEK_OPTIONS.forEach { seconds ->
                    SettingsChoiceChip(
                        label = "${seconds}s",
                        selected = preferences.doubleTapSeekSec == seconds,
                        onClick = { onSelectDoubleTapSeek(seconds) },
                        tabular = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitlesAudioSettingsCard(
    preferences: UserPreferences,
    onSubtitleScaleChange: (Float) -> Unit,
    onPreferredAudioLangChange: (String) -> Unit,
    onPreferredSubtitleLangChange: (String) -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val clampedScale = preferences.subtitleFontScale.coerceIn(0.5f, 2.5f)

    SettingsCard(
        title = "Subtitles & audio",
        icon = Icons.Outlined.Subtitles,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Default subtitle scale",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary,
                )
                Text(
                    text = formatScale(clampedScale),
                    style = MaterialTheme.typography.labelLarge.merge(TabularStyle),
                    fontWeight = FontWeight.Medium,
                    color = accent,
                )
            }
            Slider(
                value = clampedScale,
                onValueChange = { raw ->
                    val stepped = ((raw * 10f).roundToInt() / 10f).coerceIn(0.5f, 2.5f)
                    onSubtitleScaleChange(stepped)
                },
                valueRange = 0.5f..2.5f,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = palette.borderSubtle,
                ),
            )
        }

        SettingsLanguageField(
            label = "Preferred audio language",
            value = preferences.preferredAudioLang,
            placeholder = "eng,jpn",
            onValueChange = onPreferredAudioLangChange,
        )

        SettingsLanguageField(
            label = "Preferred subtitle language",
            value = preferences.preferredSubtitleLang,
            placeholder = "eng,en",
            onValueChange = onPreferredSubtitleLangChange,
        )
    }
}

@Composable
private fun AppearanceSettingsCard(
    preferences: UserPreferences,
    onOpenBackgroundColorPicker: () -> Unit,
    onOpenAccentColorPicker: () -> Unit,
    onOpenTextColorPicker: () -> Unit,
    onSelectGlassStyle: (GlassStyle) -> Unit,
    onCornerRoundnessChange: (Int) -> Unit,
    onShowNavLabelsChange: (Boolean) -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val clampedPercent = preferences.cornerRoundnessPercent.coerceIn(0, 100)

    SettingsCard(
        title = "Appearance",
        icon = Icons.Outlined.Palette,
    ) {
        ColorSwatchPickerRow(
            label = "Background",
            colorArgb = preferences.backgroundArgb,
            onClick = onOpenBackgroundColorPicker,
        )

        ColorSwatchPickerRow(
            label = "Accent",
            colorArgb = preferences.accentArgb,
            onClick = onOpenAccentColorPicker,
        )

        ColorSwatchPickerRow(
            label = "Text",
            colorArgb = preferences.textArgb ?: defaultTextArgbForCanvas(preferences.backgroundArgb),
            onClick = onOpenTextColorPicker,
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Corner roundness",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary,
                )
                Text(
                    text = "$clampedPercent%",
                    style = MaterialTheme.typography.labelLarge.merge(TabularStyle),
                    fontWeight = FontWeight.Medium,
                    color = accent,
                )
            }
            Slider(
                value = clampedPercent.toFloat(),
                onValueChange = { raw ->
                    onCornerRoundnessChange(raw.roundToInt().coerceIn(0, 100))
                },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = palette.borderSubtle,
                ),
            )
        }

        SettingsChipSection(label = "Glass style") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassStyle.entries.forEach { style ->
                    SettingsChoiceChip(
                        label = style.label,
                        selected = preferences.glassStyle == style,
                        onClick = { onSelectGlassStyle(style) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        SettingsSwitchRow(
            label = "Navigation labels",
            checked = preferences.showNavLabels,
            onCheckedChange = onShowNavLabelsChange,
        )
    }
}

@Composable
private fun ColorSwatchPickerRow(
    label: String,
    colorArgb: Int,
    onClick: () -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val shape = LocalJupiterShapes.current.control
    val accent = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(palette.canvas.copy(alpha = 0.48f), shape)
            .border(width = 1.dp, color = palette.borderSubtle, shape = shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(colorArgb))
                    .border(width = 1.dp, color = palette.borderSubtle, shape = CircleShape),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = ColorWheelMath.formatHex(colorArgb),
                    style = MaterialTheme.typography.labelSmall.merge(
                        TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontFeatureSettings = "tnum",
                        ),
                    ),
                    color = palette.textSecondary,
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.Colorize,
            contentDescription = "Pick $label color",
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AdvancedSettingsCard(
    mpvVersion: String,
    hasCustomMpvConf: Boolean,
    customMpvConfLineCount: Int,
    onImportMpvConf: () -> Unit,
    onResetMpvConf: () -> Unit,
    onRequestClearHistory: () -> Unit,
    onClearCache: () -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val mpvConfStatus = if (hasCustomMpvConf) {
        "Custom (${formatLineCount(customMpvConfLineCount)})"
    } else {
        "Default"
    }
    val statusColor = if (hasCustomMpvConf) {
        MaterialTheme.colorScheme.primary
    } else {
        palette.textPrimary
    }

    SettingsCard(
        title = "Advanced",
        icon = Icons.Outlined.Tune,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "mpv.conf",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
                Text(
                    text = mpvConfStatus,
                    style = MaterialTheme.typography.labelLarge.merge(TabularStyle),
                    fontWeight = FontWeight.Medium,
                    color = statusColor,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsActionRow(
                    label = "Import mpv.conf",
                    icon = Icons.Outlined.UploadFile,
                    onClick = onImportMpvConf,
                    modifier = Modifier.weight(1f),
                )

                if (hasCustomMpvConf) {
                    SettingsCompactActionButton(
                        label = "Reset",
                        icon = Icons.Outlined.RestartAlt,
                        onClick = onResetMpvConf,
                    )
                }
            }
        }

        SettingsActionRow(
            label = "Clear watch history",
            icon = Icons.Outlined.DeleteOutline,
            onClick = onRequestClearHistory,
        )

        SettingsActionRow(
            label = "Clear cache",
            icon = Icons.Outlined.Cached,
            onClick = onClearCache,
        )

        HorizontalDivider(color = palette.borderSubtle)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "libmpv API",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
            )
            Text(
                text = mpvVersion,
                style = MaterialTheme.typography.labelLarge.merge(TabularStyle).copy(
                    fontFamily = FontFamily.Monospace,
                ),
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalJupiterPalette.current
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        hazeState = null,
        shape = LocalJupiterShapes.current.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary,
                )
            }
            HorizontalDivider(color = palette.borderSubtle)
            content()
        }
    }
}

@Composable
private fun SettingsChipSection(
    label: String,
    chips: @Composable () -> Unit,
) {
    val palette = LocalJupiterPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
        )
        chips()
    }
}

@Composable
private fun SettingsChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tabular: Boolean = false,
) {
    val palette = LocalJupiterPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val shape = LocalJupiterShapes.current.control
    val bgColor = if (selected) {
        accent.copy(alpha = 0.16f)
    } else {
        palette.canvas.copy(alpha = 0.48f)
    }
    val borderColor = if (selected) {
        accent.copy(alpha = 0.48f)
    } else {
        palette.borderSubtle
    }
    val textColor = if (selected) accent else palette.textPrimary
    val textStyle = if (tabular) {
        MaterialTheme.typography.labelLarge.merge(TabularStyle)
    } else {
        MaterialTheme.typography.labelLarge
    }

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(bgColor, shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = textStyle,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    statusLabel: String? = null,
) {
    val palette = LocalJupiterPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val labelColor = if (enabled) palette.textPrimary else palette.textSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(LocalJupiterShapes.current.control)
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (statusLabel != null) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = accent,
                uncheckedThumbColor = palette.textSecondary,
                uncheckedTrackColor = palette.canvas.copy(alpha = 0.64f),
                uncheckedBorderColor = palette.borderSubtle,
                disabledUncheckedThumbColor = palette.textSecondary.copy(alpha = 0.48f),
                disabledUncheckedTrackColor = palette.canvas.copy(alpha = 0.36f),
                disabledUncheckedBorderColor = palette.borderSubtle,
            ),
        )
    }
}

@Composable
private fun SettingsLanguageField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val shape = LocalJupiterShapes.current.control

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(shape)
                .background(palette.canvas.copy(alpha = 0.56f), shape)
                .border(width = 1.dp, color = palette.borderSubtle, shape = shape)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary.copy(alpha = 0.64f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                cursorBrush = SolidColor(accent),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = palette.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalJupiterPalette.current
    val shape = LocalJupiterShapes.current.control
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(palette.canvas.copy(alpha = 0.48f), shape)
            .border(width = 1.dp, color = palette.borderSubtle, shape = shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsCompactActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val shape = LocalJupiterShapes.current.control
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(palette.canvas.copy(alpha = 0.48f), shape)
            .border(width = 1.dp, color = palette.borderSubtle, shape = shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = palette.textPrimary,
            maxLines = 1,
        )
    }
}

private fun readMpvConfFromUri(
    contentResolver: ContentResolver,
    uri: Uri,
): Result<Pair<String, String?>> = runCatching {
    val displayName = resolveDisplayName(contentResolver, uri)
    val inputStream = contentResolver.openInputStream(uri)
        ?: throw IOException("Unable to open selected file")
    val bytes = inputStream.use { stream ->
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        val limit = SettingsViewModel.MAX_MPV_CONF_BYTES + 1
        while (out.size() < limit) {
            val maxToRead = minOf(buffer.size, limit - out.size())
            val read = stream.read(buffer, 0, maxToRead)
            if (read == -1) break
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }
    if (bytes.size > SettingsViewModel.MAX_MPV_CONF_BYTES) {
        throw IllegalArgumentException("Config file exceeds 256 KB")
    }
    bytes.toString(Charsets.UTF_8) to displayName
}

private fun resolveDisplayName(
    contentResolver: ContentResolver,
    uri: Uri,
): String? {
    return runCatching {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/')
}

@Composable
private fun ClearHistoryConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val palette = LocalJupiterPalette.current
    GlassDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Clear watch history",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
            )
            Text(
                text = "Saved resume positions will be removed.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DialogSecondaryButton(
                    label = "Cancel",
                    onClick = onDismiss,
                )
                Spacer(modifier = Modifier.width(8.dp))
                DialogPrimaryButton(
                    label = "Clear",
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun DialogSecondaryButton(
    label: String,
    onClick: () -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val shape = LocalJupiterShapes.current.control
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(palette.glassPrimary.copy(alpha = 0.72f), shape)
            .border(width = 1.dp, color = palette.borderSubtle, shape = shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = palette.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun DialogPrimaryButton(
    label: String,
    onClick: () -> Unit,
) {
    val shape = LocalJupiterShapes.current.control
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
        )
    }
}

private fun formatSpeed(speed: Float): String {
    return if (speed == 1.0f || speed == 2.0f) {
        String.format(Locale.US, "%.1fx", speed)
    } else if ((speed * 100f).roundToInt() % 10 == 0) {
        String.format(Locale.US, "%.1fx", speed)
    } else {
        String.format(Locale.US, "%.2fx", speed)
    }
}

private fun formatScale(scale: Float): String = String.format(Locale.US, "%.1fx", scale)

@Preview(name = "Settings Phone", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun SettingsPhonePreview() {
    JupiterTheme {
        AmbientGlassBackground {
            SettingsContent(
                uiState = SettingsUiState(mpvVersion = "2.5"),
                contentPadding = PaddingValues(16.dp),
                onSelectHwDecMode = {},
                onSelectDefaultSpeed = {},
                onRememberResumeChange = {},
                onAutoPlayNextChange = {},
                onBackgroundPlayChange = {},
                onAutoEnterPipChange = {},
                onGestureBrightnessChange = {},
                onGestureVolumeChange = {},
                onGestureSeekChange = {},
                onSelectDoubleTapSeek = {},
                onSubtitleScaleChange = {},
                onPreferredAudioLangChange = {},
                onPreferredSubtitleLangChange = {},
                onSelectBackgroundArgb = {},
                onSelectAccentArgb = {},
                onSelectTextArgb = {},
                onSelectGlassStyle = {},
                onCornerRoundnessChange = {},
                onShowNavLabelsChange = {},
                onImportMpvConf = {},
                onResetMpvConf = {},
                onRequestClearHistory = {},
                onDismissClearHistory = {},
                onConfirmClearHistory = {},
                onClearCache = {},
                onDismissFeedback = {},
            )
        }
    }
}

@Preview(name = "Settings Tablet", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun SettingsTabletPreview() {
    JupiterTheme {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalJupiterAdaptiveInfo provides computeAdaptiveInfo(
                widthSizeClass = WindowWidthSizeClass.Expanded,
                heightSizeClass = WindowHeightSizeClass.Expanded,
            ),
        ) {
            AmbientGlassBackground {
                SettingsContent(
                    uiState = SettingsUiState(mpvVersion = "2.5"),
                    contentPadding = PaddingValues(24.dp),
                    onSelectHwDecMode = {},
                    onSelectDefaultSpeed = {},
                    onRememberResumeChange = {},
                    onAutoPlayNextChange = {},
                    onBackgroundPlayChange = {},
                    onAutoEnterPipChange = {},
                    onGestureBrightnessChange = {},
                    onGestureVolumeChange = {},
                    onGestureSeekChange = {},
                    onSelectDoubleTapSeek = {},
                    onSubtitleScaleChange = {},
                    onPreferredAudioLangChange = {},
                    onPreferredSubtitleLangChange = {},
                    onSelectBackgroundArgb = {},
                    onSelectAccentArgb = {},
                    onSelectTextArgb = {},
                    onSelectGlassStyle = {},
                    onCornerRoundnessChange = {},
                    onShowNavLabelsChange = {},
                    onImportMpvConf = {},
                    onResetMpvConf = {},
                    onRequestClearHistory = {},
                    onDismissClearHistory = {},
                    onConfirmClearHistory = {},
                    onClearCache = {},
                    onDismissFeedback = {},
                )
            }
        }
    }
}
