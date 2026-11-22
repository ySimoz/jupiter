package com.jupiter.app.feature.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun PlayerScreen(
    mediaUri: String,
    title: String,
    isNetworkStream: Boolean,
    headersJson: String?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val isInPipMode = LocalIsInPipMode.current
    val pipHostController = LocalPipHostController.current
    val currentIsInPipMode by rememberUpdatedState(isInPipMode)
    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* Playback proceeds even if notification permission is denied. */ }

    val externalSubtitleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.addExternalSubtitle(uri.toString())
        }
    }

    LaunchedEffect(Unit) {
        if (!hasRequestedNotificationPermission &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            hasRequestedNotificationPermission = true
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(mediaUri, title, isNetworkStream, headersJson) {
        viewModel.loadMedia(
            uri = mediaUri,
            title = title,
            isNetworkStream = isNetworkStream,
            headersJson = headersJson,
        )
    }

    LaunchedEffect(
        pipHostController,
        uiState.isPaused,
        uiState.videoWidth,
        uiState.videoHeight,
        uiState.autoEnterPip,
    ) {
        pipHostController?.updatePlayback(
            PipPlaybackSnapshot(
                isActive = true,
                isPlaying = !uiState.isPaused,
                videoWidth = uiState.videoWidth,
                videoHeight = uiState.videoHeight,
                autoEnterPip = uiState.autoEnterPip,
            ),
        )
    }

    DisposableEffect(pipHostController, viewModel) {
        pipHostController?.setActionHandler(viewModel::onPipAction)
        onDispose {
            pipHostController?.setActionHandler(null)
            pipHostController?.updatePlayback(PipPlaybackSnapshot(isActive = false))
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onHostStop(currentIsInPipMode)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = !isInPipMode && uiState.isLocked) {
        viewModel.flashUnlockHint()
    }

    PlayerImmersiveBarsEffect(isHudVisible = !isInPipMode && uiState.isHudVisible && !uiState.isLocked)
    PlayerOrientationEffect(orientationLock = uiState.orientationLock)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        MpvSurfaceView(
            onSurfaceAttached = viewModel::attachSurface,
            onSurfaceSizeChanged = viewModel::onSurfaceSizeChanged,
            onSurfaceDetached = viewModel::detachSurface,
            modifier = Modifier.fillMaxSize(),
        )

        if (!isInPipMode) {
            PlayerGestureLayer(
                isLocked = uiState.isLocked,
                gestureOverlay = uiState.gestureOverlay,
                currentPositionMs = { viewModel.positionMs.value },
                durationMs = uiState.durationMs,
                currentZoom = uiState.videoZoom,
                brightnessGestureEnabled = uiState.gestureBrightnessEnabled,
                volumeGestureEnabled = uiState.gestureVolumeEnabled,
                seekGestureEnabled = uiState.gestureSeekEnabled,
                onSingleTap = viewModel::toggleHud,
                onLockedTap = viewModel::flashUnlockHint,
                onDoubleTap = viewModel::onDoubleTap,
                onBrightnessGesture = viewModel::onBrightnessGesture,
                onVolumeGesture = viewModel::onVolumeGesture,
                onHorizontalDragUpdate = viewModel::onHorizontalDragUpdate,
                onHorizontalDragEnd = viewModel::onHorizontalDragEnd,
                onHorizontalDragCancel = viewModel::onHorizontalDragCancel,
                onLongPressSpeedStart = viewModel::onLongPressSpeedStart,
                onLongPressSpeedEnd = viewModel::onLongPressSpeedEnd,
                onPinchZoom = viewModel::onPinchZoom,
                onClearGestureOverlay = viewModel::clearGestureOverlay,
                modifier = Modifier.fillMaxSize(),
            )

            PlayerHudOverlay(
                uiState = uiState,
                positionMsFlow = viewModel.positionMs,
                onNavigateBack = onNavigateBack,
                onPipClick = { pipHostController?.enterPip() },
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeekBy = viewModel::seekBy,
                onScrubStart = viewModel::onScrubStart,
                onScrubChange = viewModel::onScrubChange,
                onScrubFinish = viewModel::onScrubFinish,
                onToggleRemainingTime = viewModel::toggleRemainingTime,
                onSetSpeed = viewModel::setSpeed,
                onCycleAspectMode = viewModel::cycleAspectMode,
                onSetAspectMode = viewModel::setAspectMode,
                onCycleOrientationLock = viewModel::cycleOrientationLock,
                onToggleLock = viewModel::toggleLock,
                onSelectAudioTrack = viewModel::selectAudioTrack,
                onAdjustAudioDelay = viewModel::adjustAudioDelay,
                onResetAudioDelay = viewModel::resetAudioDelay,
                onSelectSubtitleTrack = viewModel::selectSubtitleTrack,
                onSelectSecondarySubtitleTrack = viewModel::selectSecondarySubtitleTrack,
                onLoadExternalSubtitle = { externalSubtitleLauncher.launch(arrayOf("*/*")) },
                onAdjustSubtitleDelay = viewModel::adjustSubtitleDelay,
                onResetSubtitleDelay = viewModel::resetSubtitleDelay,
                onSetSubtitleScale = viewModel::setSubtitleScale,
                onResetSubtitleScale = viewModel::resetSubtitleScale,
                onSetHwDecMode = viewModel::setHwDecMode,
                onJumpToChapter = viewModel::jumpToChapter,
                onToggleAbLoop = viewModel::toggleAbLoop,
                onClearAbLoop = viewModel::clearAbLoop,
                onSetEqualizer = viewModel::setEqualizer,
                onResetEqualizer = viewModel::resetEqualizer,
                onSheetVisibilityChanged = viewModel::onSheetVisibilityChanged,
                onRetry = viewModel::retryLoad,
                onDismissError = viewModel::dismissError,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
