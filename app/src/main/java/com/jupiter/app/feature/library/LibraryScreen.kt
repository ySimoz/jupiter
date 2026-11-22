package com.jupiter.app.feature.library

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jupiter.app.core.adaptive.LocalJupiterAdaptiveInfo
import com.jupiter.app.core.adaptive.NavigationLayout
import com.jupiter.app.core.designsystem.GlassAdaptiveSheet
import com.jupiter.app.core.designsystem.GlassIconButton
import com.jupiter.app.core.designsystem.GlassPill
import com.jupiter.app.core.designsystem.GlassSurface
import com.jupiter.app.core.designsystem.JupiterColors
import com.jupiter.app.core.designsystem.LocalJupiterPalette
import com.jupiter.app.core.designsystem.LocalJupiterShapes
import com.jupiter.app.data.local.MediaHistoryEntity
import com.jupiter.app.data.media.LibrarySortOption
import com.jupiter.app.data.media.LocalFolderSummary
import com.jupiter.app.data.media.LocalVideoItem
import com.jupiter.app.feature.player.QueueMediaItem
import com.jupiter.app.feature.player.TimeFormatters
import com.jupiter.app.feature.playlists.AddToPlaylistSheet
import kotlinx.coroutines.delay
import java.util.Locale

private sealed interface LibraryPlaylistTarget {
    data class Video(val item: LocalVideoItem) : LibraryPlaylistTarget
    data class Folder(val folderName: String) : LibraryPlaylistTarget
}

@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    onOpenMedia: (uri: String, title: String) -> Unit,
    onOpenQueue: (items: List<QueueMediaItem>, startIndex: Int, sourceTitle: String) -> Unit = { items, startIndex, _ ->
        items.getOrNull(startIndex)?.let { onOpenMedia(it.uri, it.title) }
    },
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    val videoPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val displayName = resolveOpenableDisplayName(context.contentResolver, uri)
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "Video"
            onOpenMedia(uri.toString(), displayName)
        }
    }

    val openTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.addCustomFolder(uri.toString())
            viewModel.refresh()
        }
    }

    LaunchedEffect(videoPermission) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            videoPermission,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(granted)
    }

    DisposableEffect(lifecycleOwner, videoPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    videoPermission,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted != viewModel.uiState.value.hasPermission) {
                    viewModel.onPermissionResult(granted)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(
        enabled = uiState.viewMode == LibraryViewMode.FOLDERS &&
            uiState.selectedFolder != null,
    ) {
        viewModel.selectFolder(null)
    }

    LibraryContent(
        uiState = uiState,
        contentPadding = contentPadding,
        onGrantAccessClick = { permissionLauncher.launch(videoPermission) },
        onAddFolderClick = { openTreeLauncher.launch(null) },
        onOpenFileClick = { openDocumentLauncher.launch(arrayOf("video/*", "audio/*")) },
        onRefreshClick = viewModel::refresh,
        onManageFoldersClick = viewModel::openManageFolders,
        onDismissManageFolders = viewModel::dismissManageFolders,
        onToggleFolderHidden = { folderName, hidden ->
            viewModel.setFolderHidden(folderName, hidden)
        },
        onRemoveFolder = viewModel::removeFolder,
        onRemoveCustomFolder = viewModel::removeCustomFolder,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onViewModeChange = viewModel::setViewMode,
        onSortOptionChange = viewModel::setSortOption,
        onFolderClick = { viewModel.selectFolder(it) },
        onBackFromFolder = { viewModel.selectFolder(null) },
        onOpenMedia = onOpenMedia,
        onOpenQueue = onOpenQueue,
        onRemoveHistory = viewModel::removeFromHistory,
        onAddVideoToPlaylist = viewModel::addVideoToPlaylist,
        onAddFolderToPlaylist = viewModel::addFolderToPlaylist,
        onCreatePlaylistAndAddVideo = viewModel::createPlaylistAndAddVideo,
        onCreatePlaylistAndAddFolder = viewModel::createPlaylistAndAddFolder,
        onDismissFeedback = viewModel::dismissFeedback,
        modifier = modifier,
    )
}

