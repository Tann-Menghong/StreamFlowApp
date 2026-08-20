package com.streamflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.streamflow.ui.theme.appShape
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

    // Recovery state, collected here as well as in the player screen.
    //
    // PlaybackRecovery is process-wide, but only PlayerScreen ever read it — so
    // someone listening with just this bar showing got the old silent failure:
    // audio stops, and nothing anywhere says whether the app is reconnecting or
    // has given up. This bar IS the player for background listening, and it
    // should say what the full player says.
    val recovery by com.streamflow.data.PlaybackRecovery.state.collectAsState()
    val playbackFatal by com.streamflow.data.PlaybackRecovery.fatal.collectAsState()

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    // Gated on STARTED, like the player screen's loops. This bar is composed
    // precisely when the user is listening with the screen off or the app in the
    // background, so an ungated 500 ms loop woke the CPU twice a second for the
    // whole session to compute a progress fraction nobody could see. The same
    // fix was applied to the four loops in PlayerScreen; this one was missed.
    LaunchedEffect(mediaController, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
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
    }

    // Replaces the channel name only while something is actually wrong, so the
    // bar reads normally the rest of the time.
    val statusLine: String? = when {
        playbackFatal != null -> playbackFatal
        recovery.waitingForNetwork -> "Waiting for network"
        recovery.active ->
            "Reconnecting ${recovery.attempt}/${com.streamflow.data.PlaybackRecovery.MAX_ATTEMPTS}"
        else -> null
    }

    // MODERN: floating rounded card; AURORA: adds a gradient hairline border;
    // CLASSIC: original full-width bar
    val designStyle = com.streamflow.ui.theme.LocalDesignStyle.current
    val terminalStyle = designStyle == "TERMINAL"
    val modernStyle = designStyle != "CLASSIC" && !terminalStyle
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (terminalStyle) 0.dp else 8.dp,
        shadowElevation = if (terminalStyle) 0.dp else 6.dp,
        border = when {
            // A solid phosphor frame: the "now playing" pane docked above the
            // tab bar, exactly like a split in a terminal multiplexer.
            terminalStyle -> androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.outline)
            designStyle == "AURORA" -> androidx.compose.foundation.BorderStroke(1.dp,
                androidx.compose.ui.graphics.Brush.linearGradient(listOf(
                    MaterialTheme.colorScheme.primary.copy(0.55f),
                    MaterialTheme.colorScheme.tertiary.copy(0.35f))))
            else -> null
        },
        shape = if (modernStyle) appShape(16.dp)
                else androidx.compose.ui.graphics.RectangleShape,
        modifier = Modifier
            .then(
                when {
                    terminalStyle -> Modifier.padding(horizontal = 6.dp).padding(bottom = 4.dp)
                    modernStyle -> Modifier.padding(horizontal = 10.dp).padding(bottom = 6.dp)
                    else -> Modifier
                }
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
                    .clip(appShape(8.dp))
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
                    statusLine ?: data.uploaderName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Amber while recovering, red once it has given up: the two
                    // outcomes need to look different, because one says wait and
                    // the other says the video needs another try.
                    color = when {
                        playbackFatal != null -> MaterialTheme.colorScheme.error
                        statusLine != null -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
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
