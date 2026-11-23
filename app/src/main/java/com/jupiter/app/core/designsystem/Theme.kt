package com.jupiter.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object JupiterColors {
    val Canvas = Color(0xFF090C12)
    val GlassPrimary = Color(0xFF141923)
    val GlassElevated = Color(0xFF1E2636)
    val MonochromeAccent = Color(0xFFE6ECF5)
    val IceAccent = Color(0xFF5CE1E6)
    val CobaltAccent = Color(0xFF7DA8F5)
    val SageAccent = Color(0xFF7BC9A6)
    val AmberAccent = Color(0xFFE8B86D)
    val Accent = MonochromeAccent
    val TextPrimary = Color(0xFFF2F5FA)
    val TextSecondary = Color(0xFF9AA4B2)
    val BorderSubtle = Color.White.copy(alpha = 0.08f)
    val BorderSpecularTop = Color.White.copy(alpha = 0.26f)
    val BorderSpecularBottom = Color.White.copy(alpha = 0.06f)
}

data class JupiterPalette(
    val canvas: Color,
    val glassPrimary: Color,
    val glassElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val borderSubtle: Color,
    val borderSpecularTop: Color,
    val borderSpecularBottom: Color,
    val isLightCanvas: Boolean,
)

fun defaultTextArgbForCanvas(canvasArgb: Int): Int {
    val opaqueCanvas = canvasArgb or (0xFF shl 24)
    return if (ColorWheelMath.isLightColor(opaqueCanvas)) {
        0xFF111620.toInt()
    } else {
        0xFFF2F5FA.toInt()
    }
}

fun deriveJupiterPalette(canvasArgb: Int, textArgb: Int? = null): JupiterPalette {
    val opaqueCanvas = canvasArgb or (0xFF shl 24)
    val isLight = ColorWheelMath.isLightColor(opaqueCanvas)
    val luminance = ColorWheelMath.relativeLuminance(opaqueCanvas)
    val customOpaqueText = textArgb?.let { it or (0xFF shl 24) }

    return if (isLight) {
        val glassPrimaryArgb = if (luminance > 0.85) {
            ColorWheelMath.blendArgb(opaqueCanvas, 0xFF090C12.toInt(), 0.045f)
        } else {
            ColorWheelMath.blendArgb(opaqueCanvas, 0xFFFFFFFF.toInt(), 0.42f)
        }
        val glassElevatedArgb = if (luminance > 0.85) {
            ColorWheelMath.blendArgb(opaqueCanvas, 0xFF090C12.toInt(), 0.09f)
        } else {
            ColorWheelMath.blendArgb(opaqueCanvas, 0xFFFFFFFF.toInt(), 0.68f)
        }
        val textPrimaryColor = Color(customOpaqueText ?: 0xFF111620.toInt())
        val textSecondaryColor = if (customOpaqueText != null) {
            Color(ColorWheelMath.blendArgb(customOpaqueText, opaqueCanvas, 0.34f))
        } else {
            Color(0xFF4B5565)
        }
        JupiterPalette(
            canvas = Color(opaqueCanvas),
            glassPrimary = Color(glassPrimaryArgb),
            glassElevated = Color(glassElevatedArgb),
            textPrimary = textPrimaryColor,
            textSecondary = textSecondaryColor,
            borderSubtle = Color(0xFF090C12).copy(alpha = 0.09f),
            borderSpecularTop = Color(0xFF090C12).copy(alpha = 0.18f),
            borderSpecularBottom = Color(0xFF090C12).copy(alpha = 0.07f),
            isLightCanvas = true,
        )
    } else {
        val glassPrimaryArgb = ColorWheelMath.blendArgb(opaqueCanvas, 0xFFFFFFFF.toInt(), 0.06f)
        val glassElevatedArgb = ColorWheelMath.blendArgb(opaqueCanvas, 0xFFFFFFFF.toInt(), 0.12f)
        val defaultTextSecondaryArgb = if (luminance > 0.06) {
            ColorWheelMath.blendArgb(0xFFF2F5FA.toInt(), opaqueCanvas, 0.34f)
        } else {
            0xFF9AA4B2.toInt()
        }
        val textPrimaryColor = Color(customOpaqueText ?: 0xFFF2F5FA.toInt())
        val textSecondaryColor = if (customOpaqueText != null) {
            Color(ColorWheelMath.blendArgb(customOpaqueText, opaqueCanvas, 0.34f))
        } else {
            Color(defaultTextSecondaryArgb)
        }
        JupiterPalette(
            canvas = Color(opaqueCanvas),
            glassPrimary = Color(glassPrimaryArgb),
            glassElevated = Color(glassElevatedArgb),
            textPrimary = textPrimaryColor,
            textSecondary = textSecondaryColor,
            borderSubtle = Color.White.copy(alpha = 0.08f),
            borderSpecularTop = Color.White.copy(alpha = 0.26f),
            borderSpecularBottom = Color.White.copy(alpha = 0.06f),
            isLightCanvas = false,
        )
    }
}

