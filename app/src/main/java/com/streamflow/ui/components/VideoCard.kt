package com.streamflow.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflow.data.PlaybackQueue
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.model.VideoItem

private val avatarPalette = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350),
    Color(0xFFAB47BC), Color(0xFF42A5F5), Color(0xFFFF7043),
    Color(0xFF66BB6A), Color(0xFFD4E157), Color(0xFFEC407A),
    Color(0xFF29B6F6)
)

private fun avatarColorFor(name: String): Color {
    val idx = Math.abs(name.hashCode()) % avatarPalette.size
    return avatarPalette[idx]
}

// Channel avatar: real profile picture when available, colored letter circle as fallback
@Composable
fun ChannelAvatar(
    name: String,
    avatarUrl: String,
    size: androidx.compose.ui.unit.Dp = 34.dp,
    onClick: (() -> Unit)? = null
) {
    val clickMod = if (onClick != null)
        Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
    else Modifier
    if (avatarUrl.isNotEmpty()) {
        AsyncImage(
            model              = avatarUrl,
            contentDescription = name,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.size(size).clip(CircleShape)
                .background(avatarColorFor(name)).then(clickMod)
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape)
                .background(avatarColorFor(name)).then(clickMod),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = name.firstOrNull()?.uppercase() ?: "?",
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = (size.value * 0.41f).sp
            )
        }
    }
}

