package com.jupiter.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

object ColorWheelMath {

    fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)

        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - c

        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun argbToHsv(argb: Int): Triple<Float, Float, Float> {
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val rawHue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        val hue = ((rawHue % 360f) + 360f) % 360f
        val saturation = if (max == 0f) 0f else delta / max
        return Triple(hue, saturation, max)
    }

    fun pointToHueSaturation(dx: Float, dy: Float, radius: Float): Pair<Float, Float> {
        if (radius <= 0f) return 0f to 0f
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val saturation = (distance / radius).coerceIn(0f, 1f)
        if (distance == 0f) return 0f to 0f
        val angleDeg = (atan2(dy.toDouble(), dx.toDouble()) * (180.0 / PI)).toFloat()
        val hue = ((angleDeg % 360f) + 360f) % 360f
        return hue to saturation
    }

    fun relativeLuminance(argb: Int): Double {
        fun linearize(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = linearize((argb ushr 16) and 0xFF)
        val g = linearize((argb ushr 8) and 0xFF)
        val b = linearize(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun isLightColor(argb: Int): Boolean = relativeLuminance(argb) > 0.35

    fun computeOnColorArgb(bgArgb: Int): Int =
        if (isLightColor(bgArgb)) 0xFF090C12.toInt() else 0xFFF2F5FA.toInt()

    fun blendArgb(baseArgb: Int, targetArgb: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val a0 = (baseArgb ushr 24) and 0xFF
        val r0 = (baseArgb ushr 16) and 0xFF
        val g0 = (baseArgb ushr 8) and 0xFF
        val b0 = baseArgb and 0xFF

        val a1 = (targetArgb ushr 24) and 0xFF
        val r1 = (targetArgb ushr 16) and 0xFF
        val g1 = (targetArgb ushr 8) and 0xFF
        val b1 = targetArgb and 0xFF

        val a = (a0 + (a1 - a0) * f).roundToInt().coerceIn(0, 255)
        val r = (r0 + (r1 - r0) * f).roundToInt().coerceIn(0, 255)
        val g = (g0 + (g1 - g0) * f).roundToInt().coerceIn(0, 255)
        val b = (b0 + (b1 - b0) * f).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun formatHex(argb: Int): String =
        String.format(Locale.US, "#%06X", argb and 0xFFFFFF)
}

data class ColorPreset(
    val label: String,
    val argb: Int,
)

val BackgroundColorPresets: List<ColorPreset> = listOf(
    ColorPreset(label = "Obsidian", argb = 0xFF090C12.toInt()),
    ColorPreset(label = "Black", argb = 0xFF000000.toInt()),
    ColorPreset(label = "Midnight", argb = 0xFF0D1829.toInt()),
    ColorPreset(label = "Forest", argb = 0xFF0C1A14.toInt()),
    ColorPreset(label = "Warm slate", argb = 0xFF1C1917.toInt()),
)

val AccentColorPresets: List<ColorPreset> = listOf(
    ColorPreset(label = "Monochrome", argb = 0xFFE6ECF5.toInt()),
    ColorPreset(label = "Ice", argb = 0xFF5CE1E6.toInt()),
    ColorPreset(label = "Cobalt", argb = 0xFF7DA8F5.toInt()),
    ColorPreset(label = "Sage", argb = 0xFF7BC9A6.toInt()),
    ColorPreset(label = "Amber", argb = 0xFFE8B86D.toInt()),
)

@Composable
fun ColorWheelDialog(
    title: String,
    initialArgb: Int,
    defaultArgb: Int,
    onDismissRequest: () -> Unit,
    onColorSelected: (Int) -> Unit,
    presets: List<ColorPreset> = emptyList(),
) {
    val initialHsv = remember(initialArgb) { ColorWheelMath.argbToHsv(initialArgb) }
    var hue by remember(initialArgb) { mutableFloatStateOf(initialHsv.first) }
    var saturation by remember(initialArgb) { mutableFloatStateOf(initialHsv.second) }
    var value by remember(initialArgb) { mutableFloatStateOf(initialHsv.third) }

    val selectedArgb = ColorWheelMath.hsvToArgb(hue, saturation, value)
    val palette = LocalJupiterPalette.current
    val controlShape = LocalJupiterShapes.current.control
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary

    GlassDialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
            )

            if (presets.isNotEmpty()) {
                PresetSwatchRow(
                    presets = presets,
                    selectedArgb = selectedArgb,
                    activeBorderColor = accent,
                    inactiveBorderColor = palette.borderSubtle,
                    onPresetClick = { preset ->
                        val hsv = ColorWheelMath.argbToHsv(preset.argb)
                        hue = hsv.first
                        saturation = hsv.second
                        value = hsv.third
                    },
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                ChromaticColorWheel(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    selectedArgb = selectedArgb,
                    borderColor = palette.borderSubtle,
                    onHueSaturationChange = { newHue, newSaturation ->
                        hue = newHue
                        saturation = newSaturation
                    },
                    modifier = Modifier.size(216.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Brightness",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                    )
                    Text(
                        text = "${(value * 100f).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge.merge(
                            TextStyle(fontFeatureSettings = "tnum"),
                        ),
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                    )
                }

                BrightnessGradientSlider(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    selectedArgb = selectedArgb,
                    borderColor = palette.borderSubtle,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(selectedArgb))
                            .border(width = 1.dp, color = palette.borderSubtle, shape = CircleShape),
                    )
                    Text(
                        text = ColorWheelMath.formatHex(selectedArgb),
                        style = MaterialTheme.typography.labelLarge.merge(
                            TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontFeatureSettings = "tnum",
                            ),
                        ),
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                    )
                }

                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(controlShape)
                        .background(palette.glassPrimary.copy(alpha = 0.72f), controlShape)
                        .border(width = 1.dp, color = palette.borderSubtle, shape = controlShape)
                        .clickable(role = Role.Button) {
                            val resetHsv = ColorWheelMath.argbToHsv(defaultArgb)
                            hue = resetHsv.first
                            saturation = resetHsv.second
                            value = resetHsv.third
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                    )
                }
            }

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
                        .border(width = 1.dp, color = palette.borderSubtle, shape = controlShape)
                        .clickable(role = Role.Button, onClick = onDismissRequest)
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
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(controlShape)
                        .background(accent, controlShape)
                        .clickable(role = Role.Button) { onColorSelected(selectedArgb) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Apply",
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
private fun PresetSwatchRow(
    presets: List<ColorPreset>,
    selectedArgb: Int,
    activeBorderColor: Color,
    inactiveBorderColor: Color,
    onPresetClick: (ColorPreset) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.forEach { preset ->
            val isSelected = (selectedArgb and 0xFFFFFF) == (preset.argb and 0xFFFFFF)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) {
                            Modifier
                                .border(width = 2.dp, color = activeBorderColor, shape = CircleShape)
                                .padding(3.dp)
                        } else {
                            Modifier.border(width = 1.dp, color = inactiveBorderColor, shape = CircleShape)
                        },
                    )
                    .clip(CircleShape)
                    .background(Color(preset.argb))
                    .semantics {
                        this.contentDescription = preset.label
                        this.selected = isSelected
                    }
                    .clickable(role = Role.Button) {
                        onPresetClick(preset)
                    },
            )
        }
    }
}

