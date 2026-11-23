package com.jupiter.app.feature.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jupiter.app.core.adaptive.LocalJupiterAdaptiveInfo
import com.jupiter.app.core.adaptive.NavigationLayout
import com.jupiter.app.core.designsystem.GlassDialog
import com.jupiter.app.core.designsystem.GlassDropdownMenu
import com.jupiter.app.core.designsystem.GlassSurface
import com.jupiter.app.core.designsystem.LocalJupiterPalette
import com.jupiter.app.core.designsystem.LocalJupiterShapes
import com.jupiter.app.data.local.PlaylistItemEntity
import com.jupiter.app.data.local.PlaylistSummary
import com.jupiter.app.feature.player.QueueMediaItem
import com.jupiter.app.feature.player.TimeFormatters
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun PlaylistsScreen(
    contentPadding: PaddingValues,
    onOpenQueue: (items: List<QueueMediaItem>, startIndex: Int, sourceTitle: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(enabled = uiState.selectedPlaylistId != null) {
        viewModel.selectPlaylist(null)
    }

    PlaylistsContent(
        uiState = uiState,
        contentPadding = contentPadding,
        onSelectPlaylist = viewModel::selectPlaylist,
        onPlayPlaylist = { playlist ->
            viewModel.launchPlaylist(playlist, onOpenQueue)
        },
        onOpenQueue = onOpenQueue,
        onCreatePlaylistClick = viewModel::openCreateDialog,
        onRenamePlaylistClick = viewModel::openRenameDialog,
        onDeletePlaylistClick = { playlist -> viewModel.deletePlaylist(playlist.id) },
        onMoveItem = viewModel::moveItem,
        onRemoveItem = viewModel::removeItem,
        onDialogNameChange = viewModel::updateDialogName,
        onDismissDialog = viewModel::dismissDialog,
        onSubmitDialog = viewModel::submitDialog,
        modifier = modifier,
    )
}

@Composable
internal fun PlaylistsContent(
    uiState: PlaylistsUiState,
    contentPadding: PaddingValues,
    onSelectPlaylist: (Long?) -> Unit,
    onPlayPlaylist: (PlaylistSummary) -> Unit,
    onOpenQueue: (items: List<QueueMediaItem>, startIndex: Int, sourceTitle: String) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onRenamePlaylistClick: (PlaylistSummary) -> Unit,
    onDeletePlaylistClick: (PlaylistSummary) -> Unit,
    onMoveItem: (itemId: Long, moveUp: Boolean) -> Unit,
    onRemoveItem: (itemId: Long) -> Unit,
    onDialogNameChange: (String) -> Unit,
    onDismissDialog: () -> Unit,
    onSubmitDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalJupiterAdaptiveInfo.current
    val palette = LocalJupiterPalette.current
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

    val selectedPlaylist = uiState.selectedPlaylist
    val screenHazeState = rememberHazeState()
    Box(modifier = modifier.fillMaxSize()) {
        if (selectedPlaylist != null) {
            PlaylistDetailView(
                playlist = selectedPlaylist,
                items = uiState.selectedItems,
                contentPadding = contentPadding,
                onBackClick = { onSelectPlaylist(null) },
                onPlayAll = {
                    val queueItems = uiState.selectedItems.map { it.toQueueMediaItem() }
                    if (queueItems.isNotEmpty()) {
                        onOpenQueue(queueItems, 0, selectedPlaylist.name)
                    }
                },
                onItemClick = { index ->
                    val queueItems = uiState.selectedItems.map { it.toQueueMediaItem() }
                    if (index in queueItems.indices) {
                        onOpenQueue(queueItems, index, selectedPlaylist.name)
                    }
                },
                onMoveItem = onMoveItem,
                onRemoveItem = onRemoveItem,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = adaptiveInfo.gridMinColumnDp.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = screenHazeState),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                if (uiState.playlists.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyPlaylistsCard(onCreateClick = onCreatePlaylistClick)
                    }
                } else {
                    items(
                        items = uiState.playlists,
                        key = { "playlist_${it.id}" },
                    ) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onSelectPlaylist(playlist.id) },
                            onPlayClick = { onPlayPlaylist(playlist) },
                            onRenameClick = { onRenamePlaylistClick(playlist) },
                            onDeleteClick = { onDeletePlaylistClick(playlist) },
                        )
                    }
                }
            }

            CreatePlaylistFab(
                onClick = onCreatePlaylistClick,
                hazeState = screenHazeState,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = fabEndPadding, bottom = fabBottomPadding),
            )
        }
    }

    val dialog = uiState.dialog
    if (dialog != null) {
        PlaylistNameDialog(
            dialog = dialog,
            onNameChange = onDialogNameChange,
            onDismiss = onDismissDialog,
            onSubmit = onSubmitDialog,
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: PlaylistSummary,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalJupiterPalette.current
    val shapes = LocalJupiterShapes.current
    val shape = shapes.surface
    var menuExpanded by remember { mutableStateOf(false) }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        hazeState = null,
        shape = shape,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (playlist.previewUri == null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
                        contentDescription = null,
                        tint = palette.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.Center),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.VideoLibrary,
                        contentDescription = null,
                        tint = palette.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center),
                    )
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(playlist.previewUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatPlaylistComposition(
                            videoCount = playlist.videoCount,
                            streamCount = playlist.streamCount,
                        ),
                        style = MaterialTheme.typography.labelMedium.merge(
                            TextStyle(fontFeatureSettings = "tnum"),
                        ),
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (playlist.itemCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .semantics { contentDescription = "Play ${playlist.name}" }
                            .clickable(role = Role.Button, onClick = onPlayClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                var anchorBounds by remember { mutableStateOf(Rect.Zero) }
                Box(
                    modifier = Modifier.onGloballyPositioned { anchorBounds = it.boundsInRoot() },
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .semantics { contentDescription = "Playlist options" }
                            .clickable(role = Role.Button) { menuExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    GlassDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        anchorBounds = anchorBounds,
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Rename",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textPrimary,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRenameClick()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Delete",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textPrimary,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: PlaylistSummary,
    items: List<PlaylistItemEntity>,
    contentPadding: PaddingValues,
    onBackClick: () -> Unit,
    onPlayAll: () -> Unit,
    onItemClick: (index: Int) -> Unit,
    onMoveItem: (itemId: Long, moveUp: Boolean) -> Unit,
    onRemoveItem: (itemId: Long) -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val shapes = LocalJupiterShapes.current
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlassSurface(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .height(40.dp)
                            .clip(shapes.control)
                            .clickable(role = Role.Button, onClick = onBackClick),
                        hazeState = null,
                        shape = shapes.control,
                        elevated = true,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back to playlists",
                                tint = palette.textPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Text(
                        text = formatPlaylistComposition(
                            videoCount = playlist.videoCount,
                            streamCount = playlist.streamCount,
                        ),
                        style = MaterialTheme.typography.labelMedium.merge(
                            TextStyle(fontFeatureSettings = "tnum"),
                        ),
                        color = palette.textSecondary,
                        maxLines = 1,
                    )
                }

                if (items.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(shapes.control)
                            .background(accent, shapes.control)
                            .clickable(role = Role.Button, onClick = onPlayAll)
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
                            text = "Play all",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = onAccent,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        if (items.isEmpty()) {
            item {
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "No items in this playlist",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                        )
                    }
                }
            }
        } else {
            itemsIndexed(
                items = items,
                key = { _, item -> "playlist_item_${item.id}" },
            ) { index, item ->
                PlaylistItemRow(
                    item = item,
                    canMoveUp = index > 0,
                    canMoveDown = index < items.lastIndex,
                    onClick = { onItemClick(index) },
                    onMoveUp = { onMoveItem(item.id, true) },
                    onMoveDown = { onMoveItem(item.id, false) },
                    onRemove = { onRemoveItem(item.id) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistItemRow(
    item: PlaylistItemEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalJupiterPalette.current
    val shapes = LocalJupiterShapes.current
    val innerThumbShape = RoundedCornerShape((4f + 8f * (shapes.percent / 100f)).dp)
    var menuExpanded by remember { mutableStateOf(false) }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.surface)
            .clickable(onClick = onClick),
        hazeState = null,
        shape = shapes.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .aspectRatio(16f / 9f)
                    .clip(innerThumbShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (item.isNetworkStream) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Movie,
                        contentDescription = null,
                        tint = palette.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.mediaUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (item.isNetworkStream || item.durationMs > 0L) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (item.isNetworkStream) {
                            Box(
                                modifier = Modifier
                                    .clip(shapes.control)
                                    .background(palette.glassElevated.copy(alpha = 0.72f))
                                    .border(1.dp, palette.borderSubtle, shapes.control)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "Stream",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textSecondary,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (item.durationMs > 0L) {
                            Text(
                                text = TimeFormatters.formatDuration(item.durationMs),
                                style = MaterialTheme.typography.labelSmall.merge(
                                    TextStyle(fontFeatureSettings = "tnum"),
                                ),
                                color = palette.textSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            var anchorBounds by remember { mutableStateOf(Rect.Zero) }
            Box(
                modifier = Modifier.onGloballyPositioned { anchorBounds = it.boundsInRoot() },
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = "Item options" }
                        .clickable(role = Role.Button) { menuExpanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                GlassDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    anchorBounds = anchorBounds,
                ) {
                    DropdownMenuItem(
                        enabled = canMoveUp,
                        text = {
                            Text(
                                text = "Move up",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (canMoveUp) palette.textPrimary else palette.textSecondary.copy(alpha = 0.5f),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.ArrowUpward,
                                contentDescription = null,
                                tint = if (canMoveUp) palette.textSecondary else palette.textSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onMoveUp()
                        },
                    )
                    DropdownMenuItem(
                        enabled = canMoveDown,
                        text = {
                            Text(
                                text = "Move down",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (canMoveDown) palette.textPrimary else palette.textSecondary.copy(alpha = 0.5f),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.ArrowDownward,
                                contentDescription = null,
                                tint = if (canMoveDown) palette.textSecondary else palette.textSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onMoveDown()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Remove",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textPrimary,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = palette.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPlaylistsCard(onCreateClick: () -> Unit) {
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
                    imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "No playlists",
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
                    .clickable(role = Role.Button, onClick = onCreateClick)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Create playlist",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun CreatePlaylistFab(
    onClick: () -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .semantics { contentDescription = "Create playlist" }
            .clickable(role = Role.Button, onClick = onClick),
        hazeState = hazeState,
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
private fun PlaylistNameDialog(
    dialog: PlaylistDialogState,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val controlShape = LocalJupiterShapes.current.control
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val errorColor = if (palette.isLightCanvas) Color(0xFFB3261E) else Color(0xFFF28B82)
    val isCreate = dialog.editingPlaylistId == null

    GlassDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (isCreate) "Create playlist" else "Rename playlist",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(controlShape)
                    .background(palette.canvas.copy(alpha = 0.56f), controlShape)
                    .border(
                        width = 1.dp,
                        color = if (dialog.errorMessage != null) errorColor.copy(alpha = 0.64f) else palette.borderSubtle,
                        shape = controlShape,
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (dialog.name.isEmpty()) {
                    Text(
                        text = "Playlist name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = dialog.name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    cursorBrush = SolidColor(accent),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = palette.textPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (dialog.errorMessage != null) {
                Text(
                    text = dialog.errorMessage,
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
                        .clickable(role = Role.Button, onClick = onSubmit)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isCreate) "Create" else "Save",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = onAccent,
                    )
                }
            }
        }
    }
}

private fun formatPlaylistComposition(videoCount: Int, streamCount: Int): String {
    if (videoCount == 0 && streamCount == 0) return "Empty"
    val parts = buildList {
        if (videoCount > 0) {
            add(if (videoCount == 1) "1 video" else "$videoCount videos")
        }
        if (streamCount > 0) {
            add(if (streamCount == 1) "1 stream" else "$streamCount streams")
        }
    }
    return parts.joinToString(" · ")
}
