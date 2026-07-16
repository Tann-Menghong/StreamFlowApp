package com.streamflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun MiniPlayerBar(
    data: MiniPlayerData,
    mediaController: MediaController?,
    onNavigateToPlayer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    // Gestures: swipe sideways to dismiss, swipe up to reopen the full player
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(mediaController) {
        while (true) {
            val mc = mediaController
            if (mc != null) {
                isPlaying = mc.isPlaying
                progress = if (mc.duration > 0L)
                    (mc.currentPosition.toFloat() / mc.duration).coerceIn(0f, 1f) else 0f
            }
            delay(500L)
        }
    }

    // MODERN design: floating rounded card; CLASSIC: original full-width bar
    val modernStyle = com.streamflow.ui.theme.LocalDesignStyle.current == "MODERN"
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp,
        shadowElevation = 6.dp,
        shape = if (modernStyle) RoundedCornerShape(16.dp)
                else androidx.compose.ui.graphics.RectangleShape,
        modifier = Modifier
            .then(
                if (modernStyle) Modifier.padding(horizontal = 10.dp).padding(bottom = 6.dp)
                else Modifier
            )
            .graphicsLayer {
                translationX = dragX
                alpha = 1f - (kotlin.math.abs(dragX) / 700f).coerceIn(0f, 0.6f)
            }
            // Keyed on the controller: with pointerInput(Unit) the gesture kept the
            // NULL controller captured before the session connected, so swiping the
            // bar away dismissed it without actually pausing playback
            .pointerInput(mediaController) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dx -> dragX += dx },
                    onDragEnd = {
                        if (kotlin.math.abs(dragX) > 220f) {
                            mediaController?.pause()
                            onDismiss()
                        }
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f }
                )
            }
            // Keyed on the current video: after autoplay advanced, swipe-up used to
            // reopen the PREVIOUS video (stale data.url in the frozen closure)
            .pointerInput(data.url) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dy -> dragY += dy },
                    onDragEnd = {
                        if (dragY < -60f) onNavigateToPlayer(data.url)
                        dragY = 0f
                    },
                    onDragCancel = { dragY = 0f }
                )
            }
    ) {
        Column {
        // Thin playback progress line across the top
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline.copy(0.2f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clickable { onNavigateToPlayer(data.url) }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Thumbnail
            AsyncImage(
                model = data.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 80.dp, height = 45.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(0.2f))
            )

            // Title + uploader
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    data.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    data.uploaderName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Play/pause
            IconButton(
                onClick = {
                    val mc = mediaController ?: return@IconButton
                    // isPlaying flips asynchronously, so reading mc.isPlaying right
                    // after the call returns the OLD value and the icon shows the
                    // wrong glyph until the next poll — drive it from the intent.
                    if (mc.isPlaying) { mc.pause(); isPlaying = false }
                    else { mc.play(); isPlaying = true }
                }
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Close
            IconButton(onClick = {
                mediaController?.pause()
                onDismiss()
            }) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        }
    }
}