@Composable
private fun ChromaticColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    selectedArgb: Int,
    borderColor: Color,
    onHueSaturationChange: (hue: Float, saturation: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sweepColors = remember(value) {
        listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { deg ->
            Color(ColorWheelMath.hsvToArgb(deg, 1f, value))
        }
    }
    val centerWhite = remember(value) {
        Color(ColorWheelMath.hsvToArgb(0f, 0f, value))
    }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val radius = minOf(size.width, size.height) / 2f
                    val dx = offset.x - size.width / 2f
                    val dy = offset.y - size.height / 2f
                    val (newHue, newSat) = ColorWheelMath.pointToHueSaturation(dx, dy, radius)
                    onHueSaturationChange(newHue, newSat)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val radius = minOf(size.width, size.height) / 2f
                    val dx = change.position.x - size.width / 2f
                    val dy = change.position.y - size.height / 2f
                    val (newHue, newSat) = ColorWheelMath.pointToHueSaturation(dx, dy, radius)
                    onHueSaturationChange(newHue, newSat)
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f - 2.dp.toPx()

        drawCircle(
            brush = Brush.sweepGradient(colors = sweepColors, center = center),
            radius = radius,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(centerWhite, centerWhite.copy(alpha = 0f)),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = borderColor,
            radius = radius,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )

        val angleRad = hue * (PI / 180.0)
        val thumbOffset = Offset(
            x = center.x + (cos(angleRad) * saturation * radius).toFloat(),
            y = center.y + (sin(angleRad) * saturation * radius).toFloat(),
        )
        val thumbRadius = 10.dp.toPx()
        val ringColor = Color(ColorWheelMath.computeOnColorArgb(selectedArgb))
        drawCircle(
            color = Color(selectedArgb),
            radius = thumbRadius,
            center = thumbOffset,
        )
        drawCircle(
            color = ringColor,
            radius = thumbRadius,
            center = thumbOffset,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
private fun BrightnessGradientSlider(
    hue: Float,
    saturation: Float,
    value: Float,
    selectedArgb: Int,
    borderColor: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fullBrightColor = remember(hue, saturation) {
        Color(ColorWheelMath.hsvToArgb(hue, saturation, 1f))
    }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width > 0) {
                        onValueChange((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    if (size.width > 0) {
                        onValueChange((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
            },
    ) {
        val cornerRadius = size.height / 2f
        val horizontalInset = cornerRadius
        val trackWidth = (size.width - horizontalInset * 2f).coerceAtLeast(1f)

        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Black, fullBrightColor),
            ),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        )
        drawRoundRect(
            color = borderColor,
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = 1.dp.toPx()),
        )

        val thumbX = horizontalInset + value.coerceIn(0f, 1f) * trackWidth
        val thumbCenter = Offset(thumbX, size.height / 2f)
        val thumbRadius = (size.height / 2f) - 3.dp.toPx()
        val ringColor = Color(ColorWheelMath.computeOnColorArgb(selectedArgb))

        drawCircle(
            color = Color(selectedArgb),
            radius = thumbRadius,
            center = thumbCenter,
        )
        drawCircle(
            color = ringColor,
            radius = thumbRadius,
            center = thumbCenter,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}
