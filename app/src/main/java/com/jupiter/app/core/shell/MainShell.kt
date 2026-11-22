package com.jupiter.app.core.shell

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.jupiter.app.core.adaptive.JupiterAdaptiveInfo
import com.jupiter.app.core.adaptive.LocalJupiterAdaptiveInfo
import com.jupiter.app.core.adaptive.NavigationLayout
import com.jupiter.app.core.adaptive.computeAdaptiveInfo
import com.jupiter.app.core.designsystem.AmbientGlassBackground
import com.jupiter.app.core.designsystem.GlassStyle
import com.jupiter.app.core.designsystem.GlassSurface
import com.jupiter.app.core.designsystem.JupiterPalette
import com.jupiter.app.core.designsystem.JupiterTheme
import com.jupiter.app.core.designsystem.LocalGlassStyle
import com.jupiter.app.core.designsystem.LocalJupiterPalette
import com.jupiter.app.core.designsystem.LocalJupiterShapes
import com.jupiter.app.core.navigation.LibraryRoute
import com.jupiter.app.core.navigation.PlayerRoute
import com.jupiter.app.core.navigation.PlaylistsRoute
import com.jupiter.app.core.navigation.SettingsRoute
import com.jupiter.app.core.navigation.StreamsRoute
import com.jupiter.app.feature.library.LibraryScreen
import com.jupiter.app.feature.player.PlaybackQueueManager
import com.jupiter.app.feature.player.PlayerScreen
import com.jupiter.app.feature.player.QueueMediaItem
import com.jupiter.app.feature.player.QueueSourceKind
import com.jupiter.app.feature.playlists.PlaylistsScreen
import com.jupiter.app.feature.settings.SettingsScreen
import com.jupiter.app.feature.streams.StreamsScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class ExternalMediaLaunch(
    val uri: String,
    val title: String = "",
    val isNetworkStream: Boolean = false,
    val requestId: Long = System.nanoTime(),
)

private enum class TopLevelDestination(
    val label: String,
    val icon: ImageVector,
    val route: Any,
) {
    LIBRARY("Library", Icons.Outlined.VideoLibrary, LibraryRoute),
    PLAYLISTS("Playlists", Icons.AutoMirrored.Outlined.PlaylistPlay, PlaylistsRoute),
    STREAMS("Streams", Icons.Outlined.Public, StreamsRoute),
    SETTINGS("Settings", Icons.Outlined.Settings, SettingsRoute),
}

private fun NavDestination?.isDestinationSelected(destination: TopLevelDestination): Boolean {
    if (this == null) return false
    return when (destination) {
        TopLevelDestination.LIBRARY -> hasRoute<LibraryRoute>()
        TopLevelDestination.PLAYLISTS -> hasRoute<PlaylistsRoute>()
        TopLevelDestination.STREAMS -> hasRoute<StreamsRoute>()
        TopLevelDestination.SETTINGS -> hasRoute<SettingsRoute>()
    }
}