@Composable
internal fun LibraryContent(
    uiState: LibraryUiState,
    contentPadding: PaddingValues,
    onGrantAccessClick: () -> Unit,
    onAddFolderClick: () -> Unit,
    onOpenFileClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onManageFoldersClick: () -> Unit,
    onDismissManageFolders: () -> Unit,
    onToggleFolderHidden: (folderName: String, hidden: Boolean) -> Unit,
    onRemoveFolder: (LocalFolderSummary) -> Unit,
    onRemoveCustomFolder: (treeUri: String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onSortOptionChange: (LibrarySortOption) -> Unit,
    onFolderClick: (String) -> Unit,
    onBackFromFolder: () -> Unit,
    onOpenMedia: (uri: String, title: String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onOpenQueue: (items: List<QueueMediaItem>, startIndex: Int, sourceTitle: String) -> Unit = { items, startIndex, _ ->
        items.getOrNull(startIndex)?.let { onOpenMedia(it.uri, it.title) }
    },
    onAddVideoToPlaylist: (playlistId: Long, video: LocalVideoItem) -> Unit = { _, _ -> },
    onAddFolderToPlaylist: (playlistId: Long, folderName: String) -> Unit = { _, _ -> },
    onCreatePlaylistAndAddVideo: (name: String, video: LocalVideoItem) -> Unit = { _, _ -> },
    onCreatePlaylistAndAddFolder: (name: String, folderName: String) -> Unit = { _, _ -> },
    onDismissFeedback: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalJupiterAdaptiveInfo.current
    val palette = LocalJupiterPalette.current
    var playlistTarget by remember { mutableStateOf<LibraryPlaylistTarget?>(null) }

    LaunchedEffect(uiState.feedbackMessage) {
        if (uiState.feedbackMessage != null) {
            delay(2500)
            onDismissFeedback()
        }
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
    val showLibraryGrid = uiState.hasPermission ||
        uiState.customFolders.isNotEmpty() ||
        uiState.folders.isNotEmpty() ||
        uiState.visibleVideos.isNotEmpty() ||
        uiState.isLoading

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = adaptiveInfo.gridMinColumnDp.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryHeaderRow(
                    showRefresh = uiState.hasPermission || uiState.customFolders.isNotEmpty(),
                    isLoading = uiState.isLoading,
                    onManageFoldersClick = onManageFoldersClick,
                    onRefreshClick = onRefreshClick,
                )
            }

            if (uiState.continueWatching.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ContinueWatchingSection(
                        items = uiState.continueWatching,
                        onOpenMedia = onOpenMedia,
                        onRemoveHistory = onRemoveHistory,
                    )
                }
            }

            if (!uiState.hasPermission) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PermissionPromptCard(
                        onGrantAccessClick = onGrantAccessClick,
                        onAddFolderClick = onAddFolderClick,
                        onOpenFileClick = onOpenFileClick,
                    )
                }
            }

            if (showLibraryGrid) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LibraryFilterControls(
                        searchQuery = uiState.searchQuery,
                        viewMode = uiState.viewMode,
                        sortOption = uiState.sortOption,
                        onSearchQueryChange = onSearchQueryChange,
                        onViewModeChange = onViewModeChange,
                        onSortOptionChange = onSortOptionChange,
                    )
                }

                if (uiState.viewMode == LibraryViewMode.FOLDERS && uiState.selectedFolder != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FolderBackHeader(
                            folderName = uiState.selectedFolder,
                            videoCount = uiState.visibleVideos.size,
                            onBackClick = onBackFromFolder,
                        )
                    }
                }

                val showingFolders = uiState.viewMode == LibraryViewMode.FOLDERS &&
                    uiState.selectedFolder == null
                val isEmpty = if (showingFolders) {
                    uiState.folders.isEmpty()
                } else {
                    uiState.visibleVideos.isEmpty()
                }

                if (uiState.isLoading && isEmpty) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                } else if (isEmpty) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyLibraryCard(
                            message = if (uiState.searchQuery.isNotBlank()) {
                                "No matching videos"
                            } else {
                                "No videos found"
                            },
                        )
                    }
                } else if (showingFolders) {
                    items(
                        items = uiState.folders,
                        key = { "folder_${it.treeUri ?: it.name}" },
                    ) { folder ->
                        FolderCard(
                            folder = folder,
                            onClick = { onFolderClick(folder.name) },
                            onAddToPlaylist = {
                                playlistTarget = LibraryPlaylistTarget.Folder(folder.name)
                            },
                            onHideFolder = { onToggleFolderHidden(folder.name, true) },
                            onRemoveFolder = { onRemoveFolder(folder) },
                        )
                    }
                } else {
                    itemsIndexed(
                        items = uiState.visibleVideos,
                        key = { _, it -> "video_${it.contentUri}" },
                    ) { index, video ->
                        VideoCard(
                            video = video,
                            onClick = {
                                val queueItems = uiState.visibleVideos.map { item ->
                                    QueueMediaItem(
                                        uri = item.contentUri,
                                        title = item.displayName,
                                        isNetworkStream = false,
                                        headersJson = null,
                                        durationMs = item.durationMs,
                                    )
                                }
                                onOpenQueue(
                                    queueItems,
                                    index,
                                    uiState.selectedFolder ?: "Videos",
                                )
                            },
                            onAddToPlaylist = {
                                playlistTarget = LibraryPlaylistTarget.Video(video)
                            },
                        )
                    }
                }
            }
        }

        OpenFileFab(
            onClick = onOpenFileClick,
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

        ManageFoldersSheet(
            visible = uiState.manageFoldersOpen,
            useSideSheet = adaptiveInfo.useSideSheet,
            customFolders = uiState.customFolders,
            hiddenFolders = uiState.hiddenFolders,
            hiddenFolderNames = uiState.hiddenFolderNames,
            onDismiss = onDismissManageFolders,
            onAddFolderClick = onAddFolderClick,
            onRemoveCustomFolder = onRemoveCustomFolder,
            onToggleFolderHidden = onToggleFolderHidden,
        )

        val currentTarget = playlistTarget
        AddToPlaylistSheet(
            visible = currentTarget != null,
            useSideSheet = adaptiveInfo.useSideSheet,
            playlists = uiState.playlists,
            onDismiss = { playlistTarget = null },
            onSelectPlaylist = { playlistId ->
                when (currentTarget) {
                    is LibraryPlaylistTarget.Video -> onAddVideoToPlaylist(playlistId, currentTarget.item)
                    is LibraryPlaylistTarget.Folder -> onAddFolderToPlaylist(playlistId, currentTarget.folderName)
                    null -> Unit
                }
                playlistTarget = null
            },
            onCreatePlaylistAndAdd = { name ->
                when (currentTarget) {
                    is LibraryPlaylistTarget.Video -> onCreatePlaylistAndAddVideo(name, currentTarget.item)
                    is LibraryPlaylistTarget.Folder -> onCreatePlaylistAndAddFolder(name, currentTarget.folderName)
                    null -> Unit
                }
                playlistTarget = null
            },
        )
    }
}

