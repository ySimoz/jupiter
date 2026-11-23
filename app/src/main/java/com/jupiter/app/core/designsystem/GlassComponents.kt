package com.jupiter.app.core.designsystem

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.roundToInt

internal class GlassOverlayEntry(
    val id: Long,
    initialContent: @Composable () -> Unit,
) {
    var content: @Composable () -> Unit by mutableStateOf(initialContent)
}

class GlassOverlayHostState(
    val hazeState: HazeState,
) {
    private var nextEntryId = 1L
    internal val entries: SnapshotStateList<GlassOverlayEntry> = mutableStateListOf()

    internal fun nextId(): Long = nextEntryId++

    internal fun add(entry: GlassOverlayEntry) {
        if (entries.none { it.id == entry.id }) {
            entries.add(entry)
        }
    }

    internal fun remove(entry: GlassOverlayEntry) {
        entries.removeAll { it.id == entry.id }
    }
}

val LocalGlassOverlayHost = staticCompositionLocalOf<GlassOverlayHostState?> { null }

@Composable
fun GlassOverlayHostLayer(
    host: GlassOverlayHostState,
    modifier: Modifier = Modifier,
) {
    if (host.entries.isEmpty()) return
    Box(modifier = modifier.fillMaxSize()) {
        host.entries.forEach { entry ->
            key(entry.id) {
                entry.content()
            }
        }
    }
}

@Composable
internal fun GlassOverlayPortal(
    host: GlassOverlayHostState,
    content: @Composable () -> Unit,
) {
    val entry = remember(host) {
        GlassOverlayEntry(id = host.nextId(), initialContent = content)
    }
    entry.content = content
    DisposableEffect(host, entry) {
        host.add(entry)
        onDispose { host.remove(entry) }
    }
}

private fun createGlassSheenBrush(
    glassStyle: GlassStyle,
    isFloating: Boolean,
    isLightCanvas: Boolean,
): Brush? = when (glassStyle) {
    GlassStyle.SOLID -> null
    GlassStyle.FROSTED -> {
        val (topAlpha, bottomAlpha) = when {
            isFloating && isLightCanvas -> 0.28f to 0.08f
            isFloating -> 0.12f to 0.03f
            isLightCanvas -> 0.22f to 0.05f
            else -> 0.09f to 0.02f
        }
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = topAlpha),
                Color.White.copy(alpha = bottomAlpha),
            ),
        )
    }
    GlassStyle.CLEAR -> if (isFloating) {
        val topAlpha = if (isLightCanvas) 0.42f else 0.22f
        val midAlpha = if (isLightCanvas) 0.08f else 0.02f
        Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = topAlpha),
            0.26f to Color.White.copy(alpha = midAlpha),
            0.50f to Color.Transparent,
            1.0f to Color.Transparent,
        )
    } else {
        val topAlpha = if (isLightCanvas) 0.34f else 0.16f
        Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = topAlpha),
            0.48f to Color.Transparent,
            1.0f to Color.Transparent,
        )
    }
}

private fun glassStyleBorderBrush(
    glassStyle: GlassStyle,
    palette: JupiterPalette,
): Brush? = when (glassStyle) {
    GlassStyle.SOLID -> null
    GlassStyle.FROSTED -> palette.frostedBorderBrush()
    GlassStyle.CLEAR -> palette.clearBorderBrush()
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    shape: Shape = LocalJupiterShapes.current.surface,
    elevated: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val glassStyle = LocalGlassStyle.current
    val baseColor = if (elevated) palette.glassElevated else palette.glassPrimary
    val supportsLiveBlur = glassStyle == GlassStyle.FROSTED &&
        hazeState != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val sheenBrush = remember(glassStyle, hazeState != null, palette.isLightCanvas) {
        createGlassSheenBrush(
            glassStyle = glassStyle,
            isFloating = hazeState != null,
            isLightCanvas = palette.isLightCanvas,
        )
    }
    val borderBrush = remember(glassStyle, palette) {
        glassStyleBorderBrush(glassStyle = glassStyle, palette = palette)
    }

    val surfaceModifier = modifier
        .clip(shape)
        .then(
            when {
                glassStyle == GlassStyle.SOLID -> {
                    Modifier.background(baseColor, shape)
                }

                supportsLiveBlur -> {
                    val liveTintAlpha = if (elevated) 0.60f else 0.54f
                    val fallbackAlpha = if (elevated) 0.84f else 0.78f
                    Modifier
                        .hazeEffect(
                            state = hazeState,
                            style = HazeStyle(
                                backgroundColor = palette.canvas,
                                tints = listOf(HazeTint(baseColor.copy(alpha = liveTintAlpha))),
                                blurRadius = 28.dp,
                                fallbackTint = HazeTint(baseColor.copy(alpha = fallbackAlpha)),
                            ),
                        )
                        .let { mod -> if (sheenBrush != null) mod.background(sheenBrush, shape) else mod }
                }

                glassStyle == GlassStyle.FROSTED -> {
                    val translucentAlpha = if (elevated) 0.84f else 0.78f
                    Modifier
                        .background(baseColor.copy(alpha = translucentAlpha), shape)
                        .let { mod -> if (sheenBrush != null) mod.background(sheenBrush, shape) else mod }
                }

                else -> {
                    // GlassStyle.CLEAR: crystal-clear transparent pane without blur.
                    val clearAlpha = when {
                        hazeState != null -> if (elevated) 0.28f else 0.22f
                        elevated -> 0.34f
                        else -> 0.26f
                    }
                    Modifier
                        .background(baseColor.copy(alpha = clearAlpha), shape)
                        .let { mod -> if (sheenBrush != null) mod.background(sheenBrush, shape) else mod }
                }
            },
        )
        .then(
            if (borderBrush == null) {
                Modifier.border(
                    width = 1.dp,
                    color = palette.borderSubtle,
                    shape = shape,
                )
            } else {
                Modifier.border(
                    width = 1.dp,
                    brush = borderBrush,
                    shape = shape,
                )
            },
        )

    Box(modifier = surfaceModifier) {
        CompositionLocalProvider(LocalContentColor provides palette.textPrimary) {
            content()
        }
    }
}

