package com.jupiter.app.core.shell

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
import com.jupiter.app.core.designsystem.GlassSurface
import com.jupiter.app.core.designsystem.JupiterTheme
import com.jupiter.app.core.designsystem.LocalJupiterPalette
import com.jupiter.app.core.designsystem.LocalJupiterShapes
import com.jupiter.app.core.navigation.LibraryRoute
import com.jupiter.app.core.navigation.PlayerRoute
import com.jupiter.app.core.navigation.SettingsRoute
import com.jupiter.app.core.navigation.StreamsRoute
import com.jupiter.app.feature.library.LibraryScreen
import com.jupiter.app.feature.player.PlayerScreen
import com.jupiter.app.feature.settings.SettingsScreen
import com.jupiter.app.feature.streams.StreamsScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

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
    STREAMS("Streams", Icons.Outlined.Public, StreamsRoute),
    SETTINGS("Settings", Icons.Outlined.Settings, SettingsRoute),
}

private fun NavDestination?.isDestinationSelected(destination: TopLevelDestination): Boolean {
    if (this == null) return false
    return when (destination) {
        TopLevelDestination.LIBRARY -> hasRoute<LibraryRoute>()
        TopLevelDestination.STREAMS -> hasRoute<StreamsRoute>()
        TopLevelDestination.SETTINGS -> hasRoute<SettingsRoute>()
    }
}

@Composable
fun MainShell(
    adaptiveInfo: JupiterAdaptiveInfo = LocalJupiterAdaptiveInfo.current,
    pendingExternalLaunch: ExternalMediaLaunch? = null,
    onExternalLaunchConsumed: () -> Unit = {},
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
        navController.navigate(
            PlayerRoute(
                mediaUri = uri,
                title = title,
                isNetworkStream = isNetworkStream,
                headersJson = headersJson,
            )
        )
    }

    LaunchedEffect(pendingExternalLaunch) {
        val launch = pendingExternalLaunch ?: return@LaunchedEffect
        val isNetwork = launch.isNetworkStream ||
            launch.uri.startsWith("http://", ignoreCase = true) ||
            launch.uri.startsWith("https://", ignoreCase = true) ||
            launch.uri.startsWith("rtsp://", ignoreCase = true)
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
                            onSelect = navigateToTopLevel,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                    }

                    NavigationLayout.NAVIGATION_RAIL -> {
                        SideGlassNavigationRail(
                            currentDestination = currentDestination,
                            hazeState = hazeState,
                            expanded = false,
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

@Composable
private fun BottomGlassNavigationBar(
    currentDestination: NavDestination?,
    hazeState: HazeState,
    onSelect: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shapes = LocalJupiterShapes.current
    val palette = LocalJupiterPalette.current

    GlassSurface(
        modifier = modifier,
        hazeState = hazeState,
        shape = shapes.navBar,
        elevated = true,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val isSelected = currentDestination.isDestinationSelected(destination)
                val accentColor = MaterialTheme.colorScheme.primary
                val contentColor = if (isSelected) accentColor else palette.textSecondary
                val itemBackground = if (isSelected) {
                    accentColor.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                }
                val itemBorder = if (isSelected) {
                    accentColor.copy(alpha = 0.28f)
                } else {
                    Color.Transparent
                }
                val itemShape = shapes.control

                if (destination == TopLevelDestination.SETTINGS) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(itemShape)
                            .background(itemBackground, itemShape)
                            .border(width = 1.dp, color = itemBorder, shape = itemShape)
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
                            tint = contentColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(itemShape)
                            .background(itemBackground, itemShape)
                            .border(width = 1.dp, color = itemBorder, shape = itemShape)
                            .semantics {
                                selected = isSelected
                                contentDescription = destination.label
                            }
                            .clickable(
                                role = Role.Tab,
                                onClick = { onSelect(destination.route) },
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) palette.textPrimary else contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
    onSelect: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val railWidth = if (expanded) 204.dp else 80.dp
    val shapes = LocalJupiterShapes.current
    val palette = LocalJupiterPalette.current

    GlassSurface(
        modifier = modifier.width(railWidth),
        hazeState = hazeState,
        shape = shapes.navBar,
        elevated = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 20.dp),
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
                val isSelected = currentDestination.isDestinationSelected(destination)
                val accentColor = MaterialTheme.colorScheme.primary
                val iconColor = if (isSelected) accentColor else palette.textSecondary
                val itemBackground = if (isSelected) {
                    accentColor.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                }
                val itemBorder = if (isSelected) {
                    accentColor.copy(alpha = 0.28f)
                } else {
                    Color.Transparent
                }
                val itemShape = shapes.control

                if (expanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(itemShape)
                            .background(itemBackground, itemShape)
                            .border(width = 1.dp, color = itemBorder, shape = itemShape)
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
                            tint = iconColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) palette.textPrimary else palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .width(56.dp)
                            .height(52.dp)
                            .clip(itemShape)
                            .background(itemBackground, itemShape)
                            .border(width = 1.dp, color = itemBorder, shape = itemShape)
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
                            tint = iconColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) palette.textPrimary else palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
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
