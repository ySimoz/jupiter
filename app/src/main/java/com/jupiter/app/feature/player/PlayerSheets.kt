package com.jupiter.app.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupiter.app.core.designsystem.GlassDialog
import com.jupiter.app.core.designsystem.JupiterColors
import com.jupiter.app.core.designsystem.LocalJupiterShapes
import com.jupiter.app.data.local.VideoBookmarkEntity
import com.jupiter.engine.AudioTrack
import com.jupiter.engine.Chapter
import com.jupiter.engine.HwDecMode
import com.jupiter.engine.SubtitleTrack
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PlayerSheetType(val title: String) {
    TRACKS("Tracks"),
    SPEED("Playback speed"),
    BOOKMARKS("Bookmarks"),
    QUEUE("Up next"),
    MORE("Controls"),
}

private val SPEED_SHEET_PRESETS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f)

@Composable
fun TracksSheetContent(
    uiState: PlayerUiState,
    onSelectSubtitleTrack: (Int?) -> Unit,
    onSelectSecondarySubtitleTrack: (Int?) -> Unit,
    onLoadExternalSubtitle: () -> Unit,
    onAdjustSubtitleDelay: (Double) -> Unit,
    onResetSubtitleDelay: () -> Unit,
    onSubtitleScaleChange: (Double) -> Unit,
    onResetSubtitleScale: () -> Unit,
    onSelectAudioTrack: (Int?) -> Unit,
    onAdjustAudioDelay: (Double) -> Unit,
    onResetAudioDelay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    Column(modifier = modifier.fillMaxWidth()) {
        SheetSegmentedTabs(
            tabs = listOf("Subtitles", "Audio"),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (selectedTab == 0) {
            SubtitleSheetContent(
                uiState = uiState,
                onSelectSubtitleTrack = onSelectSubtitleTrack,
                onSelectSecondarySubtitleTrack = onSelectSecondarySubtitleTrack,
                onLoadExternalSubtitle = onLoadExternalSubtitle,
                onAdjustSubtitleDelay = onAdjustSubtitleDelay,
                onResetSubtitleDelay = onResetSubtitleDelay,
                onSubtitleScaleChange = onSubtitleScaleChange,
                onResetSubtitleScale = onResetSubtitleScale,
            )
        } else {
            AudioSheetContent(
                uiState = uiState,
                onSelectAudioTrack = onSelectAudioTrack,
                onAdjustAudioDelay = onAdjustAudioDelay,
                onResetAudioDelay = onResetAudioDelay,
            )
        }
    }
}

@Composable
fun SubtitleSheetContent(
    uiState: PlayerUiState,
    onSelectSubtitleTrack: (Int?) -> Unit,
    onSelectSecondarySubtitleTrack: (Int?) -> Unit,
    onLoadExternalSubtitle: () -> Unit,
    onAdjustSubtitleDelay: (Double) -> Unit,
    onResetSubtitleDelay: () -> Unit,
    onSubtitleScaleChange: (Double) -> Unit,
    onResetSubtitleScale: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetSectionHeader(title = "Primary track")
            SheetTrackRow(
                title = "Off",
                meta = null,
                badge = null,
                selected = uiState.selectedSubtitleTrackId == null,
                onClick = { onSelectSubtitleTrack(null) },
            )
            uiState.subtitleTracks.forEach { track ->
                val label = resolveSubtitleTitle(track)
                SheetTrackRow(
                    title = label,
                    meta = buildSubtitleMeta(track, label),
                    badge = if (track.isExternal) "External" else null,
                    selected = uiState.selectedSubtitleTrackId == track.id,
                    onClick = { onSelectSubtitleTrack(track.id) },
                )
            }
        }

        SheetActionButton(
            label = "Load external subtitle",
            icon = Icons.Outlined.FolderOpen,
            onClick = onLoadExternalSubtitle,
        )

        if (uiState.subtitleTracks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetSectionHeader(title = "Secondary track")
                SheetTrackRow(
                    title = "Off",
                    meta = null,
                    badge = null,
                    selected = uiState.secondarySubtitleTrackId == null,
                    onClick = { onSelectSecondarySubtitleTrack(null) },
                )
                uiState.subtitleTracks.forEach { track ->
                    val label = resolveSubtitleTitle(track)
                    SheetTrackRow(
                        title = label,
                        meta = buildSubtitleMeta(track, label),
                        badge = if (track.isExternal) "External" else null,
                        selected = uiState.secondarySubtitleTrackId == track.id,
                        onClick = { onSelectSecondarySubtitleTrack(track.id) },
                    )
                }
            }
        }

        SheetDelayStepper(
            label = "Subtitle delay",
            delaySec = uiState.subDelaySec,
            onAdjust = onAdjustSubtitleDelay,
            onReset = onResetSubtitleDelay,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val scaleValue = uiState.subScale.toFloat().coerceIn(0.5f, 2.5f)
            SheetSectionHeader(
                title = "Subtitle scale",
                trailing = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatScaleLabel(uiState.subScale),
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        SheetResetButton(
                            enabled = abs(uiState.subScale - 1.0) > 0.01,
                            onClick = onResetSubtitleScale,
                        )
                    }
                },
            )
            GlassSlider(
                value = scaleValue,
                onValueChange = { raw ->
                    val stepped = (raw * 20f).roundToInt() / 20.0
                    onSubtitleScaleChange(stepped)
                },
                valueRange = 0.5f..2.5f,
            )
        }
    }
}

