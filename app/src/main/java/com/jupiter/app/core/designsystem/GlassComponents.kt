package com.jupiter.app.core.designsystem

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

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

    val sheenBrush = remember(palette.isLightCanvas) {
        val topAlpha = if (palette.isLightCanvas) 0.32f else 0.10f
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = topAlpha),
                Color.Transparent,
            ),
        )
    }

    val surfaceModifier = modifier
        .clip(shape)
        .then(
            when {
                glassStyle == GlassStyle.SOLID -> {
                    Modifier.background(baseColor, shape)
                }

                supportsLiveBlur -> {
                    val liveTintAlpha = if (elevated) 0.52f else 0.46f
                    val fallbackAlpha = if (elevated) 0.80f else 0.74f
                    Modifier
                        .hazeEffect(
                            state = hazeState,
                            style = HazeStyle(
                                backgroundColor = baseColor,
                                tints = listOf(HazeTint(baseColor.copy(alpha = liveTintAlpha))),
                                blurRadius = 24.dp,
                                fallbackTint = HazeTint(baseColor.copy(alpha = fallbackAlpha)),
                            ),
                        )
                        .background(sheenBrush, shape)
                }

                glassStyle == GlassStyle.FROSTED -> {
                    val translucentAlpha = if (elevated) 0.80f else 0.74f
                    Modifier
                        .background(baseColor.copy(alpha = translucentAlpha), shape)
                        .background(sheenBrush, shape)
                }

                else -> {
                    // GlassStyle.CLEAR: translucent glass without blur.
                    val clearAlpha = when {
                        hazeState != null -> if (elevated) 0.60f else 0.54f
                        elevated -> 0.64f
                        else -> 0.56f
                    }
                    Modifier
                        .background(baseColor.copy(alpha = clearAlpha), shape)
                        .background(sheenBrush, shape)
                }
            },
        )
        .then(
            if (glassStyle == GlassStyle.SOLID) {
                Modifier.border(
                    width = 1.dp,
                    color = palette.borderSubtle,
                    shape = shape,
                )
            } else {
                Modifier.border(
                    width = 1.dp,
                    brush = palette.specularBorderBrush(),
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
    val sheenBrush = remember(palette.isLightCanvas) {
        val topAlpha = if (palette.isLightCanvas) 0.32f else 0.10f
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = topAlpha),
                Color.Transparent,
            ),
        )
    }
    val targetBackground = when (glassStyle) {
        GlassStyle.SOLID -> when {
            !enabled -> palette.glassPrimary
            selected -> lerp(palette.glassPrimary, accentColor, 0.22f)
            else -> palette.glassPrimary
        }

        GlassStyle.FROSTED -> if (supportsLiveBlur) {
            when {
                !enabled -> palette.glassPrimary.copy(alpha = 0.42f)
                selected -> accentColor.copy(alpha = 0.22f)
                else -> palette.glassPrimary.copy(alpha = 0.46f)
            }
        } else {
            when {
                !enabled -> palette.glassPrimary.copy(alpha = 0.74f)
                selected -> lerp(palette.glassPrimary, accentColor, 0.22f).copy(alpha = 0.80f)
                else -> palette.glassPrimary.copy(alpha = 0.74f)
            }
        }

        GlassStyle.CLEAR -> if (hazeState != null) {
            when {
                !enabled -> palette.glassPrimary.copy(alpha = 0.50f)
                selected -> lerp(palette.glassPrimary, accentColor, 0.22f).copy(alpha = 0.60f)
                else -> palette.glassPrimary.copy(alpha = 0.54f)
            }
        } else {
            when {
                !enabled -> palette.glassPrimary.copy(alpha = 0.52f)
                selected -> lerp(palette.glassPrimary, accentColor, 0.22f).copy(alpha = 0.64f)
                else -> palette.glassPrimary.copy(alpha = 0.56f)
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
                                backgroundColor = palette.glassPrimary,
                                tints = listOf(HazeTint(backgroundColor)),
                                blurRadius = 24.dp,
                                fallbackTint = HazeTint(palette.glassPrimary.copy(alpha = 0.74f)),
                            ),
                        )
                        .background(sheenBrush, CircleShape)
                } else if (glassStyle != GlassStyle.SOLID) {
                    Modifier
                        .background(backgroundColor, CircleShape)
                        .background(sheenBrush, CircleShape)
                } else {
                    Modifier.background(backgroundColor, CircleShape)
                },
            )
            .then(
                if (glassStyle != GlassStyle.SOLID && enabled && !selected) {
                    Modifier.border(
                        width = 1.dp,
                        brush = palette.specularBorderBrush(),
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
fun GlassDialog(
    onDismissRequest: () -> Unit,
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        // Dialog opens its own window; avoid cross-window Haze sampling and use frosted backing.
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
                        .fillMaxHeight(),
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
