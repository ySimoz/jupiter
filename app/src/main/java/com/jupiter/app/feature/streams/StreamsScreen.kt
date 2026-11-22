package com.jupiter.app.feature.streams

import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jupiter.app.core.adaptive.LocalJupiterAdaptiveInfo
import com.jupiter.app.core.adaptive.NavigationLayout
import com.jupiter.app.core.designsystem.GlassDialog
import com.jupiter.app.core.designsystem.GlassPill
import com.jupiter.app.core.designsystem.GlassSurface
import com.jupiter.app.core.designsystem.LocalJupiterPalette
import com.jupiter.app.core.designsystem.LocalJupiterShapes
import com.jupiter.app.data.local.NetworkStreamEntity
import com.jupiter.app.data.repository.StreamHeaderConfig
import com.jupiter.app.data.repository.StreamUrlValidator
import com.jupiter.app.feature.playlists.AddToPlaylistSheet
import kotlinx.coroutines.delay

@Composable
private fun adaptiveErrorColor(): Color =
    if (LocalJupiterPalette.current.isLightCanvas) Color(0xFFB3261E) else Color(0xFFF28B82)

@Composable
fun StreamsScreen(
    contentPadding: PaddingValues,
    onOpenStream: (StreamLaunchRequest) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StreamsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    StreamsContent(
        uiState = uiState,
        contentPadding = contentPadding,
        onQuickUrlChange = viewModel::onQuickUrlChange,
        onPasteClick = {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val clipText = clipboard?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                .orEmpty()
            viewModel.onPasteQuickUrl(clipText)
        },
        onQuickPlayClick = {
            viewModel.submitQuickPlay()?.let(onOpenStream)
        },
        onSaveQuickUrlClick = {
            viewModel.openAddDialog(prefillUrl = uiState.quickUrl)
        },
        onAddStreamClick = {
            viewModel.openAddDialog()
        },
        onBookmarkClick = { stream ->
            onOpenStream(viewModel.prepareLaunch(stream))
        },
        onTogglePin = viewModel::togglePin,
        onEditBookmark = viewModel::openEditDialog,
        onDeleteBookmark = { stream -> viewModel.deleteStream(stream.id) },
        onDismissEditor = viewModel::dismissEditor,
        onUpdateEditor = viewModel::updateEditor,
        onAddHeaderRow = viewModel::addHeaderRow,
        onUpdateHeaderRow = viewModel::updateHeaderRow,
        onRemoveHeaderRow = viewModel::removeHeaderRow,
        onSaveEditor = viewModel::saveEditor,
        onAddStreamToPlaylist = viewModel::addStreamToPlaylist,
        onCreatePlaylistAndAddStream = viewModel::createPlaylistAndAddStream,
        onDismissFeedback = viewModel::dismissFeedback,
        modifier = modifier,
    )
}