@Composable
fun AudioSheetContent(
    uiState: PlayerUiState,
    onSelectAudioTrack: (Int?) -> Unit,
    onAdjustAudioDelay: (Double) -> Unit,
    onResetAudioDelay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetSectionHeader(title = "Audio track")
            SheetTrackRow(
                title = "Off",
                meta = null,
                badge = null,
                selected = uiState.selectedAudioTrackId == null,
                onClick = { onSelectAudioTrack(null) },
            )
            uiState.audioTracks.forEach { track ->
                val label = resolveAudioTitle(track)
                SheetTrackRow(
                    title = label,
                    meta = buildAudioMeta(track, label),
                    badge = if (track.isExternal) "External" else null,
                    selected = uiState.selectedAudioTrackId == track.id,
                    onClick = { onSelectAudioTrack(track.id) },
                )
            }
        }

        SheetDelayStepper(
            label = "Audio delay",
            delaySec = uiState.audioDelaySec,
            onAdjust = onAdjustAudioDelay,
            onReset = onResetAudioDelay,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedSheetContent(
    uiState: PlayerUiState,
    onSelectSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetSectionHeader(
            title = "Current speed",
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatSheetSpeed(uiState.speed),
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    SheetResetButton(
                        enabled = abs(uiState.speed - 1.0f) > 0.01f,
                        onClick = { onSelectSpeed(1.0f) },
                    )
                }
            },
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SPEED_SHEET_PRESETS.forEach { preset ->
                val isSelected = abs(uiState.speed - preset) < 0.01f
                SheetChip(
                    label = formatSheetSpeed(preset),
                    selected = isSelected,
                    onClick = { onSelectSpeed(preset) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetSectionHeader(title = "Fine adjustment")
            GlassSlider(
                value = uiState.speed.coerceIn(0.25f, 4.0f),
                onValueChange = { raw ->
                    val stepped = ((raw * 20f).roundToInt() / 20f).coerceIn(0.25f, 4.0f)
                    onSelectSpeed(stepped)
                },
                valueRange = 0.25f..4.0f,
            )
        }
    }
}

@Composable
fun BookmarksSheetContent(
    uiState: PlayerUiState,
    positionMsFlow: StateFlow<Long>,
    onAddBookmark: (label: String) -> Unit,
    onRenameBookmark: (id: Long, label: String) -> Unit,
    onDeleteBookmark: (id: Long) -> Unit,
    onJumpToBookmark: (VideoBookmarkEntity) -> Unit,
    onCaptureFrame: () -> Unit,
    onSelectChapter: (Chapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasChapters = uiState.chapters.isNotEmpty()
    var selectedTab by rememberSaveable(hasChapters) { mutableStateOf(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (hasChapters) {
            SheetSegmentedTabs(
                tabs = listOf("Bookmarks", "Chapters"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (!hasChapters || selectedTab == 0) {
            BookmarksTabContent(
                bookmarks = uiState.bookmarks,
                isCapturingFrame = uiState.isCapturingFrame,
                positionMsFlow = positionMsFlow,
                onAddBookmark = onAddBookmark,
                onRenameBookmark = onRenameBookmark,
                onDeleteBookmark = onDeleteBookmark,
                onJumpToBookmark = onJumpToBookmark,
                onCaptureFrame = onCaptureFrame,
            )
        } else {
            ChaptersSheetContent(
                uiState = uiState,
                onSelectChapter = onSelectChapter,
            )
        }
    }
}

@Composable
private fun BookmarksTabContent(
    bookmarks: List<VideoBookmarkEntity>,
    isCapturingFrame: Boolean,
    positionMsFlow: StateFlow<Long>,
    onAddBookmark: (label: String) -> Unit,
    onRenameBookmark: (id: Long, label: String) -> Unit,
    onDeleteBookmark: (id: Long) -> Unit,
    onJumpToBookmark: (VideoBookmarkEntity) -> Unit,
    onCaptureFrame: () -> Unit,
) {
    var labelDraft by rememberSaveable { mutableStateOf("") }
    var editingBookmark by remember { mutableStateOf<VideoBookmarkEntity?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetTextInputField(
            value = labelDraft,
            onValueChange = { labelDraft = it },
            placeholder = "Label (optional)",
            onDone = {
                onAddBookmark(labelDraft)
                labelDraft = ""
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AddBookmarkButton(
                positionMsFlow = positionMsFlow,
                onClick = {
                    onAddBookmark(labelDraft)
                    labelDraft = ""
                },
                modifier = Modifier.weight(1f),
            )
            CaptureFrameButton(
                isCapturing = isCapturingFrame,
                onClick = onCaptureFrame,
                modifier = Modifier.weight(1f),
            )
        }

        if (bookmarks.isEmpty()) {
            Text(
                text = "No bookmarks saved",
                style = MaterialTheme.typography.bodyMedium,
                color = JupiterColors.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = bookmarks,
                    key = { it.id },
                ) { bookmark ->
                    BookmarkRow(
                        bookmark = bookmark,
                        onJump = { onJumpToBookmark(bookmark) },
                        onEdit = { editingBookmark = bookmark },
                        onDelete = { onDeleteBookmark(bookmark.id) },
                    )
                }
            }
        }
    }

    val target = editingBookmark
    if (target != null) {
        RenameBookmarkDialog(
            initialLabel = target.label,
            onDismiss = { editingBookmark = null },
            onSave = { updated ->
                onRenameBookmark(target.id, updated)
                editingBookmark = null
            },
        )
    }
}

@Composable
private fun AddBookmarkButton(
    positionMsFlow: StateFlow<Long>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val livePositionMs by positionMsFlow.collectAsState()
    val shape = LocalJupiterShapes.current.control
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(accent.copy(alpha = 0.16f), shape)
            .border(1.dp, accent.copy(alpha = 0.44f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.BookmarkAdd,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Save ${TimeFormatters.formatDuration(livePositionMs)}",
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CaptureFrameButton(
    isCapturing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = LocalJupiterShapes.current.control
    val textColor = if (isCapturing) {
        JupiterColors.TextSecondary.copy(alpha = 0.6f)
    } else {
        JupiterColors.TextPrimary
    }
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(JupiterColors.GlassElevated.copy(alpha = 0.72f), shape)
            .border(1.dp, JupiterColors.BorderSpecularTop, shape)
            .clickable(enabled = !isCapturing, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCapturing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isCapturing) "Saving" else "Capture frame",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BookmarkRow(
    bookmark: VideoBookmarkEntity,
    onJump: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = LocalJupiterShapes.current.control
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(JupiterColors.GlassPrimary.copy(alpha = 0.64f), shape)
            .border(1.dp, JupiterColors.BorderSubtle, shape)
            .clickable(role = Role.Button, onClick = onJump)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = TimeFormatters.formatDuration(bookmark.positionMs),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium,
            color = accent,
            modifier = Modifier
                .clip(shape)
                .background(accent.copy(alpha = 0.14f), shape)
                .border(1.dp, accent.copy(alpha = 0.36f), shape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            text = bookmark.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = JupiterColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SheetRowIconButton(
            icon = Icons.Outlined.Edit,
            contentDescription = "Rename bookmark",
            tint = JupiterColors.TextSecondary,
            onClick = onEdit,
        )
        SheetRowIconButton(
            icon = Icons.Outlined.DeleteOutline,
            contentDescription = "Delete bookmark",
            tint = JupiterColors.TextSecondary,
            onClick = onDelete,
        )
    }
}

@Composable
private fun RenameBookmarkDialog(
    initialLabel: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by rememberSaveable(initialLabel) { mutableStateOf(initialLabel) }
    val trimmed = draft.trim()
    val accent = MaterialTheme.colorScheme.primary
    val shape = LocalJupiterShapes.current.control

    GlassDialog(
        onDismissRequest = onDismiss,
        hazeState = null,
    ) {
        Text(
            text = "Rename bookmark",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = JupiterColors.TextPrimary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SheetTextInputField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = "Bookmark label",
            onDone = {
                if (trimmed.isNotEmpty()) {
                    onSave(trimmed)
                }
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(shape)
                    .background(JupiterColors.GlassPrimary.copy(alpha = 0.64f), shape)
                    .border(1.dp, JupiterColors.BorderSubtle, shape)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = JupiterColors.TextPrimary,
                )
            }
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(shape)
                    .background(
                        if (trimmed.isNotEmpty()) accent else JupiterColors.GlassPrimary.copy(alpha = 0.40f),
                        shape,
                    )
                    .clickable(
                        enabled = trimmed.isNotEmpty(),
                        role = Role.Button,
                        onClick = { onSave(trimmed) },
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Save",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (trimmed.isNotEmpty()) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        JupiterColors.TextSecondary.copy(alpha = 0.40f)
                    },
                )
            }
        }
    }
}

@Composable
fun QueueSheetContent(
    uiState: PlayerUiState,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val currentDisplayIndex = if (uiState.queueIndex >= 0) uiState.queueIndex + 1 else 0
        SheetSectionHeader(
            title = uiState.queueSourceTitle.ifBlank { "Queue" },
            trailing = {
                Text(
                    text = "$currentDisplayIndex / ${uiState.queueItems.size}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QueueNavButton(
                label = "Previous",
                icon = Icons.Outlined.SkipPrevious,
                enabled = uiState.hasPreviousInQueue,
                onClick = onPlayPrevious,
                modifier = Modifier.weight(1f),
            )
            QueueNavButton(
                label = "Next",
                icon = Icons.Outlined.SkipNext,
                enabled = uiState.hasNextInQueue,
                onClick = onPlayNext,
                modifier = Modifier.weight(1f),
            )
        }

        if (uiState.queueItems.isEmpty()) {
            Text(
                text = "Queue is empty",
                style = MaterialTheme.typography.bodyMedium,
                color = JupiterColors.TextSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    items = uiState.queueItems,
                    key = { index, item -> "${index}_${item.uri}" },
                ) { index, item ->
                    val isCurrent = index == uiState.queueIndex
                    val shape = LocalJupiterShapes.current.control
                    val accent = MaterialTheme.colorScheme.primary
                    val bgColor = if (isCurrent) {
                        accent.copy(alpha = 0.16f)
                    } else {
                        JupiterColors.GlassPrimary.copy(alpha = 0.64f)
                    }
                    val borderColor = if (isCurrent) {
                        accent.copy(alpha = 0.48f)
                    } else {
                        JupiterColors.BorderSubtle
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(shape)
                            .background(bgColor, shape)
                            .border(1.dp, borderColor, shape)
                            .clickable(role = Role.Button) { onSelectQueueIndex(index) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Medium,
                            color = if (isCurrent) accent else JupiterColors.TextSecondary,
                            modifier = Modifier.width(24.dp),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = item.title.ifBlank { "Video ${index + 1}" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isCurrent) accent else JupiterColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.isNetworkStream) {
                            val badgeShape = LocalJupiterShapes.current.control
                            Text(
                                text = "Stream",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = JupiterColors.TextSecondary,
                                modifier = Modifier
                                    .clip(badgeShape)
                                    .background(JupiterColors.GlassElevated.copy(alpha = 0.8f), badgeShape)
                                    .border(1.dp, JupiterColors.BorderSubtle, badgeShape)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        if (item.durationMs > 0L) {
                            Text(
                                text = TimeFormatters.formatDuration(item.durationMs),
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Medium,
                                color = if (isCurrent) accent else JupiterColors.TextSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueNavButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = LocalJupiterShapes.current.control
    val textColor = if (enabled) JupiterColors.TextPrimary else JupiterColors.TextSecondary.copy(alpha = 0.40f)
    val bgColor = if (enabled) {
        JupiterColors.GlassElevated.copy(alpha = 0.72f)
    } else {
        JupiterColors.GlassPrimary.copy(alpha = 0.40f)
    }
    val borderColor = if (enabled) JupiterColors.BorderSubtle else JupiterColors.BorderSubtle.copy(alpha = 0.04f)
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(bgColor, shape)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
fun ChaptersSheetContent(
    uiState: PlayerUiState,
    onSelectChapter: (Chapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.chapters.isEmpty()) {
        Text(
            text = "No chapters in this media",
            style = MaterialTheme.typography.bodyMedium,
            color = JupiterColors.TextSecondary,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = uiState.chapters,
            key = { index, chapter -> "${chapter.index}_$index" },
        ) { index, chapter ->
            val isCurrent = chapter.index == uiState.currentChapter
            val shape = LocalJupiterShapes.current.control
            val accent = MaterialTheme.colorScheme.primary
            val bgColor = if (isCurrent) {
                accent.copy(alpha = 0.16f)
            } else {
                JupiterColors.GlassPrimary.copy(alpha = 0.64f)
            }
            val borderColor = if (isCurrent) {
                accent.copy(alpha = 0.48f)
            } else {
                JupiterColors.BorderSubtle
            }
            val chapterTitle = chapter.title?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}"
            val timestamp = TimeFormatters.formatDuration((chapter.timeSec * 1000.0).toLong())

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(bgColor, shape)
                    .border(1.dp, borderColor, shape)
                    .clickable(role = Role.Button) { onSelectChapter(chapter) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isCurrent) accent else JupiterColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Medium,
                    color = if (isCurrent) accent else JupiterColors.TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreControlsSheetContent(
    uiState: PlayerUiState,
    onSelectAspectMode: (AspectMode) -> Unit,
    onSelectHwDecMode: (HwDecMode) -> Unit,
    onToggleAbLoop: () -> Unit,
    onClearAbLoop: () -> Unit,
    onAutoPlayNextChange: (Boolean) -> Unit,
    onOpenQueue: () -> Unit,
    onEqualizerChange: (brightness: Int, contrast: Int, saturation: Int, gamma: Int) -> Unit,
    onResetEqualizer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        SheetSegmentedTabs(
            tabs = listOf("Playback", "Video"),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetSectionHeader(title = "Aspect ratio")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AspectMode.entries.forEach { mode ->
                            SheetChip(
                                label = mode.label,
                                selected = uiState.aspectMode == mode,
                                onClick = { onSelectAspectMode(mode) },
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val abLoop = uiState.abLoop
                    val abStatus = formatAbLoopStatus(abLoop)
                    SheetSectionHeader(
                        title = "A-B loop",
                        trailing = {
                            Text(
                                text = abStatus,
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Medium,
                                color = if (abLoop.isActive || abLoop.pointAMs != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    JupiterColors.TextSecondary
                                },
                            )
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val canSetA = abLoop.pointAMs == null
                        val canSetB = abLoop.pointAMs != null && abLoop.pointBMs == null
                        val canClear = abLoop.pointAMs != null || abLoop.pointBMs != null || abLoop.isActive
                        SheetChip(
                            label = "Set A",
                            selected = abLoop.pointAMs != null && abLoop.pointBMs == null,
                            enabled = canSetA,
                            onClick = onToggleAbLoop,
                            modifier = Modifier.weight(1f),
                        )
                        SheetChip(
                            label = "Set B",
                            selected = abLoop.pointBMs != null,
                            enabled = canSetB,
                            onClick = onToggleAbLoop,
                            modifier = Modifier.weight(1f),
                        )
                        SheetChip(
                            label = "Clear",
                            selected = false,
                            enabled = canClear,
                            onClick = onClearAbLoop,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                SheetSwitchRow(
                    label = "Auto-play next",
                    checked = uiState.autoPlayNext,
                    onCheckedChange = onAutoPlayNextChange,
                )

                if (uiState.hasQueue) {
                    SheetActionButton(
                        label = "Queue (${uiState.queueItems.size})",
                        icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
                        onClick = onOpenQueue,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetSectionHeader(title = "Hardware decoding")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val hwModes = listOf(
                            "Auto" to HwDecMode.AUTO,
                            "HW+" to HwDecMode.MEDIACODEC,
                            "SW" to HwDecMode.SOFTWARE,
                        )
                        hwModes.forEach { (label, mode) ->
                            val isSelected = when (mode) {
                                HwDecMode.AUTO ->
                                    uiState.hwDecMode == HwDecMode.AUTO || uiState.hwDecMode == HwDecMode.MEDIACODEC_COPY
                                else -> uiState.hwDecMode == mode
                            }
                            SheetChip(
                                label = label,
                                selected = isSelected,
                                onClick = { onSelectHwDecMode(mode) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val eq = uiState.equalizer
                    SheetSectionHeader(
                        title = "Video equalizer",
                        trailing = {
                            SheetResetButton(
                                enabled = !eq.isDefault,
                                onClick = onResetEqualizer,
                            )
                        },
                    )
                    EqualizerSliderRow(
                        label = "Brightness",
                        value = eq.brightness,
                        onValueChange = { onEqualizerChange(it, eq.contrast, eq.saturation, eq.gamma) },
                    )
                    EqualizerSliderRow(
                        label = "Contrast",
                        value = eq.contrast,
                        onValueChange = { onEqualizerChange(eq.brightness, it, eq.saturation, eq.gamma) },
                    )
                    EqualizerSliderRow(
                        label = "Saturation",
                        value = eq.saturation,
                        onValueChange = { onEqualizerChange(eq.brightness, eq.contrast, it, eq.gamma) },
                    )
                    EqualizerSliderRow(
                        label = "Gamma",
                        value = eq.gamma,
                        onValueChange = { onEqualizerChange(eq.brightness, eq.contrast, eq.saturation, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val shape = LocalJupiterShapes.current.control
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(JupiterColors.GlassPrimary.copy(alpha = 0.64f), shape)
            .border(1.dp, JupiterColors.BorderSubtle, shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bgColor = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent
            val borderColor = if (selected) accent.copy(alpha = 0.44f) else Color.Transparent
            val textColor = if (selected) accent else JupiterColors.TextSecondary
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(shape)
                    .background(bgColor, shape)
                    .border(1.dp, borderColor, shape)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SheetSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val shape = LocalJupiterShapes.current.control
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(JupiterColors.GlassPrimary.copy(alpha = 0.64f), shape)
            .border(1.dp, JupiterColors.BorderSubtle, shape)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = JupiterColors.TextPrimary,
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = accent,
                uncheckedThumbColor = JupiterColors.TextSecondary,
                uncheckedTrackColor = JupiterColors.Canvas.copy(alpha = 0.64f),
                uncheckedBorderColor = JupiterColors.BorderSubtle,
            ),
        )
    }
}

@Composable
private fun SheetTextInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onDone: () -> Unit,
) {
    val shape = LocalJupiterShapes.current.control
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(JupiterColors.Canvas.copy(alpha = 0.56f), shape)
            .border(1.dp, JupiterColors.BorderSubtle, shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = JupiterColors.TextSecondary.copy(alpha = 0.64f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            cursorBrush = SolidColor(accent),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = JupiterColors.TextPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SheetRowIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val shape = LocalJupiterShapes.current.control
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .semantics { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun EqualizerSliderRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                color = JupiterColors.TextPrimary,
            )
            Text(
                text = if (value > 0) "+$value" else "$value",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
                color = if (value != 0) MaterialTheme.colorScheme.primary else JupiterColors.TextSecondary,
            )
        }
        GlassSlider(
            value = value.coerceIn(-100, 100).toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(-100, 100)) },
            valueRange = -100f..100f,
        )
    }
}

@Composable
private fun SheetSectionHeader(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = JupiterColors.TextSecondary,
        )
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun SheetResetButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = LocalJupiterShapes.current.control
    Box(
        modifier = Modifier
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Reset",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                JupiterColors.TextSecondary.copy(alpha = 0.4f)
            },
        )
    }
}

@Composable
private fun SheetTrackRow(
    title: String,
    meta: String?,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = LocalJupiterShapes.current.control
    val accent = MaterialTheme.colorScheme.primary
    val bgColor = if (selected) {
        accent.copy(alpha = 0.16f)
    } else {
        JupiterColors.GlassPrimary.copy(alpha = 0.64f)
    }
    val borderColor = if (selected) {
        accent.copy(alpha = 0.48f)
    } else {
        JupiterColors.BorderSubtle
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(bgColor, shape)
            .border(1.dp, borderColor, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (selected) accent else JupiterColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!meta.isNullOrBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Normal,
                    color = JupiterColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!badge.isNullOrBlank()) {
            val badgeShape = LocalJupiterShapes.current.control
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = JupiterColors.TextSecondary,
                modifier = Modifier
                    .clip(badgeShape)
                    .background(JupiterColors.GlassElevated.copy(alpha = 0.8f), badgeShape)
                    .border(1.dp, JupiterColors.BorderSubtle, badgeShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SheetDelayStepper(
    label: String,
    delaySec: Double,
    onAdjust: (Double) -> Unit,
    onReset: () -> Unit,
) {
    val roundedTenths = (delaySec * 10.0).roundToInt()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SheetSectionHeader(
            title = label,
            trailing = {
                SheetResetButton(
                    enabled = roundedTenths != 0,
                    onClick = onReset,
                )
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetChip(
                label = "-100ms",
                selected = false,
                onClick = { onAdjust(-0.1) },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDelaySec(delaySec),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
                color = if (roundedTenths != 0) MaterialTheme.colorScheme.primary else JupiterColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            SheetChip(
                label = "+100ms",
                selected = false,
                onClick = { onAdjust(0.1) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SheetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = LocalJupiterShapes.current.control
    val accent = MaterialTheme.colorScheme.primary
    val bgColor = when {
        !enabled -> JupiterColors.GlassPrimary.copy(alpha = 0.40f)
        selected -> accent.copy(alpha = 0.16f)
        else -> JupiterColors.GlassPrimary.copy(alpha = 0.64f)
    }
    val borderColor = when {
        !enabled -> JupiterColors.BorderSubtle.copy(alpha = 0.04f)
        selected -> accent.copy(alpha = 0.48f)
        else -> JupiterColors.BorderSubtle
    }
    val textColor = when {
        !enabled -> JupiterColors.TextSecondary.copy(alpha = 0.40f)
        selected -> accent
        else -> JupiterColors.TextPrimary
    }

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(bgColor, shape)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun SheetActionButton(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = LocalJupiterShapes.current.control
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(JupiterColors.GlassElevated.copy(alpha = 0.72f), shape)
            .border(1.dp, JupiterColors.BorderSpecularTop, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = JupiterColors.TextPrimary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = JupiterColors.TextPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = JupiterColors.TextPrimary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = Color.White.copy(alpha = 0.16f),
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
}

private fun resolveSubtitleTitle(track: SubtitleTrack): String =
    track.title?.takeIf { it.isNotBlank() }
        ?: track.lang?.takeIf { it.isNotBlank() }
        ?: "Track ${track.id}"

private fun buildSubtitleMeta(track: SubtitleTrack, primaryLabel: String): String? {
    val lang = track.lang?.takeIf { it.isNotBlank() && it != primaryLabel }
    val codec = track.codec?.takeIf { it.isNotBlank() }?.uppercase(Locale.US)
    val parts = listOfNotNull(lang, codec)
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

private fun resolveAudioTitle(track: AudioTrack): String =
    track.title?.takeIf { it.isNotBlank() }
        ?: track.lang?.takeIf { it.isNotBlank() }
        ?: "Track ${track.id}"

private fun buildAudioMeta(track: AudioTrack, primaryLabel: String): String? {
    val lang = track.lang?.takeIf { it.isNotBlank() && it != primaryLabel }
    val codec = track.codec?.takeIf { it.isNotBlank() }?.uppercase(Locale.US)
    val channels = formatChannelCount(track.channelCount)
    val parts = listOfNotNull(lang, codec, channels)
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

private fun formatChannelCount(channels: Int?): String? =
    when {
        channels == null || channels <= 0 -> null
        channels == 1 -> "Mono"
        channels == 2 -> "Stereo"
        channels == 6 -> "5.1"
        channels == 8 -> "7.1"
        else -> "${channels}ch"
    }

private fun formatDelaySec(delaySec: Double): String {
    val tenths = (delaySec * 10.0).roundToInt()
    if (tenths == 0) return "0.0s"
    return String.format(Locale.US, "%+.1fs", tenths / 10.0)
}

private fun formatScaleLabel(scale: Double): String {
    val hundredths = (scale * 100.0).roundToInt()
    return if (hundredths % 10 == 0) {
        String.format(Locale.US, "%.1fx", hundredths / 100.0)
    } else {
        String.format(Locale.US, "%.2fx", hundredths / 100.0)
    }
}

private fun formatSheetSpeed(speed: Float): String {
    val hundredths = (speed * 100f).roundToInt()
    return if (hundredths % 10 == 0) {
        String.format(Locale.US, "%.1fx", hundredths / 100f)
    } else {
        String.format(Locale.US, "%.2fx", hundredths / 100f)
    }
}

private fun formatAbLoopStatus(abLoop: AbLoopState): String {
    val a = abLoop.pointAMs
    val b = abLoop.pointBMs
    return when {
        a != null && b != null ->
            "A: ${TimeFormatters.formatDuration(a)} - B: ${TimeFormatters.formatDuration(b)}"
        a != null ->
            "A: ${TimeFormatters.formatDuration(a)}"
        else -> "Off"
    }
}