@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    progressFraction: Float = 0f,
    onAddToWatchLater: (() -> Unit)? = null,
    onAddToFavorites:  (() -> Unit)? = null,
    onChannelClick: ((String) -> Unit)? = null,
    onNotInterested: (() -> Unit)? = null,
    onBlockChannel: (() -> Unit)? = null,
    remainingLabel: String? = null
) {
    val context  = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var pressed  by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label         = "card_scale"
    )

    Box(modifier = Modifier.fillMaxWidth().scale(scale).padding(bottom = 20.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                        onTap   = { onClick() },
                        onLongPress = { showMenu = true }
                    )
                }
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model              = video.thumbnailUrl,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
                // Duration badge or remaining label
                if (remainingLabel != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(0.82f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(remainingLabel, color = Color.White,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else if (video.duration > 0) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(0.82f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(formatDuration(video.duration), color = Color.White,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                // Watch progress bar
                if (progressFraction in 0.01f..0.99f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                    ) {
                        Box(Modifier.fillMaxSize().background(Color.White.copy(0.3f)))
                        Box(Modifier.fillMaxWidth(progressFraction).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChannelAvatar(
                    name      = video.uploaderName,
                    avatarUrl = video.uploaderAvatarUrl,
                    size      = 34.dp,
                    onClick   = if (onChannelClick != null && video.uploaderUrl.isNotEmpty())
                        ({ onChannelClick(video.uploaderUrl) }) else null
                )
                Column(Modifier.weight(1f)) {
                    Text(video.title, style = MaterialTheme.typography.titleSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        video.uploaderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (onChannelClick != null && video.uploaderUrl.isNotEmpty())
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = if (onChannelClick != null && video.uploaderUrl.isNotEmpty())
                            Modifier.pointerInput(Unit) {
                                detectTapGestures(onTap = { onChannelClick(video.uploaderUrl) })
                            } else Modifier
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        buildString {
                            if (video.viewCount > 0) append("${formatViews(video.viewCount)} views")
                            if (video.viewCount > 0 && video.uploadedAgo.isNotEmpty()) append("  ·  ")
                            if (video.uploadedAgo.isNotEmpty()) append(video.uploadedAgo)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                // Three-dot menu anchor
                Box {
                    IconButton(
                        onClick  = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f),
                            modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (onAddToWatchLater != null) {
                            DropdownMenuItem(
                                text = { Text("Watch later") },
                                leadingIcon = { Icon(Icons.Default.BookmarkBorder, null, modifier = Modifier.size(18.dp)) },
                                onClick = { onAddToWatchLater(); showMenu = false }
                            )
                        }
                        if (onAddToFavorites != null) {
                            DropdownMenuItem(
                                text = { Text("Add to favorites") },
                                leadingIcon = { Icon(Icons.Default.FavoriteBorder, null, modifier = Modifier.size(18.dp)) },
                                onClick = { onAddToFavorites(); showMenu = false }
                            )
                        }
                        if (onNotInterested != null) {
                            DropdownMenuItem(
                                text = { Text("Not interested") },
                                leadingIcon = { Icon(Icons.Default.VisibilityOff, null, modifier = Modifier.size(18.dp)) },
                                onClick = { onNotInterested(); showMenu = false }
                            )
                        }
                        if (onBlockChannel != null) {
                            DropdownMenuItem(
                                text = { Text("Don't recommend channel") },
                                leadingIcon = { Icon(Icons.Default.Block, null, modifier = Modifier.size(18.dp)) },
                                onClick = { onBlockChannel(); showMenu = false }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, video.url) }
                                context.startActivity(Intent.createChooser(i, "Share video"))
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open in browser") },
                            leadingIcon = { Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                // Chooser needed: StreamFlow itself handles YouTube links now,
                                // so a plain ACTION_VIEW would just reopen the app
                                runCatching {
                                    context.startActivity(Intent.createChooser(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(video.url)), "Open with"))
                                }
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to queue") },
                            leadingIcon = { Icon(Icons.Default.QueueMusic, null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                PlaybackQueue.add(video)
                                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeroVideoCard(video: VideoItem, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.98f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label         = "hero_scale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress  = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap    = { onClick() }
                )
            }
    ) {
        AsyncImage(
            model              = video.thumbnailUrl,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxWidth().fillMaxHeight(0.55f).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.88f))))
        )
        // Play button overlay
        Box(
            Modifier
                .size(52.dp)
                .align(Alignment.Center)
                .background(Color.White.copy(0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        if (video.duration > 0) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(10.dp)
                    .background(Color.Black.copy(0.75f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(formatDuration(video.duration), color = Color.White,
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(12.dp).padding(end = 70.dp)
        ) {
            Text(video.title, color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(video.uploaderName)
                    if (video.viewCount > 0) append("  ·  ${formatViews(video.viewCount)} views")
                },
                color = Color.White.copy(0.75f), fontSize = 12.sp, maxLines = 1
            )
        }
    }
}

@Composable
fun ContinueWatchingCard(entity: HistoryEntity, onClick: () -> Unit) {
    val fraction = if (entity.duration > 0L)
        (entity.position / 1000f / entity.duration).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .width(160.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
        ) {
            AsyncImage(
                model              = entity.thumbnailUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
            Box(
                Modifier.align(Alignment.BottomStart).padding(5.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(0.88f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("▶ ${formatDuration(entity.position / 1000)}",
                    color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
            // Progress bar
            if (fraction > 0.01f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(0.3f)))
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(entity.title, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium, lineHeight = 15.sp)
    }
}

// Compact list row: thumbnail on the left, details on the right (YouTube search-result style)
@Composable
fun CompactVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onChannelClick: ((String) -> Unit)? = null,
    onAddToWatchLater: (() -> Unit)? = null,
    onAddToFavorites:  (() -> Unit)? = null,
    onNotInterested: (() -> Unit)? = null,
    onBlockChannel: (() -> Unit)? = null
) {
    val context  = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var pressed  by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label         = "compact_scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .padding(bottom = 12.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress     = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap       = { onClick() },
                    onLongPress = { showMenu = true }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
        ) {
            AsyncImage(
                model              = video.thumbnailUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
            if (video.duration > 0) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(5.dp)
                        .background(Color.Black.copy(0.82f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(formatDuration(video.duration), color = Color.White,
                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(video.title, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChannelAvatar(
                    name      = video.uploaderName,
                    avatarUrl = video.uploaderAvatarUrl,
                    size      = 18.dp,
                    onClick   = if (onChannelClick != null && video.uploaderUrl.isNotEmpty())
                        ({ onChannelClick(video.uploaderUrl) }) else null
                )
                Text(video.uploaderName, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = if (onChannelClick != null && video.uploaderUrl.isNotEmpty())
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(onTap = { onChannelClick(video.uploaderUrl) })
                        } else Modifier)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    if (video.viewCount > 0) append("${formatViews(video.viewCount)} views")
                    if (video.viewCount > 0 && video.uploadedAgo.isNotEmpty()) append("  ·  ")
                    if (video.uploadedAgo.isNotEmpty()) append(video.uploadedAgo)
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.MoreVert, null,
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f),
                    modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (onAddToWatchLater != null) {
                    DropdownMenuItem(
                        text = { Text("Watch later") },
                        leadingIcon = { Icon(Icons.Default.BookmarkBorder, null, modifier = Modifier.size(18.dp)) },
                        onClick = { onAddToWatchLater(); showMenu = false }
                    )
                }
                if (onAddToFavorites != null) {
                    DropdownMenuItem(
                        text = { Text("Add to favorites") },
                        leadingIcon = { Icon(Icons.Default.FavoriteBorder, null, modifier = Modifier.size(18.dp)) },
                        onClick = { onAddToFavorites(); showMenu = false }
                    )
                }
                if (onNotInterested != null) {
                    DropdownMenuItem(
                        text = { Text("Not interested") },
                        leadingIcon = { Icon(Icons.Default.VisibilityOff, null, modifier = Modifier.size(18.dp)) },
                        onClick = { onNotInterested(); showMenu = false }
                    )
                }
                if (onBlockChannel != null) {
                    DropdownMenuItem(
                        text = { Text("Don't recommend channel") },
                        leadingIcon = { Icon(Icons.Default.Block, null, modifier = Modifier.size(18.dp)) },
                        onClick = { onBlockChannel(); showMenu = false }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, video.url) }
                        context.startActivity(Intent.createChooser(i, "Share video"))
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    leadingIcon = { Icon(Icons.Default.QueueMusic, null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        PlaybackQueue.add(video)
                        Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                        showMenu = false
                    }
                )
            }
        }
    }
}

internal fun formatViews(count: Long): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0).trimEnd('0').trimEnd('.')
    count >= 1_000     -> "%.1fK".format(count / 1_000.0).trimEnd('0').trimEnd('.')
    else               -> count.toString()
}

internal fun formatDuration(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