@Composable
fun MainShell(
    adaptiveInfo: JupiterAdaptiveInfo = LocalJupiterAdaptiveInfo.current,
    showNavLabels: Boolean = true,
    pendingExternalLaunch: ExternalMediaLaunch? = null,
    onExternalLaunchConsumed: () -> Unit = {},
    playbackQueueManager: PlaybackQueueManager = remember { PlaybackQueueManager() },
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val hazeState = rememberHazeState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isPlayerRoute = currentDestination?.hasRoute<PlayerRoute>() == true

    val contentPadding = rememberShellContentPadding(adaptiveInfo.navigationLayout)

    val navigateToTopLevel: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openPlayer: (String, String, Boolean, String?) -> Unit = { uri, title, isNetworkStream, headersJson ->
        playbackQueueManager.setSingle(
            QueueMediaItem(
                uri = uri,
                title = title,
                isNetworkStream = isNetworkStream,
                headersJson = headersJson,
            )
        )
        navController.navigate(
            PlayerRoute(
                mediaUri = uri,
                title = title,
                isNetworkStream = isNetworkStream,
                headersJson = headersJson,
            )
        )
    }

    val openQueue: (List<QueueMediaItem>, Int, String, QueueSourceKind) -> Unit =
        { items, startIndex, sourceTitle, sourceKind ->
            if (items.isNotEmpty()) {
                val resolvedIndex = startIndex.coerceIn(0, items.lastIndex)
                val target = items[resolvedIndex]
                playbackQueueManager.setQueue(items, resolvedIndex, sourceTitle, sourceKind)
                navController.navigate(
                    PlayerRoute(
                        mediaUri = target.uri,
                        title = target.title,
                        isNetworkStream = target.isNetworkStream,
                        headersJson = target.headersJson,
                    )
                )
            }
        }

    LaunchedEffect(pendingExternalLaunch) {
        val launch = pendingExternalLaunch ?: return@LaunchedEffect
        val isNetwork = launch.isNetworkStream ||
            launch.uri.startsWith("http://", ignoreCase = true) ||
            launch.uri.startsWith("https://", ignoreCase = true) ||
            launch.uri.startsWith("rtsp://", ignoreCase = true)
        playbackQueueManager.setSingle(
            QueueMediaItem(
                uri = launch.uri,
                title = launch.title,
                isNetworkStream = isNetwork,
            )
        )
        val currentlyInPlayer = navController.currentDestination?.hasRoute<PlayerRoute>() == true
        navController.navigate(
            PlayerRoute(
                mediaUri = launch.uri,
                title = launch.title,
                isNetworkStream = isNetwork,
            ),
        ) {
            if (currentlyInPlayer) {
                popUpTo<PlayerRoute> { inclusive = true }
            }
            launchSingleTop = true
        }
        onExternalLaunchConsumed()
    }

    CompositionLocalProvider(LocalJupiterAdaptiveInfo provides adaptiveInfo) {
        Box(modifier = modifier.fillMaxSize()) {
            val backgroundModifier = if (isPlayerRoute) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
            }
            AmbientGlassBackground(
                modifier = backgroundModifier,
            ) {
                NavHost(
                    navController = navController,
                    startDestination = LibraryRoute,
                    enterTransition = {
                        if (targetState.destination.hasRoute<PlayerRoute>() ||
                            initialState.destination.hasRoute<PlayerRoute>()
                        ) {
                            EnterTransition.None
                        } else {
                            fadeIn(animationSpec = tween(160))
                        }
                    },
                    exitTransition = {
                        if (targetState.destination.hasRoute<PlayerRoute>() ||
                            initialState.destination.hasRoute<PlayerRoute>()
                        ) {
                            ExitTransition.None
                        } else {
                            fadeOut(animationSpec = tween(160))
                        }
                    },
                    popEnterTransition = {
                        if (targetState.destination.hasRoute<PlayerRoute>() ||
                            initialState.destination.hasRoute<PlayerRoute>()
                        ) {
                            EnterTransition.None
                        } else {
                            fadeIn(animationSpec = tween(160))
                        }
                    },
                    popExitTransition = {
                        if (targetState.destination.hasRoute<PlayerRoute>() ||
                            initialState.destination.hasRoute<PlayerRoute>()
                        ) {
                            ExitTransition.None
                        } else {
                            fadeOut(animationSpec = tween(160))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable<LibraryRoute> {
                        LibraryScreen(
                            contentPadding = contentPadding,
                            onOpenMedia = { uri, title ->
                                val isNetwork = uri.startsWith("http://", ignoreCase = true) ||
                                    uri.startsWith("https://", ignoreCase = true) ||
                                    uri.startsWith("rtsp://", ignoreCase = true)
                                openPlayer(uri, title, isNetwork, null)
                            },
                            onOpenQueue = { items, startIndex, sourceTitle ->
                                openQueue(items, startIndex, sourceTitle, QueueSourceKind.FOLDER)
                            },
                        )
                    }
                    composable<PlaylistsRoute> {
                        PlaylistsScreen(
                            contentPadding = contentPadding,
                            onOpenQueue = { items, startIndex, sourceTitle ->
                                openQueue(items, startIndex, sourceTitle, QueueSourceKind.PLAYLIST)
                            },
                        )
                    }
                    composable<StreamsRoute> {
                        StreamsScreen(
                            contentPadding = contentPadding,
                            onOpenStream = { request ->
                                openPlayer(request.url, request.title, true, request.headersJson)
                            },
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            contentPadding = contentPadding,
                        )
                    }
                    composable<PlayerRoute>(
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None },
                    ) { entry ->
                        val route = entry.toRoute<PlayerRoute>()
                        PlayerScreen(
                            mediaUri = route.mediaUri,
                            title = route.title,
                            isNetworkStream = route.isNetworkStream,
                            headersJson = route.headersJson,
                            onNavigateBack = {
                                if (navController.currentDestination?.hasRoute<PlayerRoute>() == true) {
                                    navController.popBackStack()
                                }
                            },
                        )
                    }
                }
            }

            if (!isPlayerRoute) {
                when (adaptiveInfo.navigationLayout) {
                    NavigationLayout.BOTTOM_BAR -> {
                        BottomGlassNavigationBar(
                            currentDestination = currentDestination,
                            hazeState = hazeState,
                            showNavLabels = showNavLabels,
                            onSelect = navigateToTopLevel,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                        )
                    }

                    NavigationLayout.NAVIGATION_RAIL -> {
                        SideGlassNavigationRail(
                            currentDestination = currentDestination,
                            hazeState = hazeState,
                            expanded = false,
                            showNavLabels = showNavLabels,
                            onSelect = navigateToTopLevel,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                        )
                    }

                    NavigationLayout.PERMANENT_RAIL -> {
                        SideGlassNavigationRail(
                            currentDestination = currentDestination,
                            hazeState = hazeState,
                            expanded = true,
                            showNavLabels = showNavLabels,
                            onSelect = navigateToTopLevel,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberShellContentPadding(navigationLayout: NavigationLayout): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    val statusInsets = WindowInsets.statusBars.asPaddingValues()
    val navInsets = WindowInsets.navigationBars.asPaddingValues()

    val startInset = navInsets.calculateStartPadding(layoutDirection)
    val endInset = navInsets.calculateEndPadding(layoutDirection)
    val topInset = statusInsets.calculateTopPadding()
    val bottomInset = navInsets.calculateBottomPadding()

    return when (navigationLayout) {
        NavigationLayout.BOTTOM_BAR -> PaddingValues(
            start = startInset + 16.dp,
            top = topInset + 16.dp,
            end = endInset + 16.dp,
            bottom = bottomInset + 104.dp,
        )

        NavigationLayout.NAVIGATION_RAIL -> PaddingValues(
            start = startInset + 112.dp,
            top = topInset + 16.dp,
            end = endInset + 16.dp,
            bottom = bottomInset + 16.dp,
        )

        NavigationLayout.PERMANENT_RAIL -> PaddingValues(
            start = startInset + 236.dp,
            top = topInset + 16.dp,
            end = endInset + 16.dp,
            bottom = bottomInset + 16.dp,
        )
    }
}

private class NavBubbleState(
    val itemBounds: SnapshotStateMap<TopLevelDestination, Rect>,
    val left: Animatable<Float, AnimationVector1D>,
    val top: Animatable<Float, AnimationVector1D>,
    val width: Animatable<Float, AnimationVector1D>,
    val height: Animatable<Float, AnimationVector1D>,
) {
    var lastAnimatedDestination: TopLevelDestination? = null

    val isAnimating: Boolean
        get() = left.isRunning || top.isRunning || width.isRunning || height.isRunning
}

@Composable
private fun rememberNavBubbleState(
    selectedDestination: TopLevelDestination?,
    layoutKey: Any = Unit,
): NavBubbleState {
    val state = remember(layoutKey) {
        NavBubbleState(
            itemBounds = mutableStateMapOf(),
            left = Animatable(0f),
            top = Animatable(0f),
            width = Animatable(0f),
            height = Animatable(0f),
        )
    }

    val targetBounds = selectedDestination?.let { state.itemBounds[it] }

    LaunchedEffect(state, selectedDestination, targetBounds) {
        if (selectedDestination == null || targetBounds == null) return@LaunchedEffect
        val isInitialOrSameDestination =
            state.lastAnimatedDestination == null || state.lastAnimatedDestination == selectedDestination
        state.lastAnimatedDestination = selectedDestination

        if (isInitialOrSameDestination) {
            state.left.snapTo(targetBounds.left)
            state.top.snapTo(targetBounds.top)
            state.width.snapTo(targetBounds.width)
            state.height.snapTo(targetBounds.height)
        } else {
            val bubbleSpring = spring<Float>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 380f,
            )
            coroutineScope {
                launch { state.left.animateTo(targetBounds.left, bubbleSpring) }
                launch { state.top.animateTo(targetBounds.top, bubbleSpring) }
                launch { state.width.animateTo(targetBounds.width, bubbleSpring) }
                launch { state.height.animateTo(targetBounds.height, bubbleSpring) }
            }
        }
    }

    return state
}

private fun DrawScope.drawNavGlassBubble(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    cornerShape: CornerBasedShape,
    glassStyle: GlassStyle,
    palette: JupiterPalette,
    accentColor: Color,
) {
    if (width <= 0f || height <= 0f) return

    val bubbleSize = Size(width, height)
    val topLeft = Offset(left, top)
    val radius = cornerShape.topStart.toPx(bubbleSize, this)
        .coerceIn(0f, minOf(width, height) / 2f)
    val cornerRadius = CornerRadius(radius, radius)

    val strokeWidth = 1.dp.toPx()
    val halfStroke = strokeWidth / 2f
    val borderTopLeft = Offset(left + halfStroke, top + halfStroke)
    val borderSize = Size(
        width = (width - strokeWidth).coerceAtLeast(0f),
        height = (height - strokeWidth).coerceAtLeast(0f),
    )
    val borderRadius = (radius - halfStroke).coerceAtLeast(0f)
    val borderCornerRadius = CornerRadius(borderRadius, borderRadius)

    when (glassStyle) {
        GlassStyle.SOLID -> {
            drawRoundRect(
                color = accentColor.copy(alpha = 0.18f),
                topLeft = topLeft,
                size = bubbleSize,
                cornerRadius = cornerRadius,
            )
            drawRoundRect(
                color = accentColor.copy(alpha = 0.32f),
                topLeft = borderTopLeft,
                size = borderSize,
                cornerRadius = borderCornerRadius,
                style = Stroke(width = strokeWidth),
            )
        }

        GlassStyle.FROSTED -> {
            drawRoundRect(
                color = accentColor.copy(alpha = 0.18f),
                topLeft = topLeft,
                size = bubbleSize,
                cornerRadius = cornerRadius,
            )
            val sheenBrush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (palette.isLightCanvas) 0.36f else 0.18f),
                    Color.White.copy(alpha = 0.02f),
                ),
                startY = top,
                endY = top + height,
            )
            drawRoundRect(
                brush = sheenBrush,
                topLeft = topLeft,
                size = bubbleSize,
                cornerRadius = cornerRadius,
            )
            val borderBrush = Brush.verticalGradient(
                colors = if (palette.isLightCanvas) {
                    listOf(
                        Color.White.copy(alpha = 0.52f),
                        accentColor.copy(alpha = 0.24f),
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.08f),
                    )
                },
                startY = top,
                endY = top + height,
            )
            drawRoundRect(
                brush = borderBrush,
                topLeft = borderTopLeft,
                size = borderSize,
                cornerRadius = borderCornerRadius,
                style = Stroke(width = strokeWidth),
            )
        }

        GlassStyle.CLEAR -> {
            drawRoundRect(
                color = accentColor.copy(alpha = 0.14f),
                topLeft = topLeft,
                size = bubbleSize,
                cornerRadius = cornerRadius,
            )
            val glintBrush = Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = if (palette.isLightCanvas) 0.52f else 0.30f),
                0.28f to Color.White.copy(alpha = if (palette.isLightCanvas) 0.04f else 0.03f),
                0.55f to Color.Transparent,
                1.0f to Color.Transparent,
                startY = top,
                endY = top + height,
            )
            drawRoundRect(
                brush = glintBrush,
                topLeft = topLeft,
                size = bubbleSize,
                cornerRadius = cornerRadius,
            )
            val borderBrush = Brush.verticalGradient(
                colors = if (palette.isLightCanvas) {
                    listOf(
                        Color.White.copy(alpha = 0.78f),
                        Color(0xFF090C12).copy(alpha = 0.16f),
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = 0.48f),
                        Color.White.copy(alpha = 0.12f),
                    )
                },
                startY = top,
                endY = top + height,
            )
            drawRoundRect(
                brush = borderBrush,
                topLeft = borderTopLeft,
                size = borderSize,
                cornerRadius = borderCornerRadius,
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

private fun NavBubbleState.resolveDrawBounds(selectedDestination: TopLevelDestination?): Rect? {
    if (selectedDestination == null) return null
    val targetBounds = itemBounds[selectedDestination]
    if (lastAnimatedDestination == null || (lastAnimatedDestination == selectedDestination && !isAnimating)) {
        return targetBounds
    }
    return Rect(
        left = left.value,
        top = top.value,
        right = left.value + width.value,
        bottom = top.value + height.value,
    )
}

@Composable
private fun BottomGlassNavigationBar(
    currentDestination: NavDestination?,
    hazeState: HazeState,
    showNavLabels: Boolean,
    onSelect: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shapes = LocalJupiterShapes.current
    val palette = LocalJupiterPalette.current
    val glassStyle = LocalGlassStyle.current
    val accentColor = MaterialTheme.colorScheme.primary
    val selectedDestination = TopLevelDestination.entries.firstOrNull {
        currentDestination.isDestinationSelected(it)
    }
    val bubbleState = rememberNavBubbleState(
        selectedDestination = selectedDestination,
        layoutKey = showNavLabels,
    )

    GlassSurface(
        modifier = modifier,
        hazeState = hazeState,
        shape = shapes.navBar,
        elevated = true,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .drawBehind {
                    val bounds = bubbleState.resolveDrawBounds(selectedDestination) ?: return@drawBehind
                    drawNavGlassBubble(
                        left = bounds.left,
                        top = bounds.top,
                        width = bounds.width,
                        height = bounds.height,
                        cornerShape = shapes.control,
                        glassStyle = glassStyle,
                        palette = palette,
                        accentColor = accentColor,
                    )
                },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (showNavLabels) 4.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopLevelDestination.entries.forEach { destination ->
                    val isSelected = destination == selectedDestination
                    val itemShape = shapes.control
                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) accentColor else palette.textSecondary,
                        animationSpec = tween(durationMillis = 180),
                        label = "bottomNavIconTint_${destination.name}",
                    )
                    val labelColor by animateColorAsState(
                        targetValue = if (isSelected) palette.textPrimary else palette.textSecondary,
                        animationSpec = tween(durationMillis = 180),
                        label = "bottomNavLabelColor_${destination.name}",
                    )
                    val boundsModifier = Modifier.onPlaced { coords ->
                        val pos = coords.positionInParent()
                        val rect = Rect(
                            pos.x,
                            pos.y,
                            pos.x + coords.size.width,
                            pos.y + coords.size.height,
                        )
                        if (bubbleState.itemBounds[destination] != rect) {
                            bubbleState.itemBounds[destination] = rect
                        }
                    }

                    if (!showNavLabels || destination == TopLevelDestination.SETTINGS) {
                        val itemSizeModifier = if (!showNavLabels) {
                            boundsModifier
                                .width(64.dp)
                                .height(52.dp)
                        } else {
                            boundsModifier
                                .width(48.dp)
                                .height(52.dp)
                        }
                        NavIconOnlyButton(
                            destination = destination,
                            isSelected = isSelected,
                            iconTint = iconTint,
                            itemShape = itemShape,
                            onSelect = onSelect,
                            modifier = itemSizeModifier,
                            iconSize = if (!showNavLabels) 24.dp else 18.dp,
                        )
                    } else {
                        Column(
                            modifier = boundsModifier
                                .height(52.dp)
                                .widthIn(min = 68.dp)
                                .clip(itemShape)
                                .semantics {
                                    selected = isSelected
                                    contentDescription = destination.label
                                }
                                .clickable(
                                    role = Role.Tab,
                                    onClick = { onSelect(destination.route) },
                                )
                                .padding(horizontal = 10.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = labelColor,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideGlassNavigationRail(
    currentDestination: NavDestination?,
    hazeState: HazeState,
    expanded: Boolean,
    showNavLabels: Boolean,
    onSelect: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val railWidth = if (expanded) 204.dp else 80.dp
    val shapes = LocalJupiterShapes.current
    val palette = LocalJupiterPalette.current
    val glassStyle = LocalGlassStyle.current
    val accentColor = MaterialTheme.colorScheme.primary
    val selectedDestination = TopLevelDestination.entries.firstOrNull {
        currentDestination.isDestinationSelected(it)
    }
    val bubbleState = rememberNavBubbleState(
        selectedDestination = selectedDestination,
        layoutKey = expanded to showNavLabels,
    )

    GlassSurface(
        modifier = modifier.width(railWidth),
        hazeState = hazeState,
        shape = shapes.navBar,
        elevated = true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 20.dp)
                .drawBehind {
                    val bounds = bubbleState.resolveDrawBounds(selectedDestination) ?: return@drawBehind
                    drawNavGlassBubble(
                        left = bounds.left,
                        top = bounds.top,
                        width = bounds.width,
                        height = bounds.height,
                        cornerShape = shapes.control,
                        glassStyle = glassStyle,
                        palette = palette,
                        accentColor = accentColor,
                    )
                },
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                if (expanded) {
                    Text(
                        text = "Jupiter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                TopLevelDestination.entries.forEach { destination ->
                    val isSelected = destination == selectedDestination
                    val itemShape = shapes.control
                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) accentColor else palette.textSecondary,
                        animationSpec = tween(durationMillis = 180),
                        label = "railNavIconTint_${destination.name}",
                    )
                    val labelColor by animateColorAsState(
                        targetValue = if (isSelected) palette.textPrimary else palette.textSecondary,
                        animationSpec = tween(durationMillis = 180),
                        label = "railNavLabelColor_${destination.name}",
                    )
                    val boundsModifier = Modifier.onPlaced { coords ->
                        val pos = coords.positionInParent()
                        val rect = Rect(
                            pos.x,
                            pos.y,
                            pos.x + coords.size.width,
                            pos.y + coords.size.height,
                        )
                        if (bubbleState.itemBounds[destination] != rect) {
                            bubbleState.itemBounds[destination] = rect
                        }
                    }

                    if (expanded) {
                        Row(
                            modifier = boundsModifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(itemShape)
                                .semantics {
                                    selected = isSelected
                                    contentDescription = destination.label
                                }
                                .clickable(
                                    role = Role.Tab,
                                    onClick = { onSelect(destination.route) },
                                )
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = labelColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else if (!showNavLabels) {
                        NavIconOnlyButton(
                            destination = destination,
                            isSelected = isSelected,
                            iconTint = iconTint,
                            itemShape = itemShape,
                            onSelect = onSelect,
                            modifier = boundsModifier
                                .width(56.dp)
                                .height(52.dp),
                            iconSize = 24.dp,
                        )
                    } else {
                        Column(
                            modifier = boundsModifier
                                .width(56.dp)
                                .height(52.dp)
                                .clip(itemShape)
                                .semantics {
                                    selected = isSelected
                                    contentDescription = destination.label
                                }
                                .clickable(
                                    role = Role.Tab,
                                    onClick = { onSelect(destination.route) },
                                ),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = labelColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavIconOnlyButton(
    destination: TopLevelDestination,
    isSelected: Boolean,
    iconTint: Color,
    itemShape: CornerBasedShape,
    onSelect: (Any) -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
) {
    Box(
        modifier = modifier
            .clip(itemShape)
            .semantics {
                selected = isSelected
                contentDescription = destination.label
            }
            .clickable(
                role = Role.Tab,
                onClick = { onSelect(destination.route) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Preview(
    name = "Phone Portrait",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun MainShellPhonePortraitPreview() {
    JupiterTheme {
        MainShell(
            adaptiveInfo = computeAdaptiveInfo(
                widthSizeClass = WindowWidthSizeClass.Compact,
                heightSizeClass = WindowHeightSizeClass.Expanded,
            ),
        )
    }
}

@Preview(
    name = "Phone Landscape",
    widthDp = 915,
    heightDp = 412,
    showBackground = true,
)
@Composable
private fun MainShellPhoneLandscapePreview() {
    JupiterTheme {
        MainShell(
            adaptiveInfo = computeAdaptiveInfo(
                widthSizeClass = WindowWidthSizeClass.Expanded,
                heightSizeClass = WindowHeightSizeClass.Compact,
            ),
        )
    }
}

@Preview(
    name = "Tablet",
    widthDp = 1280,
    heightDp = 800,
    showBackground = true,
)
@Composable
private fun MainShellTabletPreview() {
    JupiterTheme {
        MainShell(
            adaptiveInfo = computeAdaptiveInfo(
                widthSizeClass = WindowWidthSizeClass.Expanded,
                heightSizeClass = WindowHeightSizeClass.Expanded,
            ),
        )
    }
}