@Composable
internal fun StreamsContent(
    uiState: StreamsUiState,
    contentPadding: PaddingValues,
    onQuickUrlChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onQuickPlayClick: () -> Unit,
    onSaveQuickUrlClick: () -> Unit,
    onAddStreamClick: () -> Unit,
    onBookmarkClick: (NetworkStreamEntity) -> Unit,
    onTogglePin: (NetworkStreamEntity) -> Unit,
    onEditBookmark: (NetworkStreamEntity) -> Unit,
    onDeleteBookmark: (NetworkStreamEntity) -> Unit,
    onDismissEditor: () -> Unit,
    onUpdateEditor: (
        title: String?,
        url: String?,
        userAgent: String?,
        referer: String?,
        isPinned: Boolean?,
    ) -> Unit,
    onAddHeaderRow: () -> Unit,
    onUpdateHeaderRow: (id: Int, key: String, value: String) -> Unit,
    onRemoveHeaderRow: (Int) -> Unit,
    onSaveEditor: () -> Unit,
    onAddStreamToPlaylist: (playlistId: Long, stream: NetworkStreamEntity) -> Unit = { _, _ -> },
    onCreatePlaylistAndAddStream: (name: String, stream: NetworkStreamEntity) -> Unit = { _, _ -> },
    onDismissFeedback: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalJupiterAdaptiveInfo.current
    val palette = LocalJupiterPalette.current
    var playlistTargetStream by remember { mutableStateOf<NetworkStreamEntity?>(null) }

    LaunchedEffect(uiState.feedbackMessage) {
        if (uiState.feedbackMessage != null) {
            delay(2500)
            onDismissFeedback()
        }
    }
    val columns = if (adaptiveInfo.widthSizeClass == WindowWidthSizeClass.Compact) {
        GridCells.Fixed(1)
    } else {
        GridCells.Adaptive(minSize = adaptiveInfo.gridMinColumnDp.dp)
    }

    val fabBottomPadding = if (adaptiveInfo.navigationLayout == NavigationLayout.BOTTOM_BAR) {
        96.dp
    } else {
        24.dp
    }
    val fabEndPadding = if (adaptiveInfo.navigationLayout == NavigationLayout.BOTTOM_BAR) {
        16.dp
    } else {
        24.dp
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = columns,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Streams",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                QuickPlayBar(
                    url = uiState.quickUrl,
                    errorMessage = uiState.quickUrlError,
                    onUrlChange = onQuickUrlChange,
                    onPasteClick = onPasteClick,
                    onSaveClick = onSaveQuickUrlClick,
                    onPlayClick = onQuickPlayClick,
                )
            }

            if (uiState.pinnedStreams.isEmpty() && uiState.savedStreams.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyStreamsCard(onAddClick = onAddStreamClick)
                }
            } else {
                if (uiState.pinnedStreams.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeading(title = "Pinned")
                    }
                    items(
                        items = uiState.pinnedStreams,
                        key = { "pinned_${it.id}" },
                    ) { stream ->
                        StreamBookmarkCard(
                            stream = stream,
                            onPlay = { onBookmarkClick(stream) },
                            onAddToPlaylist = { playlistTargetStream = stream },
                            onTogglePin = { onTogglePin(stream) },
                            onEdit = { onEditBookmark(stream) },
                            onDelete = { onDeleteBookmark(stream) },
                        )
                    }
                }

                if (uiState.savedStreams.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeading(title = "Bookmarks")
                    }
                    items(
                        items = uiState.savedStreams,
                        key = { "saved_${it.id}" },
                    ) { stream ->
                        StreamBookmarkCard(
                            stream = stream,
                            onPlay = { onBookmarkClick(stream) },
                            onAddToPlaylist = { playlistTargetStream = stream },
                            onTogglePin = { onTogglePin(stream) },
                            onEdit = { onEditBookmark(stream) },
                            onDelete = { onDeleteBookmark(stream) },
                        )
                    }
                }
            }
        }

        AddStreamFab(
            onClick = onAddStreamClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = fabEndPadding, bottom = fabBottomPadding),
        )

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
                hazeState = null,
                elevated = true,
                modifier = Modifier.clickable(role = Role.Button, onClick = onDismissFeedback),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }

    val targetStream = playlistTargetStream
    AddToPlaylistSheet(
        visible = targetStream != null,
        useSideSheet = adaptiveInfo.useSideSheet,
        playlists = uiState.playlists,
        onDismiss = { playlistTargetStream = null },
        onSelectPlaylist = { playlistId ->
            if (targetStream != null) {
                onAddStreamToPlaylist(playlistId, targetStream)
            }
            playlistTargetStream = null
        },
        onCreatePlaylistAndAdd = { name ->
            if (targetStream != null) {
                onCreatePlaylistAndAddStream(name, targetStream)
            }
            playlistTargetStream = null
        },
    )

    val editor = uiState.editor
    if (editor != null) {
        StreamEditorDialog(
            editor = editor,
            onDismiss = onDismissEditor,
            onUpdateEditor = onUpdateEditor,
            onAddHeaderRow = onAddHeaderRow,
            onUpdateHeaderRow = onUpdateHeaderRow,
            onRemoveHeaderRow = onRemoveHeaderRow,
            onSave = onSaveEditor,
        )
    }
}