@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    elevated: Boolean = false,
    shape: Shape = LocalJupiterShapes.current.control,
    content: @Composable RowScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        hazeState = hazeState,
        shape = shape,
        elevated = elevated,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val glassStyle = LocalGlassStyle.current
    val accentColor = MaterialTheme.colorScheme.primary
    val supportsLiveBlur = glassStyle == GlassStyle.FROSTED &&
        hazeState != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val sheenBrush = remember(glassStyle, hazeState != null, palette.isLightCanvas) {
        createGlassSheenBrush(
            glassStyle = glassStyle,
            isFloating = hazeState != null,
            isLightCanvas = palette.isLightCanvas,
        )
    }
    val borderBrush = remember(glassStyle, palette) {
        glassStyleBorderBrush(glassStyle = glassStyle, palette = palette)
    }
    val targetBackground = when (glassStyle) {
        GlassStyle.SOLID -> when {
            !enabled -> palette.glassPrimary
            selected -> lerp(palette.glassPrimary, accentColor, 0.22f)
            else -> palette.glassPrimary
        }

        GlassStyle.FROSTED -> if (supportsLiveBlur) {
            when {
                !enabled -> palette.glassPrimary.copy(alpha = 0.46f)
                selected -> lerp(palette.glassPrimary, accentColor, 0.22f).copy(alpha = 0.60f)
                else -> palette.glassPrimary.copy(alpha = 0.54f)
            }
        } else {
            when {
                !enabled -> palette.glassPrimary.copy(alpha = 0.72f)
                selected -> lerp(palette.glassPrimary, accentColor, 0.22f).copy(alpha = 0.84f)
                else -> palette.glassPrimary.copy(alpha = 0.78f)
            }
        }

        GlassStyle.CLEAR -> if (hazeState != null) {
            when {
                !enabled -> palette.glassPrimary.copy(alpha = 0.18f)
                selected -> lerp(palette.glassPrimary, accentColor, 0.22f).copy(alpha = 0.34f)
                else -> palette.glassPrimary.copy(alpha = 0.22f)
            }
        } else {
            when {
                !enabled -> palette.glassPrimary.copy(alpha = 0.20f)
                selected -> lerp(palette.glassPrimary, accentColor, 0.22f).copy(alpha = 0.36f)
                else -> palette.glassPrimary.copy(alpha = 0.26f)
            }
        }
    }
    val targetBorder = when {
        !enabled -> palette.borderSubtle.copy(alpha = 0.04f)
        selected -> accentColor.copy(alpha = 0.48f)
        glassStyle == GlassStyle.SOLID -> palette.borderSubtle
        else -> palette.borderSpecularTop
    }
    val targetContentColor = when {
        !enabled -> palette.textSecondary.copy(alpha = 0.45f)
        selected -> accentColor
        else -> palette.textPrimary
    }

    val backgroundColor by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 150),
        label = "glassIconButtonBg",
    )
    val borderColor by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = tween(durationMillis = 150),
        label = "glassIconButtonBorder",
    )
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = tween(durationMillis = 150),
        label = "glassIconButtonContent",
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(
                if (supportsLiveBlur) {
                    Modifier
                        .hazeEffect(
                            state = hazeState,
                            style = HazeStyle(
                                backgroundColor = palette.canvas,
                                tints = listOf(HazeTint(backgroundColor)),
                                blurRadius = 28.dp,
                                fallbackTint = HazeTint(palette.glassPrimary.copy(alpha = 0.78f)),
                            ),
                        )
                        .let { mod -> if (sheenBrush != null) mod.background(sheenBrush, CircleShape) else mod }
                } else {
                    Modifier
                        .background(backgroundColor, CircleShape)
                        .let { mod -> if (sheenBrush != null) mod.background(sheenBrush, CircleShape) else mod }
                },
            )
            .then(
                if (borderBrush != null && enabled && !selected) {
                    Modifier.border(
                        width = 1.dp,
                        brush = borderBrush,
                        shape = CircleShape,
                    )
                } else {
                    Modifier.border(width = 1.dp, color = borderColor, shape = CircleShape)
                },
            )
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
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            icon()
        }
    }
}