@Composable
private fun LibraryHeaderRow(
    showRefresh: Boolean,
    isLoading: Boolean,
    onManageFoldersClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                onClick = onManageFoldersClick,
                contentDescription = "Manage folders",
                hazeState = null,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CreateNewFolder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (showRefresh) {
                GlassIconButton(
                    onClick = onRefreshClick,
                    contentDescription = "Refresh library",
                    enabled = !isLoading,
                    hazeState = null,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionPromptCard(
    onGrantAccessClick: () -> Unit,
    onAddFolderClick: () -> Unit,
    onOpenFileClick: () -> Unit,
) {
    val shapes = LocalJupiterShapes.current
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        hazeState = null,
        shape = shapes.surface,
        elevated = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Grant video access",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(
                    label = "Allow",
                    primary = true,
                    onClick = onGrantAccessClick,
                )
                ActionButton(
                    label = "Add folder",
                    primary = false,
                    onClick = onAddFolderClick,
                )
                ActionButton(
                    label = "Open file",
                    primary = false,
                    onClick = onOpenFileClick,
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = LocalJupiterShapes.current.control
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val backgroundColor = if (primary) {
        accent
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    }
    val borderColor = if (primary) {
        accent
    } else {
        MaterialTheme.colorScheme.outline
    }
    val textColor = if (primary) {
        onAccent
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(backgroundColor, shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
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

@Composable
private fun ContinueWatchingSection(
    items: List<MediaHistoryEntity>,
    onOpenMedia: (uri: String, title: String) -> Unit,
    onRemoveHistory: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Continue watching",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = items,
                key = { it.uriHash },
            ) { entry ->
                ContinueWatchingCard(
                    entry = entry,
                    onClick = { onOpenMedia(entry.uri, entry.displayName) },
                    onRemoveClick = { onRemoveHistory(entry.uri) },
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    entry: MediaHistoryEntity,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    val context = LocalContext.current
    val progress = if (entry.durationMs > 0L) {
        (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val cardShape = LocalJupiterShapes.current.surface

    GlassSurface(
        modifier = Modifier
            .width(208.dp)
            .clip(cardShape)
            .clickable(onClick = onClick),
        hazeState = null,
        shape = cardShape,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center),
                )
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(entry.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = entry.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.56f), CircleShape)
                        .border(1.dp, JupiterColors.BorderSubtle, CircleShape)
                        .semantics { contentDescription = "Remove from continue watching" }
                        .clickable(role = Role.Button, onClick = onRemoveClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = JupiterColors.TextPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.18f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = entry.displayName.ifBlank { "Video" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val progressText = if (entry.durationMs > 0L) {
                    "${TimeFormatters.formatDuration(entry.positionMs)} / ${TimeFormatters.formatDuration(entry.durationMs)}"
                } else {
                    TimeFormatters.formatDuration(entry.positionMs)
                }
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.labelSmall.merge(
                        TextStyle(fontFeatureSettings = "tnum"),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LibraryFilterControls(
    searchQuery: String,
    viewMode: LibraryViewMode,
    sortOption: LibrarySortOption,
    onSearchQueryChange: (String) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onSortOptionChange: (LibrarySortOption) -> Unit,
) {
    val shapes = LocalJupiterShapes.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            hazeState = null,
            shape = shapes.control,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .semantics { contentDescription = "Clear search" }
                            .clickable(role = Role.Button) { onSearchQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryViewMode.entries.forEach { mode ->
                    SelectableFilterChip(
                        label = mode.label,
                        selected = viewMode == mode,
                        onClick = { onViewModeChange(mode) },
                    )
                }
            }

            SortDropdownButton(
                sortOption = sortOption,
                onSortOptionChange = onSortOptionChange,
            )
        }
    }
}

@Composable
private fun SortDropdownButton(
    sortOption: LibrarySortOption,
    onSortOptionChange: (LibrarySortOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val shapes = LocalJupiterShapes.current

    Box {
        GlassSurface(
            modifier = Modifier
                .height(40.dp)
                .clip(shapes.control)
                .semantics { contentDescription = "Sort by ${sortOption.label}" }
                .clickable(role = Role.Button) { expanded = true },
            hazeState = null,
            shape = shapes.control,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = sortOption.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = shapes.menu,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            LibrarySortOption.entries.forEach { option ->
                val selected = option == sortOption
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    trailingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSortOptionChange(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectableFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = LocalJupiterShapes.current.control
    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = if (selected) {
        accentColor.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    }
    val borderColor = if (selected) {
        accentColor.copy(alpha = 0.36f)
    } else {
        MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(bgColor, shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun FolderBackHeader(
    folderName: String,
    videoCount: Int,
    onBackClick: () -> Unit,
) {
    val chipShape = LocalJupiterShapes.current.control
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassSurface(
            modifier = Modifier
                .height(40.dp)
                .clip(chipShape)
                .clickable(role = Role.Button, onClick = onBackClick),
            hazeState = null,
            shape = chipShape,
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
                    contentDescription = "Back to folders",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = formatVideoCount(videoCount),
            style = MaterialTheme.typography.labelMedium.merge(
                TextStyle(fontFeatureSettings = "tnum"),
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FolderCard(
    folder: LocalFolderSummary,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onHideFolder: () -> Unit,
    onRemoveFolder: () -> Unit,
) {
    val context = LocalContext.current
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
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center),
                )
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(folder.previewUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = folder.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
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
                        text = folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatVideoCount(folder.videoCount),
                        style = MaterialTheme.typography.labelMedium.merge(
                            TextStyle(fontFeatureSettings = "tnum"),
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .semantics { contentDescription = "Folder options" }
                            .clickable(role = Role.Button) { menuExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        shape = shapes.menu,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Add to playlist",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Hide folder",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.VisibilityOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onHideFolder()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Remove folder",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRemoveFolder()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageFoldersSheet(
    visible: Boolean,
    useSideSheet: Boolean,
    customFolders: List<CustomFolderEntry>,
    hiddenFolders: Set<String>,
    hiddenFolderNames: List<String>,
    onDismiss: () -> Unit,
    onAddFolderClick: () -> Unit,
    onRemoveCustomFolder: (String) -> Unit,
    onToggleFolderHidden: (folderName: String, hidden: Boolean) -> Unit,
) {
    val shapes = LocalJupiterShapes.current
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    var hiddenExpanded by remember(visible) { mutableStateOf(false) }

    GlassAdaptiveSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        useSideSheet = useSideSheet,
        hazeState = null,
        title = "Manage folders",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(shapes.control)
                    .background(accent, shapes.control)
                    .clickable(role = Role.Button, onClick = onAddFolderClick)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CreateNewFolder,
                        contentDescription = null,
                        tint = onAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Add folder",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = onAccent,
                    )
                }
            }

            if (customFolders.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Added folders",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    customFolders.forEach { entry ->
                        val isHidden = entry.displayName in hiddenFolders
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = if (isHidden) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    accent
                                },
                                modifier = Modifier.size(18.dp),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = entry.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isHidden) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (isHidden) {
                                    Text(
                                        text = "Hidden",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .semantics {
                                        contentDescription = if (isHidden) {
                                            "Unhide ${entry.displayName}"
                                        } else {
                                            "Hide ${entry.displayName}"
                                        }
                                    }
                                    .clickable(role = Role.Button) {
                                        onToggleFolderHidden(entry.displayName, !isHidden)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isHidden) {
                                        Icons.Outlined.Visibility
                                    } else {
                                        Icons.Outlined.VisibilityOff
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .semantics { contentDescription = "Remove ${entry.displayName}" }
                                    .clickable(role = Role.Button) {
                                        onRemoveCustomFolder(entry.treeUri)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }

            if (hiddenFolderNames.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "No hidden folders",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                CollapsibleFolderListSection(
                    title = "Hidden folders (${hiddenFolderNames.size})",
                    leadingIcon = Icons.Outlined.VisibilityOff,
                    expanded = hiddenExpanded,
                    onToggleExpanded = { hiddenExpanded = !hiddenExpanded },
                    folderNames = hiddenFolderNames,
                    actionLabel = "Unhide",
                    actionDescription = { "Unhide $it" },
                    onAction = { onToggleFolderHidden(it, false) },
                )
            }
        }
    }
}

@Composable
private fun CollapsibleFolderListSection(
    title: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    folderNames: List<String>,
    actionLabel: String,
    actionDescription: (String) -> String,
    onAction: (String) -> Unit,
) {
    val shapes = LocalJupiterShapes.current
    val accent = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(shapes.control)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), shapes.control)
                .border(1.dp, MaterialTheme.colorScheme.outline, shapes.control)
                .clickable(role = Role.Button, onClick = onToggleExpanded)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.merge(
                        TextStyle(fontFeatureSettings = "tnum"),
                    ),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = if (expanded) {
                    Icons.Outlined.ExpandLess
                } else {
                    Icons.Outlined.ExpandMore
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        if (expanded) {
            folderNames.forEach { folderName ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(shapes.control)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                shapes.control,
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outline, shapes.control)
                            .semantics { contentDescription = actionDescription(folderName) }
                            .clickable(role = Role.Button) { onAction(folderName) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCard(
    video: LocalVideoItem,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val context = LocalContext.current
    val shapes = LocalJupiterShapes.current
    val shape = shapes.surface
    val badgeShape = RoundedCornerShape((4f + 4f * (shapes.percent / 100f)).dp)
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
                Icon(
                    imageVector = Icons.Outlined.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center),
                )
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(video.contentUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = video.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (video.durationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(badgeShape)
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = TimeFormatters.formatDuration(video.durationMs),
                            style = MaterialTheme.typography.labelSmall.merge(
                                TextStyle(fontFeatureSettings = "tnum"),
                            ),
                            fontWeight = FontWeight.Medium,
                            color = JupiterColors.TextPrimary,
                        )
                    }
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
                        text = video.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = formatVideoMetadata(video)
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall.merge(
                                TextStyle(fontFeatureSettings = "tnum"),
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .semantics { contentDescription = "Video options" }
                            .clickable(role = Role.Button) { menuExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        shape = shapes.menu,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Add to playlist",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(message: String) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        hazeState = null,
        shape = LocalJupiterShapes.current.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OpenFileFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    GlassSurface(
        modifier = modifier
            .size(56.dp)
            .clip(shape)
            .semantics { contentDescription = "Open file" }
            .clickable(role = Role.Button, onClick = onClick),
        hazeState = null,
        shape = shape,
        elevated = true,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun resolveOpenableDisplayName(
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
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }.getOrNull()
}

private fun formatVideoCount(count: Int): String {
    return if (count == 1) "1 video" else "$count videos"
}

private fun formatVideoMetadata(video: LocalVideoItem): String {
    val parts = buildList {
        val resolution = minOf(video.width, video.height).takeIf { it > 0 }
        if (resolution != null) {
            add("${resolution}p")
        }
        if (video.sizeBytes > 0L) {
            add(formatFileSize(video.sizeBytes))
        }
    }
    return parts.joinToString(" · ")
}

private fun formatFileSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.0f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}