@Composable
private fun QuickPlayBar(
    url: String,
    errorMessage: String?,
    onUrlChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onSaveClick: () -> Unit,
    onPlayClick: () -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val shapes = LocalJupiterShapes.current
    val errorColor = adaptiveErrorColor()

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        hazeState = null,
        shape = shapes.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(shapes.control)
                    .background(palette.canvas.copy(alpha = 0.56f))
                    .border(
                        width = 1.dp,
                        color = if (errorMessage != null) errorColor.copy(alpha = 0.64f) else palette.borderSubtle,
                        shape = shapes.control,
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (url.isEmpty()) {
                        Text(
                            text = "Stream URL",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                        )
                    }
                    BasicTextField(
                        value = url,
                        onValueChange = onUrlChange,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { onPlayClick() },
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = palette.textPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (url.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .semantics { contentDescription = "Clear URL" }
                            .clickable(role = Role.Button) { onUrlChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = errorColor,
                )
            }

            val accent = MaterialTheme.colorScheme.primary
            val onAccent = MaterialTheme.colorScheme.onPrimary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(shapes.control)
                            .background(palette.glassElevated.copy(alpha = 0.72f), shapes.control)
                            .border(1.dp, palette.borderSubtle, shapes.control)
                            .clickable(role = Role.Button, onClick = onPasteClick)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentPaste,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Paste",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = palette.textPrimary,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(palette.glassElevated.copy(alpha = 0.72f), CircleShape)
                            .border(1.dp, palette.borderSubtle, CircleShape)
                            .semantics { contentDescription = "Save bookmark" }
                            .clickable(role = Role.Button, onClick = onSaveClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkAdd,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(shapes.control)
                        .background(accent, shapes.control)
                        .clickable(role = Role.Button, onClick = onPlayClick)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = onAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Play",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = onAccent,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    val palette = LocalJupiterPalette.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = palette.textPrimary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun StreamBookmarkCard(
    stream: NetworkStreamEntity,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val shapes = LocalJupiterShapes.current
    val shape = shapes.surface
    val hasHeaders = !stream.userAgent.isNullOrBlank() ||
        !stream.referer.isNullOrBlank() ||
        StreamHeaderConfig.parseHeadersMap(stream.customHeadersJson).isNotEmpty()

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onPlay),
        hazeState = null,
        shape = shape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stream.title.ifBlank { StreamUrlValidator.deriveDisplayTitle(stream.url) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (hasHeaders) {
                    Box(
                        modifier = Modifier
                            .clip(shapes.control)
                            .background(palette.glassElevated.copy(alpha = 0.72f))
                            .border(1.dp, palette.borderSubtle, shapes.control)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Headers",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }

            Text(
                text = formatStreamMetadata(stream.url),
                style = MaterialTheme.typography.labelMedium.merge(
                    TextStyle(fontFeatureSettings = "tnum"),
                ),
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val accent = MaterialTheme.colorScheme.primary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Play",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = accent,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BookmarkActionIcon(
                        icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = palette.textSecondary,
                        onClick = onAddToPlaylist,
                    )
                    BookmarkActionIcon(
                        icon = Icons.Outlined.PushPin,
                        contentDescription = if (stream.isPinned) "Unpin stream" else "Pin stream",
                        tint = if (stream.isPinned) accent else palette.textSecondary,
                        onClick = onTogglePin,
                    )
                    BookmarkActionIcon(
                        icon = Icons.Outlined.Edit,
                        contentDescription = "Edit stream",
                        tint = palette.textSecondary,
                        onClick = onEdit,
                    )
                    BookmarkActionIcon(
                        icon = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete stream",
                        tint = palette.textSecondary,
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
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
private fun EmptyStreamsCard(onAddClick: () -> Unit) {
    val palette = LocalJupiterPalette.current
    val shapes = LocalJupiterShapes.current

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        hazeState = null,
        shape = shapes.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Public,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "No saved streams",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(shapes.control)
                    .background(palette.glassElevated, shapes.control)
                    .border(1.dp, palette.borderSubtle, shapes.control)
                    .clickable(role = Role.Button, onClick = onAddClick)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Add stream",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun AddStreamFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .semantics { contentDescription = "Add stream" }
            .clickable(role = Role.Button, onClick = onClick),
        hazeState = null,
        shape = CircleShape,
        elevated = true,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun StreamEditorDialog(
    editor: StreamEditorState,
    onDismiss: () -> Unit,
    onUpdateEditor: (
        title: String?,
        url: String?,
        userAgent: String?,
        referer: String?,
        isPinned: Boolean?,
    ) -> Unit,
    onAddHeaderRow: () -> Unit,
    onUpdateHeaderRow: (id: Int, key: String, value: String) -> Unit,
    onRemoveHeaderRow: (Int) -> Unit,
    onSave: () -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val errorColor = adaptiveErrorColor()
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.78f).dp
    val controlShape = LocalJupiterShapes.current.control
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary

    GlassDialog(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxDialogHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (editor.editingId == 0L) "Add stream" else "Edit stream",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
            )

            DialogInputField(
                value = editor.title,
                onValueChange = { onUpdateEditor(it, null, null, null, null) },
                placeholder = "Title (optional)",
                imeAction = ImeAction.Next,
            )

            DialogInputField(
                value = editor.url,
                onValueChange = { onUpdateEditor(null, it, null, null, null) },
                placeholder = "URL",
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
                isError = editor.errorMessage != null,
            )

            DialogInputField(
                value = editor.userAgent,
                onValueChange = { onUpdateEditor(null, null, it, null, null) },
                placeholder = "User-Agent (optional)",
                imeAction = ImeAction.Next,
            )

            DialogInputField(
                value = editor.referer,
                onValueChange = { onUpdateEditor(null, null, null, it, null) },
                placeholder = "Referer (optional)",
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(controlShape)
                    .clickable(role = Role.Checkbox) {
                        onUpdateEditor(null, null, null, null, !editor.isPinned)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Pin to top",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary,
                )
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(controlShape)
                        .background(
                            if (editor.isPinned) accent.copy(alpha = 0.18f)
                            else palette.glassPrimary.copy(alpha = 0.72f),
                            controlShape,
                        )
                        .border(
                            width = 1.dp,
                            color = if (editor.isPinned) accent else palette.borderSubtle,
                            shape = controlShape,
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (editor.isPinned) "Pinned" else "Off",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (editor.isPinned) accent else palette.textSecondary,
                    )
                }
            }

            HorizontalDivider(color = palette.borderSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Custom headers",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = palette.textSecondary,
                )
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(controlShape)
                        .background(palette.glassPrimary.copy(alpha = 0.72f), controlShape)
                        .border(1.dp, palette.borderSubtle, controlShape)
                        .clickable(role = Role.Button, onClick = onAddHeaderRow)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Add header",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                    )
                }
            }

            editor.customHeaders.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DialogInputField(
                        value = row.key,
                        onValueChange = { key -> onUpdateHeaderRow(row.id, key, row.value) },
                        placeholder = "Key",
                        modifier = Modifier.weight(0.44f),
                    )
                    DialogInputField(
                        value = row.value,
                        onValueChange = { value -> onUpdateHeaderRow(row.id, row.key, value) },
                        placeholder = "Value",
                        modifier = Modifier.weight(0.56f),
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .semantics { contentDescription = "Remove header" }
                            .clickable(role = Role.Button) { onRemoveHeaderRow(row.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            if (editor.errorMessage != null) {
                Text(
                    text = editor.errorMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = errorColor,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(controlShape)
                        .background(palette.glassPrimary.copy(alpha = 0.72f), controlShape)
                        .border(1.dp, palette.borderSubtle, controlShape)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(controlShape)
                        .background(accent, controlShape)
                        .clickable(role = Role.Button, onClick = onSave)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Save",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = onAccent,
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isError: Boolean = false,
) {
    val palette = LocalJupiterPalette.current
    val errorColor = adaptiveErrorColor()
    val inputShape = LocalJupiterShapes.current.control
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(inputShape)
            .background(palette.canvas.copy(alpha = 0.56f), inputShape)
            .border(
                width = 1.dp,
                color = if (isError) errorColor.copy(alpha = 0.64f) else palette.borderSubtle,
                shape = inputShape,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = palette.textPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun formatStreamMetadata(url: String): String {
    return when (val result = StreamUrlValidator.validate(url)) {
        is StreamUrlValidator.ValidationResult.Valid -> "${result.scheme} · ${result.host}"
        is StreamUrlValidator.ValidationResult.Invalid -> url
    }
}