val LocalJupiterPalette = staticCompositionLocalOf {
    deriveJupiterPalette(0xFF090C12.toInt())
}

internal val JupiterSpecularBorderBrush: Brush = Brush.linearGradient(
    colors = listOf(
        JupiterColors.BorderSpecularTop,
        JupiterColors.BorderSpecularBottom,
    ),
)

internal fun JupiterPalette.specularBorderBrush(): Brush = Brush.linearGradient(
    colors = listOf(borderSpecularTop, borderSpecularBottom),
)

internal fun JupiterPalette.frostedBorderBrush(): Brush = Brush.linearGradient(
    colors = if (isLightCanvas) {
        listOf(
            Color(0xFF090C12).copy(alpha = 0.16f),
            Color(0xFF090C12).copy(alpha = 0.06f),
        )
    } else {
        listOf(
            Color.White.copy(alpha = 0.22f),
            Color.White.copy(alpha = 0.06f),
        )
    },
)

internal fun JupiterPalette.clearBorderBrush(): Brush = Brush.linearGradient(
    colors = if (isLightCanvas) {
        listOf(
            Color.White.copy(alpha = 0.65f),
            Color(0xFF090C12).copy(alpha = 0.18f),
        )
    } else {
        listOf(
            Color.White.copy(alpha = 0.42f),
            Color.White.copy(alpha = 0.14f),
        )
    },
)

private fun jupiterColorScheme(
    palette: JupiterPalette,
    primary: Color,
    onPrimary: Color,
) = if (palette.isLightCanvas) {
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = palette.glassElevated,
        onPrimaryContainer = palette.textPrimary,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = palette.glassElevated,
        onSecondaryContainer = palette.textPrimary,
        tertiary = primary,
        onTertiary = onPrimary,
        background = palette.canvas,
        onBackground = palette.textPrimary,
        surface = palette.glassPrimary,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.glassElevated,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.borderSubtle,
        outlineVariant = palette.borderSpecularBottom,
    )
} else {
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = palette.glassElevated,
        onPrimaryContainer = palette.textPrimary,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = palette.glassElevated,
        onSecondaryContainer = palette.textPrimary,
        tertiary = primary,
        onTertiary = onPrimary,
        background = palette.canvas,
        onBackground = palette.textPrimary,
        surface = palette.glassPrimary,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.glassElevated,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.borderSubtle,
        outlineVariant = palette.borderSpecularBottom,
    )
}

private val JupiterTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
)

data class JupiterShapes(
    val percent: Int,
    val navBar: RoundedCornerShape,
    val surface: RoundedCornerShape,
    val control: RoundedCornerShape,
    val menu: RoundedCornerShape,
    val sheetTop: RoundedCornerShape,
)

fun computeJupiterShapes(percent: Int): JupiterShapes {
    val clamped = percent.coerceIn(0, 100)
    val f = clamped / 100f
    return JupiterShapes(
        percent = clamped,
        navBar = RoundedCornerShape((4f + 24f * f).dp),      // 4dp..28dp (16dp at 50%)
        surface = RoundedCornerShape((4f + 20f * f).dp),     // 4dp..24dp (14dp at 50%)
        control = RoundedCornerShape((4f + 16f * f).dp),     // 4dp..20dp (12dp at 50%)
        menu = RoundedCornerShape((4f + 14f * f).dp),        // 4dp..18dp (11dp at 50%)
        sheetTop = RoundedCornerShape(
            topStart = (4f + 24f * f).dp,
            topEnd = (4f + 24f * f).dp,
        ),
    )
}

val LocalJupiterShapes = staticCompositionLocalOf { computeJupiterShapes(50) }

