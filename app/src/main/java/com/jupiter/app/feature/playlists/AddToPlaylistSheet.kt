package com.jupiter.app.feature.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupiter.app.core.designsystem.GlassAdaptiveSheet
import com.jupiter.app.core.designsystem.LocalJupiterPalette
import com.jupiter.app.core.designsystem.LocalJupiterShapes
import com.jupiter.app.data.local.PlaylistSummary

@Composable
fun AddToPlaylistSheet(
    visible: Boolean,
    useSideSheet: Boolean,
    playlists: List<PlaylistSummary>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (playlistId: Long) -> Unit,
    onCreatePlaylistAndAdd: (name: String) -> Unit,
) {
    val palette = LocalJupiterPalette.current
    val shapes = LocalJupiterShapes.current
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val errorColor = if (palette.isLightCanvas) Color(0xFFB3261E) else Color(0xFFF28B82)

    var newPlaylistName by remember(visible) { mutableStateOf("") }
    var nameError by remember(visible) { mutableStateOf<String?>(null) }

    val submitCreate: () -> Unit = {
        val trimmed = newPlaylistName.trim()
        if (trimmed.isEmpty()) {
            nameError = "Enter a playlist name"
        } else {
            nameError = null
            onCreatePlaylistAndAdd(trimmed)
        }
    }

    GlassAdaptiveSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        useSideSheet = useSideSheet,
        hazeState = null,
        title = "Add to playlist",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(shapes.control)
                        .background(palette.canvas.copy(alpha = 0.56f), shapes.control)
                        .border(
                            width = 1.dp,
                            color = if (nameError != null) errorColor.copy(alpha = 0.64f) else palette.borderSubtle,
                            shape = shapes.control,
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (newPlaylistName.isEmpty()) {
                        Text(
                            text = "New playlist",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = newPlaylistName,
                        onValueChange = {
                            newPlaylistName = it
                            if (nameError != null) nameError = null
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitCreate() }),
                        cursorBrush = SolidColor(accent),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = palette.textPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(shapes.control)
                        .background(accent, shapes.control)
                        .clickable(role = Role.Button, onClick = submitCreate)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Create & add",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = onAccent,
                        maxLines = 1,
                    )
                }
            }

            if (nameError != null) {
                Text(
                    text = nameError.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = errorColor,
                )
            }

            if (playlists.isNotEmpty()) {
                HorizontalDivider(color = palette.borderSubtle)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    playlists.forEach { summary ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(shapes.control)
                                .clickable(role = Role.Button) {
                                    onSelectPlaylist(summary.id)
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
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
                                text = summary.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = formatPlaylistItemTotal(summary.itemCount),
                                style = MaterialTheme.typography.labelMedium.merge(
                                    TextStyle(fontFeatureSettings = "tnum"),
                                ),
                                color = palette.textSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "No existing playlists",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun formatPlaylistItemTotal(count: Int): String = when (count) {
    0 -> "Empty"
    1 -> "1 item"
    else -> "$count items"
}
