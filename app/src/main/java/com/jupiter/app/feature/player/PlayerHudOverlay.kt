package com.jupiter.app.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.ScreenLockLandscape
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupiter.app.core.adaptive.LocalJupiterAdaptiveInfo
import com.jupiter.app.core.designsystem.GlassAdaptiveSheet
import com.jupiter.app.core.designsystem.GlassDialog
import com.jupiter.app.core.designsystem.GlassSurface
import com.jupiter.app.core.designsystem.JupiterColors
import com.jupiter.app.core.designsystem.JupiterSpecularBorderBrush
import com.jupiter.app.core.designsystem.LocalJupiterShapes
import com.jupiter.app.data.local.VideoBookmarkEntity
import com.jupiter.engine.Chapter
import com.jupiter.engine.HwDecMode
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val HudGlassBackdrop = Color(0xFF12161E).copy(alpha = 0.76f)
private val HudGlassElevatedBackdrop = Color(0xFF12161E).copy(alpha = 0.82f)

@Composable
fun PlayerHudOverlay(
    uiState: PlayerUiState,
    positionMsFlow: StateFlow<Long>,
    onNavigateBack: () -> Unit,
    onPipClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (Int) -> Unit,
    onScrubStart: () -> Unit,
    onScrubChange: (Long) -> Unit,
    onScrubFinish: (Long) -> Unit,
    onToggleRemainingTime: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetAspectMode: (AspectMode) -> Unit,
    onCycleOrientationLock: () -> Unit,
    onToggleLock: () -> Unit,
    onSelectAudioTrack: (Int?) -> Unit,
    onAdjustAudioDelay: (Double) -> Unit,
    onResetAudioDelay: () -> Unit,
    onSelectSubtitleTrack: (Int?) -> Unit,
    onSelectSecondarySubtitleTrack: (Int?) -> Unit,
    onLoadExternalSubtitle: () -> Unit,
    onAdjustSubtitleDelay: (Double) -> Unit,
    onResetSubtitleDelay: () -> Unit,
    onSetSubtitleScale: (Double) -> Unit,
    onResetSubtitleScale: () -> Unit,
    onSetHwDecMode: (HwDecMode) -> Unit,
    onJumpToChapter: (Chapter) -> Unit,
    onToggleAbLoop: () -> Unit,
    onClearAbLoop: () -> Unit,
    onSetEqualizer: (brightness: Int, contrast: Int, saturation: Int, gamma: Int) -> Unit,
    onResetEqualizer: () -> Unit,
    onPlayNextNow: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onCancelUpNext: () -> Unit,
    onAutoPlayNextChange: (Boolean) -> Unit,
    onAddBookmark: (String) -> Unit,
    onRenameBookmark: (Long, String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onJumpToBookmark: (VideoBookmarkEntity) -> Unit,
    onCaptureFrame: () -> Unit,
    onDismissStatusMessage: () -> Unit,
    onSheetVisibilityChanged: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalJupiterAdaptiveInfo.current
    val isCompact = adaptiveInfo.widthSizeClass == WindowWidthSizeClass.Compact
    var activeSheet by remember { mutableStateOf<PlayerSheetType?>(null) }
    val currentOnSheetVisibilityChanged by rememberUpdatedState(onSheetVisibilityChanged)
    val isSheetOpen = activeSheet != null

    LaunchedEffect(isSheetOpen) {
        currentOnSheetVisibilityChanged(isSheetOpen)
    }
    DisposableEffect(Unit) {
        onDispose {
            currentOnSheetVisibilityChanged(false)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = uiState.isLocked && uiState.showUnlockHint,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 28.dp),
        ) {
            HudGlassChip(
                icon = Icons.Outlined.LockOpen,
                label = "Unlock",
                onClick = onToggleLock,
                elevated = true,
            )
        }

        AnimatedVisibility(
            visible = !uiState.isLocked && uiState.isHudVisible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(260)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.72f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(184.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.78f),
                                ),
                            ),
                        ),
                )

                TopGlassBar(
                    title = uiState.title,
                    orientationLock = uiState.orientationLock,
                    onNavigateBack = onNavigateBack,
                    onPipClick = onPipClick,
                    onCycleOrientationLock = onCycleOrientationLock,
                    onToggleLock = onToggleLock,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .systemBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )

                CenterPlaybackControls(
                    isPaused = uiState.isPaused,
                    isBuffering = uiState.isBuffering,
                    bufferingPercent = uiState.bufferingPercent,
                    hasQueue = uiState.hasQueue,
                    hasPreviousInQueue = uiState.hasPreviousInQueue,
                    hasNextInQueue = uiState.hasNextInQueue,
                    onPlayPrevious = onPlayPrevious,
                    onPlayNext = onPlayNextNow,
                    onSeekBy = onSeekBy,
                    onTogglePlayPause = onTogglePlayPause,
                    modifier = Modifier.align(Alignment.Center),
                )

                BottomGlassDock(
                    uiState = uiState,
                    positionMsFlow = positionMsFlow,
                    isCompact = isCompact,
                    onScrubStart = onScrubStart,
                    onScrubChange = onScrubChange,
                    onScrubFinish = onScrubFinish,
                    onToggleRemainingTime = onToggleRemainingTime,
                    onOpenSheet = { activeSheet = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .systemBarsPadding()
                        .padding(
                            horizontal = if (isCompact) 12.dp else 24.dp,
                            vertical = 12.dp,
                        ),
                )
            }
        }

        AnimatedVisibility(
            visible = !uiState.isLocked && uiState.abLoop.isActive,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(top = 68.dp, end = 16.dp),
        ) {
            HudGlassChip(
                icon = Icons.Outlined.Repeat,
                label = formatAbPillLabel(uiState.abLoop),
                onClick = onClearAbLoop,
                accent = true,
            )
        }

        AnimatedVisibility(
            visible = !uiState.isLocked && uiState.connectionAlert != null && uiState.errorMessage == null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(
                    top = if (uiState.abLoop.isActive) 112.dp else 72.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
        ) {
            uiState.connectionAlert?.let { alert ->
                val alertIcon = when (alert) {
                    ConnectionAlert.OFFLINE -> Icons.Outlined.WifiOff
                    ConnectionAlert.RECONNECTING -> Icons.Outlined.Refresh
                    ConnectionAlert.SLOW_CONNECTION -> Icons.Outlined.NetworkCheck
                }
                HudGlassChip(
                    icon = alertIcon,
                    label = alert.message,
                    onClick = if (alert == ConnectionAlert.RECONNECTING) ({}) else onRetry,
                    elevated = true,
                )
            }
        }

        AnimatedVisibility(
            visible = !uiState.isLocked && uiState.statusMessage != null && uiState.connectionAlert == null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(
                    top = if (uiState.abLoop.isActive) 112.dp else 72.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
        ) {
            uiState.statusMessage?.let { message ->
                HudGlassChip(
                    icon = Icons.Outlined.Check,
                    label = message,
                    onClick = onDismissStatusMessage,
                    elevated = true,
                )
            }
        }

        AnimatedVisibility(
            visible = !uiState.isLocked && uiState.upNextItem != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .systemBarsPadding()
                .padding(
                    start = if (isCompact) 12.dp else 24.dp,
                    end = if (isCompact) 12.dp else 24.dp,
                    bottom = if (uiState.isHudVisible) 204.dp else 20.dp,
                ),
        ) {
            uiState.upNextItem?.let { nextItem ->
                UpNextOverlayCard(
                    nextItem = nextItem,
                    countdownSec = uiState.upNextCountdownSec,
                    onPlayNow = onPlayNextNow,
                    onCancel = onCancelUpNext,
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.isBuffering && !uiState.isHudVisible && !uiState.isLocked,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(HudGlassElevatedBackdrop, CircleShape)
                    .border(1.dp, JupiterSpecularBorderBrush, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BufferingOrbContent(bufferingPercent = uiState.bufferingPercent)
            }
        }

        GlassAdaptiveSheet(
            visible = activeSheet != null,
            onDismissRequest = { activeSheet = null },
            useSideSheet = adaptiveInfo.useSideSheet,
            hazeState = null,
            title = activeSheet?.title,
        ) {
            when (activeSheet) {
                PlayerSheetType.TRACKS -> {
                    TracksSheetContent(
                        uiState = uiState,
                        onSelectSubtitleTrack = onSelectSubtitleTrack,
                        onSelectSecondarySubtitleTrack = onSelectSecondarySubtitleTrack,
                        onLoadExternalSubtitle = onLoadExternalSubtitle,
                        onAdjustSubtitleDelay = onAdjustSubtitleDelay,
                        onResetSubtitleDelay = onResetSubtitleDelay,
                        onSubtitleScaleChange = onSetSubtitleScale,
                        onResetSubtitleScale = onResetSubtitleScale,
                        onSelectAudioTrack = onSelectAudioTrack,
                        onAdjustAudioDelay = onAdjustAudioDelay,
                        onResetAudioDelay = onResetAudioDelay,
                    )
                }

                PlayerSheetType.SPEED -> {
                    SpeedSheetContent(
                        uiState = uiState,
                        onSelectSpeed = onSetSpeed,
                    )
                }

                PlayerSheetType.BOOKMARKS -> {
                    BookmarksSheetContent(
                        uiState = uiState,
                        positionMsFlow = positionMsFlow,
                        onAddBookmark = onAddBookmark,
                        onRenameBookmark = onRenameBookmark,
                        onDeleteBookmark = onDeleteBookmark,
                        onJumpToBookmark = { bookmark ->
                            onJumpToBookmark(bookmark)
                            activeSheet = null
                        },
                        onCaptureFrame = onCaptureFrame,
                        onSelectChapter = { chapter ->
                            onJumpToChapter(chapter)
                            activeSheet = null
                        },
                    )
                }

                PlayerSheetType.QUEUE -> {
                    QueueSheetContent(
                        uiState = uiState,
                        onPlayPrevious = onPlayPrevious,
                        onPlayNext = onPlayNextNow,
                        onSelectQueueIndex = { index ->
                            onPlayQueueIndex(index)
                            activeSheet = null
                        },
                    )
                }

                PlayerSheetType.MORE -> {
                    MoreControlsSheetContent(
                        uiState = uiState,
                        onSelectAspectMode = onSetAspectMode,
                        onSelectHwDecMode = onSetHwDecMode,
                        onToggleAbLoop = onToggleAbLoop,
                        onClearAbLoop = onClearAbLoop,
                        onAutoPlayNextChange = onAutoPlayNextChange,
                        onOpenQueue = { activeSheet = PlayerSheetType.QUEUE },
                        onEqualizerChange = onSetEqualizer,
                        onResetEqualizer = onResetEqualizer,
                    )
                }

                null -> Unit
            }
        }

        if (uiState.errorMessage != null) {
            GlassDialog(
                onDismissRequest = onDismissError,
                hazeState = null,
            ) {
                Text(
                    text = "Playback error",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = JupiterColors.TextPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JupiterColors.TextSecondary,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    HudGlassChip(
                        icon = null,
                        label = "Dismiss",
                        onClick = onDismissError,
                    )
                    HudGlassChip(
                        icon = null,
                        label = "Retry",
                        onClick = onRetry,
                        accent = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopGlassBar(
    title: String,
    orientationLock: OrientationLock,
    onNavigateBack: () -> Unit,
    onPipClick: () -> Unit,
    onCycleOrientationLock: () -> Unit,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val orientationIcon = when (orientationLock) {
        OrientationLock.AUTO -> Icons.Outlined.ScreenRotation
        OrientationLock.LANDSCAPE -> Icons.Outlined.ScreenLockLandscape
        OrientationLock.PORTRAIT -> Icons.Outlined.ScreenLockPortrait
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HudGlassIconButton(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            onClick = onNavigateBack,
        )

        val titleShape = LocalJupiterShapes.current.control
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(titleShape)
                .background(HudGlassBackdrop, titleShape)
                .border(1.dp, JupiterSpecularBorderBrush, titleShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title.ifBlank { "Video" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = JupiterColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(),
            )
        }

        HudGlassIconButton(
            icon = Icons.Outlined.PictureInPictureAlt,
            contentDescription = "Picture-in-picture",
            onClick = onPipClick,
        )

        HudGlassIconButton(
            icon = orientationIcon,
            contentDescription = "Orientation ${orientationLock.label}",
            onClick = onCycleOrientationLock,
            selected = orientationLock != OrientationLock.AUTO,
        )

        HudGlassIconButton(
            icon = Icons.Outlined.Lock,
            contentDescription = "Lock screen",
            onClick = onToggleLock,
        )
    }
}

@Composable
private fun CenterPlaybackControls(
    isPaused: Boolean,
    isBuffering: Boolean,
    bufferingPercent: Int,
    hasQueue: Boolean,
    hasPreviousInQueue: Boolean,
    hasNextInQueue: Boolean,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onSeekBy: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (hasQueue) 16.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasQueue) {
            HudGlassIconButton(
                icon = Icons.Outlined.SkipPrevious,
                contentDescription = "Previous video",
                onClick = onPlayPrevious,
                enabled = hasPreviousInQueue,
                size = 40.dp,
                iconSize = 20.dp,
            )
        }

        HudGlassIconButton(
            icon = Icons.Outlined.Replay10,
            contentDescription = "Rewind 10 seconds",
            onClick = { onSeekBy(-10) },
            size = 48.dp,
            iconSize = 22.dp,
        )

        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(HudGlassElevatedBackdrop, CircleShape)
                .border(1.dp, JupiterSpecularBorderBrush, CircleShape)
                .semantics {
                    contentDescription = if (isPaused) "Play" else "Pause"
                }
                .clickable(
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    onClick = onTogglePlayPause,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isBuffering) {
                BufferingOrbContent(bufferingPercent = bufferingPercent)
            } else {
                Icon(
                    imageVector = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    contentDescription = null,
                    tint = JupiterColors.TextPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        HudGlassIconButton(
            icon = Icons.Outlined.Forward10,
            contentDescription = "Forward 10 seconds",
            onClick = { onSeekBy(10) },
            size = 48.dp,
            iconSize = 22.dp,
        )

        if (hasQueue) {
            HudGlassIconButton(
                icon = Icons.Outlined.SkipNext,
                contentDescription = "Next video",
                onClick = onPlayNext,
                enabled = hasNextInQueue,
                size = 40.dp,
                iconSize = 20.dp,
            )
        }
    }
}

@Composable
private fun BufferingOrbContent(bufferingPercent: Int) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(44.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        if (bufferingPercent in 1..99) {
            Text(
                text = "$bufferingPercent%",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
                color = JupiterColors.TextPrimary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BottomGlassDock(
    uiState: PlayerUiState,
    positionMsFlow: StateFlow<Long>,
    isCompact: Boolean,
    onScrubStart: () -> Unit,
    onScrubChange: (Long) -> Unit,
    onScrubFinish: (Long) -> Unit,
    onToggleRemainingTime: () -> Unit,
    onOpenSheet: (PlayerSheetType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dockShape = LocalJupiterShapes.current.surface
    val dockInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clip(dockShape)
            .background(HudGlassBackdrop, dockShape)
            .border(1.dp, JupiterSpecularBorderBrush, dockShape)
            .clickable(
                interactionSource = dockInteraction,
                indication = null,
                onClick = {},
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.hasQueue) {
                val queueLabel = buildString {
                    append(uiState.queueSourceTitle.ifBlank { "Queue" })
                    append(" · ")
                    append((uiState.queueIndex + 1).coerceAtLeast(1))
                    append('/')
                    append(uiState.queueItems.size)
                }
                val chipShape = LocalJupiterShapes.current.control
                Row(
                    modifier = Modifier
                        .clip(chipShape)
                        .background(JupiterColors.GlassPrimary.copy(alpha = 0.64f), chipShape)
                        .border(1.dp, JupiterColors.BorderSubtle, chipShape)
                        .clickable(role = Role.Button) { onOpenSheet(PlayerSheetType.QUEUE) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = queueLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Medium,
                        color = JupiterColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            PlayerScrubberRow(
                positionMsFlow = positionMsFlow,
                durationMs = uiState.durationMs,
                isScrubbing = uiState.isScrubbing,
                scrubPreviewMs = uiState.scrubPreviewMs,
                showRemainingTime = uiState.showRemainingTime,
                chapters = uiState.chapters,
                bookmarks = uiState.bookmarks,
                onScrubStart = onScrubStart,
                onScrubChange = onScrubChange,
                onScrubFinish = onScrubFinish,
                onToggleRemainingTime = onToggleRemainingTime,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tracksActive = uiState.selectedSubtitleTrackId != null ||
                    uiState.secondarySubtitleTrackId != null ||
                    abs(uiState.audioDelaySec) > 0.001 ||
                    abs(uiState.subDelaySec) > 0.001
                DockActionButton(
                    icon = Icons.Outlined.Subtitles,
                    label = "Tracks",
                    isCompact = isCompact,
                    accent = tracksActive,
                    onClick = { onOpenSheet(PlayerSheetType.TRACKS) },
                    modifier = Modifier.weight(1f),
                )

                DockActionButton(
                    icon = Icons.Outlined.Speed,
                    label = formatSpeedLabel(uiState.speed),
                    isCompact = isCompact,
                    accent = abs(uiState.speed - 1.0f) > 0.01f,
                    tabular = true,
                    onClick = { onOpenSheet(PlayerSheetType.SPEED) },
                    modifier = Modifier.weight(1f),
                )

                DockActionButton(
                    icon = if (uiState.bookmarks.isNotEmpty()) {
                        Icons.Outlined.Bookmarks
                    } else {
                        Icons.Outlined.BookmarkBorder
                    },
                    label = if (isCompact) "Marks" else "Bookmarks",
                    accessibilityLabel = "Bookmarks",
                    isCompact = isCompact,
                    accent = uiState.bookmarks.isNotEmpty(),
                    onClick = { onOpenSheet(PlayerSheetType.BOOKMARKS) },
                    modifier = Modifier.weight(1f),
                )

                val moreActive = !uiState.equalizer.isDefault ||
                    uiState.abLoop.isActive ||
                    uiState.aspectMode != AspectMode.FIT
                DockActionButton(
                    icon = Icons.Outlined.Tune,
                    label = "More",
                    isCompact = isCompact,
                    accent = moreActive,
                    onClick = { onOpenSheet(PlayerSheetType.MORE) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DockActionButton(
    icon: ImageVector,
    label: String,
    isCompact: Boolean,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tabular: Boolean = false,
    accessibilityLabel: String? = null,
) {
    val shape = LocalJupiterShapes.current.control
    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = if (accent) accentColor.copy(alpha = 0.16f) else Color.Transparent
    val borderColor = if (accent) accentColor.copy(alpha = 0.44f) else JupiterColors.BorderSubtle
    val contentColor = if (accent) accentColor else JupiterColors.TextPrimary
    val semanticModifier = if (accessibilityLabel != null) {
        Modifier.semantics { contentDescription = accessibilityLabel }
    } else {
        Modifier
    }

    if (isCompact) {
        Column(
            modifier = modifier
                .height(48.dp)
                .clip(shape)
                .background(bgColor, shape)
                .border(1.dp, borderColor, shape)
                .then(semanticModifier)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = if (tabular) {
                    MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
                } else {
                    MaterialTheme.typography.labelSmall
                },
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Row(
            modifier = modifier
                .height(40.dp)
                .clip(shape)
                .background(bgColor, shape)
                .border(1.dp, borderColor, shape)
                .then(semanticModifier)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = if (tabular) {
                    MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
                } else {
                    MaterialTheme.typography.labelLarge
                },
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UpNextOverlayCard(
    nextItem: QueueMediaItem,
    countdownSec: Int,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
) {
    val controlShape = LocalJupiterShapes.current.control
    val accentColor = MaterialTheme.colorScheme.primary
    GlassSurface(
        modifier = Modifier.widthIn(max = 320.dp),
        hazeState = null,
        shape = LocalJupiterShapes.current.surface,
        elevated = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Up next in ${countdownSec}s",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
                color = JupiterColors.TextSecondary,
            )
            Text(
                text = nextItem.title.ifBlank { "Next video" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = JupiterColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(controlShape)
                        .background(JupiterColors.GlassPrimary.copy(alpha = 0.64f), controlShape)
                        .border(1.dp, JupiterColors.BorderSubtle, controlShape)
                        .clickable(role = Role.Button, onClick = onCancel)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = JupiterColors.TextPrimary,
                        maxLines = 1,
                    )
                }
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(controlShape)
                        .background(accentColor, controlShape)
                        .clickable(role = Role.Button, onClick = onPlayNow)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Play now",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerScrubberRow(
    positionMsFlow: StateFlow<Long>,
    durationMs: Long,
    isScrubbing: Boolean,
    scrubPreviewMs: Long?,
    showRemainingTime: Boolean,
    chapters: List<Chapter>,
    bookmarks: List<VideoBookmarkEntity>,
    onScrubStart: () -> Unit,
    onScrubChange: (Long) -> Unit,
    onScrubFinish: (Long) -> Unit,
    onToggleRemainingTime: () -> Unit,
) {
    // Collect 60Hz position updates strictly inside this isolated composable.
    val livePositionMs by positionMsFlow.collectAsState()
    val displayPosMs = if (isScrubbing && scrubPreviewMs != null) scrubPreviewMs else livePositionMs
    val safeDurationMs = durationMs.coerceAtLeast(0L)
    val fraction = if (safeDurationMs > 0L) {
        (displayPosMs.toFloat() / safeDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val currentDuration by rememberUpdatedState(safeDurationMs)
    val currentOnStart by rememberUpdatedState(onScrubStart)
    val currentOnChange by rememberUpdatedState(onScrubChange)
    val currentOnFinish by rememberUpdatedState(onScrubFinish)

    var dragTargetMs by remember { mutableStateOf(0L) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val accentColor = MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val width = size.width.toFloat()
                        if (width > 0f && currentDuration > 0L) {
                            val tapFraction = (offset.x / width).coerceIn(0f, 1f)
                            val target = (tapFraction * currentDuration).toLong()
                            currentOnStart()
                            currentOnFinish(target)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val width = size.width.toFloat()
                            currentOnStart()
                            if (width > 0f && currentDuration > 0L) {
                                val startFrac = (offset.x / width).coerceIn(0f, 1f)
                                dragTargetMs = (startFrac * currentDuration).toLong()
                                currentOnChange(dragTargetMs)
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val width = size.width.toFloat()
                            if (width > 0f && currentDuration > 0L) {
                                val frac = (change.position.x / width).coerceIn(0f, 1f)
                                dragTargetMs = (frac * currentDuration).toLong()
                                currentOnChange(dragTargetMs)
                            }
                        },
                        onDragEnd = {
                            currentOnFinish(dragTargetMs)
                        },
                        onDragCancel = {
                            currentOnFinish(dragTargetMs)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackHeightPx = (if (isScrubbing) 6.dp else 4.dp).toPx()
                val centerY = size.height / 2f
                val trackTop = centerY - trackHeightPx / 2f
                val corner = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.20f),
                    topLeft = Offset(0f, trackTop),
                    size = Size(size.width, trackHeightPx),
                    cornerRadius = corner,
                )

                val playedWidth = size.width * fraction
                if (playedWidth > 0f) {
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(0f, trackTop),
                        size = Size(playedWidth, trackHeightPx),
                        cornerRadius = corner,
                    )
                }

                if (safeDurationMs > 0L && chapters.isNotEmpty()) {
                    val tickWidth = 2.dp.toPx()
                    val tickHeight = trackHeightPx + 4.dp.toPx()
                    val tickTop = centerY - tickHeight / 2f
                    chapters.forEach { chapter ->
                        val chapterFraction = ((chapter.timeSec * 1000.0) / safeDurationMs.toDouble())
                            .toFloat()
                            .coerceIn(0f, 1f)
                        val tickX = (size.width * chapterFraction) - (tickWidth / 2f)
                        drawRect(
                            color = Color.White.copy(alpha = 0.72f),
                            topLeft = Offset(tickX.coerceIn(0f, size.width - tickWidth), tickTop),
                            size = Size(tickWidth, tickHeight),
                        )
                    }
                }

                if (safeDurationMs > 0L && bookmarks.isNotEmpty()) {
                    val markerRadius = 3.5.dp.toPx()
                    val haloRadius = 4.5.dp.toPx()
                    bookmarks.forEach { bookmark ->
                        val bookmarkFraction = (bookmark.positionMs.toFloat() / safeDurationMs.toFloat())
                            .coerceIn(0f, 1f)
                        val markerX = (size.width * bookmarkFraction)
                            .coerceIn(markerRadius, (size.width - markerRadius).coerceAtLeast(markerRadius))
                        drawCircle(
                            color = Color(0xFF090C12).copy(alpha = 0.72f),
                            radius = haloRadius,
                            center = Offset(markerX, centerY),
                        )
                        drawCircle(
                            color = accentColor,
                            radius = markerRadius,
                            center = Offset(markerX, centerY),
                        )
                    }
                }

                val thumbRadius = (if (isScrubbing) 9.dp else 7.dp).toPx()
                val thumbX = playedWidth.coerceIn(thumbRadius, (size.width - thumbRadius).coerceAtLeast(thumbRadius))
                drawCircle(
                    color = JupiterColors.TextPrimary,
                    radius = thumbRadius,
                    center = Offset(thumbX, centerY),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = TimeFormatters.formatDuration(displayPosMs),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
                color = JupiterColors.TextPrimary,
            )

            val rightLabel = if (showRemainingTime && safeDurationMs > 0L) {
                "-${TimeFormatters.formatDuration((safeDurationMs - displayPosMs).coerceAtLeast(0L))}"
            } else {
                TimeFormatters.formatDuration(safeDurationMs)
            }
            Text(
                text = rightLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
                color = JupiterColors.TextSecondary,
                modifier = Modifier
                    .clip(LocalJupiterShapes.current.control)
                    .clickable(onClick = onToggleRemainingTime)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun HudGlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    iconSize: Dp = 18.dp,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val backgroundColor = if (selected && enabled) {
        accentColor.copy(alpha = 0.18f)
    } else {
        HudGlassBackdrop
    }
    val borderBrush = if (selected && enabled) {
        Brush.linearGradient(listOf(accentColor.copy(alpha = 0.45f), accentColor.copy(alpha = 0.25f)))
    } else {
        JupiterSpecularBorderBrush
    }
    val tint = if (selected && enabled) accentColor else JupiterColors.TextPrimary
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .background(backgroundColor, CircleShape)
            .border(1.dp, borderBrush, CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun HudGlassChip(
    icon: ImageVector?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    elevated: Boolean = false,
    shape: Shape = LocalJupiterShapes.current.control,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = when {
        accent -> accentColor.copy(alpha = 0.18f)
        elevated -> HudGlassElevatedBackdrop
        else -> HudGlassBackdrop
    }
    val borderColor = if (accent) {
        accentColor.copy(alpha = 0.44f)
    } else {
        JupiterColors.BorderSpecularTop
    }
    val textColor = if (accent) accentColor else JupiterColors.TextPrimary

    Row(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(bgColor, shape)
            .border(1.dp, borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
        )
    }
}

private fun formatSpeedLabel(speed: Float): String {
    val formatted = if ((speed * 100f).roundToInt() % 10 == 0) {
        String.format(Locale.US, "%.1f", speed)
    } else {
        String.format(Locale.US, "%.2f", speed)
    }
    return "${formatted}x"
}

private fun formatAbPillLabel(abLoop: AbLoopState): String {
    val a = abLoop.pointAMs
    val b = abLoop.pointBMs
    return when {
        a != null && b != null ->
            "A-B ${TimeFormatters.formatDuration(a)}-${TimeFormatters.formatDuration(b)}"
        a != null ->
            "A: ${TimeFormatters.formatDuration(a)}"
        else -> "A-B"
    }
}