enum class GlassStyle(val label: String) {
    SOLID("Solid"),
    FROSTED("Frosted"),
    CLEAR("Clear"),
}

val LocalGlassStyle = staticCompositionLocalOf { GlassStyle.FROSTED }

@Composable
fun JupiterTheme(
    backgroundArgb: Int = 0xFF090C12.toInt(),
    accentArgb: Int = 0xFFE6ECF5.toInt(),
    textArgb: Int? = null,
    glassStyle: GlassStyle = GlassStyle.FROSTED,
    cornerRoundnessPercent: Int = 50,
    content: @Composable () -> Unit,
) {
    val canvasArgb = backgroundArgb or (0xFF shl 24)
    val opaqueAccent = accentArgb or (0xFF shl 24)
    val palette = remember(canvasArgb, textArgb) { deriveJupiterPalette(canvasArgb, textArgb) }
    val primary = remember(opaqueAccent) { Color(opaqueAccent) }
    val onPrimary = remember(opaqueAccent) { Color(ColorWheelMath.computeOnColorArgb(opaqueAccent)) }

    val colorScheme = remember(palette, primary, onPrimary) {
        jupiterColorScheme(
            palette = palette,
            primary = primary,
            onPrimary = onPrimary,
        )
    }

    val jupiterShapes = remember(cornerRoundnessPercent) {
        computeJupiterShapes(cornerRoundnessPercent)
    }

    CompositionLocalProvider(
        LocalJupiterPalette provides palette,
        LocalGlassStyle provides glassStyle,
        LocalJupiterShapes provides jupiterShapes,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = JupiterTypography,
            content = content,
        )
    }
}

@Composable
fun AmbientGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val glassStyle = LocalGlassStyle.current
    val primary = MaterialTheme.colorScheme.primary
    val topStartLift = remember(palette, primary) {
        if (palette.isLightCanvas) {
            lerp(palette.glassElevated, Color.White, 0.45f).copy(alpha = 0.65f)
        } else {
            lerp(lerp(palette.glassElevated, Color.White, 0.06f), primary, 0.12f).copy(alpha = 0.82f)
        }
    }
    val topStartTint = remember(palette, primary) {
        primary.copy(alpha = if (palette.isLightCanvas) 0.06f else 0.12f)
    }
    val bottomEndTone = remember(palette, primary) {
        if (palette.isLightCanvas) {
            lerp(palette.glassElevated, Color(0xFF090C12), 0.08f).copy(alpha = 0.50f)
        } else {
            lerp(palette.glassElevated, primary, 0.10f).copy(alpha = 0.68f)
        }
    }
    val bottomEndTint = remember(palette, primary) {
        primary.copy(alpha = if (palette.isLightCanvas) 0.05f else 0.10f)
    }
    val verticalTopTint = remember(palette, primary) {
        if (palette.isLightCanvas) {
            Color.White.copy(alpha = 0.34f)
        } else {
            lerp(palette.glassPrimary, primary, 0.05f).copy(alpha = 0.34f)
        }
    }
    val verticalBottomTint = remember(palette) {
        if (palette.isLightCanvas) {
            Color(0xFF090C12).copy(alpha = 0.07f)
        } else {
            Color.Black.copy(alpha = 0.24f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.canvas)
            .then(
                if (glassStyle == GlassStyle.SOLID) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    verticalTopTint,
                                    Color.Transparent,
                                    verticalBottomTint,
                                ),
                            ),
                        )

                        val topStartCenter = Offset(x = size.width * 0.14f, y = size.height * 0.10f)
                        val bottomEndCenter = Offset(x = size.width * 0.88f, y = size.height * 0.86f)
                        val primaryRadius = maxOf(size.width, size.height) * 0.78f
                        val secondaryRadius = maxOf(size.width, size.height) * 0.70f

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    topStartLift,
                                    topStartTint,
                                    Color.Transparent,
                                ),
                                center = topStartCenter,
                                radius = primaryRadius,
                            ),
                            radius = primaryRadius,
                            center = topStartCenter,
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    bottomEndTone,
                                    bottomEndTint,
                                    Color.Transparent,
                                ),
                                center = bottomEndCenter,
                                radius = secondaryRadius,
                            ),
                            radius = secondaryRadius,
                            center = bottomEndCenter,
                        )
                    }
                },
            ),
        content = content,
    )
}