@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: Rect,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val overlayHost = LocalGlassOverlayHost.current
    val palette = LocalJupiterPalette.current
    val shapes = LocalJupiterShapes.current

    if (overlayHost == null) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            containerColor = palette.glassElevated,
            shape = shapes.menu,
            border = BorderStroke(1.dp, palette.borderSubtle),
            modifier = modifier,
            content = content,
        )
        return
    }

    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = expanded
    if (!transitionState.currentState && !transitionState.targetState) return

    val density = LocalDensity.current
    val marginPx = with(density) { 12.dp.roundToPx() }
    val verticalGapPx = with(density) { 6.dp.roundToPx() }

    GlassOverlayPortal(host = overlayHost) {
        BackHandler(enabled = expanded, onBack = onDismissRequest)
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        var menuSize by remember { mutableStateOf(IntSize.Zero) }
        val scrimInteraction = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )

            val menuOffset = if (containerSize != IntSize.Zero && menuSize != IntSize.Zero) {
                computeDropdownMenuOffset(
                    anchorBounds = anchorBounds,
                    menuSize = menuSize,
                    containerSize = containerSize,
                    marginPx = marginPx,
                    verticalGapPx = verticalGapPx,
                )
            } else {
                IntOffset.Zero
            }

            Box(
                modifier = Modifier
                    .offset { menuOffset }
                    .onSizeChanged { menuSize = it }
                    .alpha(if (menuSize == IntSize.Zero) 0f else 1f),
            ) {
                AnimatedVisibility(
                    visibleState = transitionState,
                    enter = fadeIn(animationSpec = tween(durationMillis = 120)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 100)),
                ) {
                    GlassSurface(
                        modifier = modifier
                            .widthIn(min = 168.dp, max = 260.dp)
                            .pointerInput(Unit) { detectTapGestures { } },
                        hazeState = overlayHost.hazeState,
                        shape = shapes.menu,
                        elevated = true,
                    ) {
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .padding(vertical = 6.dp),
                            content = content,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val overlayHost = LocalGlassOverlayHost.current
    if (overlayHost == null) {
        // Outside MainShell overlay host (e.g. PlayerRoute over SurfaceView or standalone previews).
        Dialog(onDismissRequest = onDismissRequest) {
            GlassSurface(
                modifier = modifier.fillMaxWidth(),
                hazeState = null,
                shape = LocalJupiterShapes.current.surface,
                elevated = true,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    content = content,
                )
            }
        }
        return
    }

    GlassOverlayPortal(host = overlayHost) {
        BackHandler(enabled = true, onBack = onDismissRequest)
        val scrimInteraction = remember { MutableInteractionSource() }
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f))
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                GlassSurface(
                    modifier = modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) { detectTapGestures { } },
                    hazeState = overlayHost.hazeState,
                    shape = LocalJupiterShapes.current.surface,
                    elevated = true,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassAdaptiveSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    useSideSheet: Boolean,
    hazeState: HazeState? = null,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val overlayHost = LocalGlassOverlayHost.current

    if (overlayHost != null) {
        val transitionState = remember { MutableTransitionState(false) }
        transitionState.targetState = visible
        if (!transitionState.currentState && !transitionState.targetState) return

        GlassOverlayPortal(host = overlayHost) {
            BackHandler(enabled = visible, onBack = onDismissRequest)
            val scrimInteraction = remember { MutableInteractionSource() }

            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visibleState = transitionState,
                    enter = fadeIn(animationSpec = tween(durationMillis = 180)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 150)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (useSideSheet) 0.34f else 0.50f))
                            .clickable(
                                interactionSource = scrimInteraction,
                                indication = null,
                                onClick = onDismissRequest,
                            ),
                    )
                }

                if (!useSideSheet) {
                    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
                    val sheetShape = LocalJupiterShapes.current.sheetTop
                    val dismissThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
                    var dragOffsetY by remember(visible) { mutableFloatStateOf(0f) }

                    AnimatedVisibility(
                        visibleState = transitionState,
                        enter = slideInVertically(
                            animationSpec = tween(durationMillis = 220),
                            initialOffsetY = { fullHeight -> fullHeight },
                        ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                        exit = slideOutVertically(
                            animationSpec = tween(durationMillis = 180),
                            targetOffsetY = { fullHeight -> fullHeight },
                        ) + fadeOut(animationSpec = tween(durationMillis = 150)),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxHeight)
                                .imePadding()
                                .offset { IntOffset(0, dragOffsetY.roundToInt().coerceAtLeast(0)) }
                                .pointerInput(Unit) { detectTapGestures { } },
                            hazeState = overlayHost.hazeState,
                            shape = sheetShape,
                            elevated = true,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 24.dp, vertical = 20.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(onDismissRequest) {
                                            detectVerticalDragGestures(
                                                onDragEnd = {
                                                    if (dragOffsetY > dismissThresholdPx) {
                                                        onDismissRequest()
                                                    }
                                                    dragOffsetY = 0f
                                                },
                                                onDragCancel = { dragOffsetY = 0f },
                                                onVerticalDrag = { _, dragAmount ->
                                                    dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                                                },
                                            )
                                        }
                                        .padding(bottom = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(36.dp)
                                            .height(4.dp)
                                            .clip(CircleShape)
                                            .background(palette.borderSpecularTop),
                                    )
                                }
                                if (title != null) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.textPrimary,
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = palette.borderSubtle)
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                content()
                            }
                        }
                    }
                } else {
                    AnimatedVisibility(
                        visibleState = transitionState,
                        enter = slideInHorizontally(
                            animationSpec = tween(durationMillis = 220),
                            initialOffsetX = { fullWidth -> fullWidth },
                        ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                        exit = slideOutHorizontally(
                            animationSpec = tween(durationMillis = 180),
                            targetOffsetX = { fullWidth -> fullWidth },
                        ) + fadeOut(animationSpec = tween(durationMillis = 160)),
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        GlassSurface(
                            modifier = Modifier
                                .systemBarsPadding()
                                .imePadding()
                                .padding(16.dp)
                                .width(360.dp)
                                .fillMaxHeight()
                                .pointerInput(Unit) { detectTapGestures { } },
                            hazeState = overlayHost.hazeState,
                            shape = LocalJupiterShapes.current.surface,
                            elevated = true,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                            ) {
                                if (title != null) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.textPrimary,
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = palette.borderSubtle)
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                content()
                            }
                        }
                    }
                }
            }
        }
        return
    }

    if (!useSideSheet) {
        if (visible) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.65f).dp
            val sheetShape = LocalJupiterShapes.current.sheetTop

            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                sheetState = sheetState,
                shape = sheetShape,
                containerColor = Color.Transparent,
                contentColor = palette.textPrimary,
                scrimColor = Color.Black.copy(alpha = 0.56f),
                dragHandle = null,
            ) {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight),
                    hazeState = null,
                    shape = sheetShape,
                    elevated = true,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(palette.borderSpecularTop),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = palette.borderSubtle)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        content()
                    }
                }
            }
        }
    } else {
        BackHandler(enabled = visible, onBack = onDismissRequest)

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = fadeOut(animationSpec = tween(durationMillis = 160)),
            ) {
                val scrimInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .clickable(
                            interactionSource = scrimInteraction,
                            indication = null,
                            onClick = onDismissRequest,
                        ),
                )
            }

            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(
                    animationSpec = tween(durationMillis = 220),
                    initialOffsetX = { fullWidth -> fullWidth },
                ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = slideOutHorizontally(
                    animationSpec = tween(durationMillis = 180),
                    targetOffsetX = { fullWidth -> fullWidth },
                ) + fadeOut(animationSpec = tween(durationMillis = 160)),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                GlassSurface(
                    modifier = Modifier
                        .systemBarsPadding()
                        .padding(16.dp)
                        .width(360.dp)
                        .fillMaxHeight()
                        .pointerInput(Unit) { detectTapGestures { } },
                    hazeState = hazeState,
                    shape = LocalJupiterShapes.current.surface,
                    elevated = true,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                    ) {
                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = palette.borderSubtle)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        content()
                    }
                }
            }
        }
    }
}
