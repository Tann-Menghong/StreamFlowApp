package com.streamflow.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflow.data.PlaybackQueue
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.model.VideoItem
import com.streamflow.ui.theme.LocalHapticsEnabled
import com.streamflow.ui.theme.appShape
import com.streamflow.ui.theme.appCircle
import com.streamflow.ui.theme.LocalThumbCorner

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
        Modifier.pointerInput(name, avatarUrl) { detectTapGestures(onTap = { onClick() }) }
    else Modifier
    if (avatarUrl.isNotEmpty()) {
        AsyncImage(
            model              = avatarUrl,
            contentDescription = name,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.size(size).clip(appCircle())
                .background(avatarColorFor(name)).then(clickMod)
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(appCircle())
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

/**
 * The timecode chip over a thumbnail.
 *
 * Extracted because three card variants (feed, hero, compact) each carried their
 * own copy with slightly different radii and opacities — so styling the feed one
 * for TERMINAL left the other two as black pills, which is exactly the kind of
 * one-off drift a design system exists to remove. One definition now.
 *
 * TERMINAL renders it as inverted video (solid phosphor block, background-colour
 * text) instead of a translucent rounded pill: same information, read as a
 * status field on a readout.
 */
@Composable
private fun DurationBadge(
    label: String,
    modifier: Modifier = Modifier,
    outerPadding: androidx.compose.ui.unit.Dp = 8.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
) {
    val terminal = com.streamflow.ui.theme.LocalTerminalMode.current
    Box(
        modifier
            .padding(if (terminal) 4.dp else outerPadding)
            .background(
                if (terminal) MaterialTheme.colorScheme.primary else Color.Black.copy(0.82f),
                appShape(5.dp)   // appShape already squares off in TERMINAL
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            label,
            color = if (terminal) MaterialTheme.colorScheme.background else Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val haptic   = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val corner   = LocalThumbCorner.current
    var showMenu by remember { mutableStateOf(false) }
    var pressed  by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label         = "card_scale"
    )

    // MODERN: soft tonal card; AURORA: translucent card with a gradient hairline
    // border (the "glass" look); CLASSIC: original flat layout
    val designStyle = com.streamflow.ui.theme.LocalDesignStyle.current
    val terminalStyle = designStyle == "TERMINAL"
    val modernStyle = designStyle != "CLASSIC" && !terminalStyle
    val auroraStyle = designStyle == "AURORA"
    // Radius 0 is absolute in TERMINAL; the other styles round the card slightly
    // more than the thumbnail so the media sits inside the frame.
    val cardCorner = if (terminalStyle) 0 else corner + 10
    Box(modifier = Modifier.fillMaxWidth().scale(scale)
        .padding(bottom = if (terminalStyle) 10.dp else if (modernStyle) 18.dp else 20.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    // MODERN: an elevated media card — real depth (shadow + a lighter
                    // surface than the page) with an edge-to-edge thumbnail, so the feed
                    // reads as a stack of distinct cards, not a flat wall of thumbnails.
                    when {
                        // TERMINAL: a hard-edged pane. No shadow — the design
                        // system forbids depth; separation comes from the 1px
                        // phosphor border alone.
                        terminalStyle -> Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outline)
                            .background(MaterialTheme.colorScheme.surface)
                        auroraStyle -> Modifier
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(listOf(
                                    MaterialTheme.colorScheme.primary.copy(0.55f),
                                    MaterialTheme.colorScheme.tertiary.copy(0.35f),
                                    MaterialTheme.colorScheme.primary.copy(0.15f))),
                                shape = appShape(cardCorner.dp))
                            .clip(appShape(cardCorner.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.35f))
                        modernStyle -> Modifier
                            .shadow(6.dp, appShape(cardCorner.dp))
                            .background(MaterialTheme.colorScheme.surface)
                        else -> Modifier
                    }
                )
                // Keyed on the video: pointerInput(Unit) froze the FIRST video's
                // click/long-press closures, so after an in-place feed refresh a tap
                // could open the wrong (old) video
                .pointerInput(video.url) {
                    detectTapGestures(
                        onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                        onTap   = { onClick() },
                        onLongPress = {
                            if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMenu = true
                        }
                    )
                }
        ) {
            // Thumbnail — edge-to-edge in MODERN (only top corners round, flush to the
            // card above the metadata footer); self-contained rounded tile in CLASSIC.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(
                        if (modernStyle) appShape(cardCorner.dp, cardCorner.dp, 0.dp, 0.dp)
                        else appShape(corner.dp)   // corner is forced to 0 in TERMINAL
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
            ) {
                AsyncImage(
                    model              = video.thumbnailUrl,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
                // Duration badge or remaining label
                val badgeLabel = remainingLabel ?: if (video.duration > 0)
                    formatDuration(video.duration) else null
                if (badgeLabel != null) {
                    DurationBadge(badgeLabel, Modifier.align(Alignment.BottomEnd))
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

            if (!modernStyle) Spacer(Modifier.height(10.dp))

            // Metadata footer: padded inside the card for MODERN, flush for CLASSIC
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = if (modernStyle)
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 11.dp, bottom = 13.dp)
                else Modifier.fillMaxWidth()
            ) {
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
                            Modifier.pointerInput(video.uploaderUrl) {
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
                        Icon(Icons.Rounded.MoreVert, null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f),
                            modifier = Modifier.size(18.dp))
                    }
                    // Telegram-style action sheet: rounded bottom sheet with a
                    // video preview header and big spaced action rows
                    if (showMenu) ModalBottomSheet(
                        onDismissRequest = { showMenu = false },
                        shape = appShape(24.dp, 24.dp, 0.dp, 0.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AsyncImage(
                                model = video.thumbnailUrl, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(width = 96.dp, height = 54.dp)
                                    .clip(appShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                            )
                            Text(video.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outline.copy(0.15f))
                        Column(Modifier.padding(bottom = 28.dp)) {
                        if (onAddToWatchLater != null) {
                            DropdownMenuItem(
                                text = { Text("Watch later") },
                                leadingIcon = { Icon(Icons.Rounded.BookmarkBorder, null, modifier = Modifier.size(18.dp)) },
                                onClick = { onAddToWatchLater(); showMenu = false }
                            )
                        }
                        if (onAddToFavorites != null) {
                            DropdownMenuItem(
                                text = { Text("Add to favorites") },
                                leadingIcon = { Icon(Icons.Rounded.FavoriteBorder, null, modifier = Modifier.size(18.dp)) },
                                onClick = { onAddToFavorites(); showMenu = false }
                            )
                        }
                        if (onNotInterested != null) {
                            DropdownMenuItem(
                                text = { Text("Not interested") },
                                leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null, modifier = Modifier.size(18.dp)) },
                                onClick = { onNotInterested(); showMenu = false }
                            )
                        }
                        if (onBlockChannel != null) {
                            DropdownMenuItem(
                                text = { Text("Don't recommend channel") },
                                leadingIcon = { Icon(Icons.Rounded.Block, null, modifier = Modifier.size(18.dp)) },
                                onClick = { onBlockChannel(); showMenu = false }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, video.url) }
                                context.startActivity(Intent.createChooser(i, "Share video"))
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open in browser") },
                            leadingIcon = { Icon(Icons.Rounded.OpenInBrowser, null, modifier = Modifier.size(18.dp)) },
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
                            text = { Text("Play next") },
                            leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                PlaybackQueue.addNext(video)
                                Toast.makeText(context, "Will play next", Toast.LENGTH_SHORT).show()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to queue") },
                            leadingIcon = { Icon(Icons.Rounded.QueueMusic, null, modifier = Modifier.size(18.dp)) },
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
}

@Composable
fun HeroVideoCard(video: VideoItem, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val corner = LocalThumbCorner.current
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
            .clip(appShape((corner + 4).dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
            .pointerInput(video.url) {
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
                .background(Color.White.copy(0.18f), appCircle()),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        if (video.duration > 0) {
            DurationBadge(
                formatDuration(video.duration),
                Modifier.align(Alignment.BottomEnd),
                outerPadding = 10.dp
            )
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
            .pointerInput(entity.url) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(appShape(LocalThumbCorner.current.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
        ) {
            AsyncImage(
                model              = entity.thumbnailUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
            // Progress bar (drawn first so the position badge sits on top of it,
            // not underneath — they share the same BottomStart corner)
            if (fraction > 0.01f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(0.3f)))
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary))
                }
            }
            Box(
                Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 7.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(0.88f), appShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("▶ ${formatDuration(entity.position / 1000)}",
                    color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(entity.title, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium, lineHeight = 15.sp)
    }
}

// Compact list row: thumbnail on the left, details on the right (YouTube search-result style)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    progressFraction: Float = 0f,
    onChannelClick: ((String) -> Unit)? = null,
    onAddToWatchLater: (() -> Unit)? = null,
    onAddToFavorites:  (() -> Unit)? = null,
    onNotInterested: (() -> Unit)? = null,
    onBlockChannel: (() -> Unit)? = null
) {
    val context  = LocalContext.current
    val haptic   = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
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
            .pointerInput(video.url) {
                detectTapGestures(
                    onPress     = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap       = { onClick() },
                    onLongPress = {
                        if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.width(168.dp).aspectRatio(16f / 9f)
                .clip(appShape((LocalThumbCorner.current + 2).dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
        ) {
            AsyncImage(
                model              = video.thumbnailUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
            if (video.duration > 0) {
                DurationBadge(
                    formatDuration(video.duration),
                    Modifier.align(Alignment.BottomEnd),
                    outerPadding = 5.dp,
                    fontSize = 10.sp
                )
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
        Column(Modifier.weight(1f)) {
            Text(video.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp,
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
                        Modifier.pointerInput(video.uploaderUrl) {
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
                Icon(Icons.Rounded.MoreVert, "More options",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f),
                    modifier = Modifier.size(16.dp))
            }
            // Telegram-style action sheet (same as VideoCard)
            if (showMenu) ModalBottomSheet(
                onDismissRequest = { showMenu = false },
                shape = appShape(24.dp, 24.dp, 0.dp, 0.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(
                        model = video.thumbnailUrl, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 96.dp, height = 54.dp)
                            .clip(appShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                    )
                    Text(video.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(0.15f))
                Column(Modifier.padding(bottom = 28.dp)) {
                if (onAddToWatchLater != null) {
                    DropdownMenuItem(
                        text = { Text("Watch later") },
                        leadingIcon = { Icon(Icons.Rounded.BookmarkBorder, null, modifier = Modifier.size(18.dp)) },
                        onClick = { onAddToWatchLater(); showMenu = false }
                    )
                }
                if (onAddToFavorites != null) {
                    DropdownMenuItem(
                        text = { Text("Add to favorites") },
                        leadingIcon = { Icon(Icons.Rounded.FavoriteBorder, null, modifier = Modifier.size(18.dp)) },
                        onClick = { onAddToFavorites(); showMenu = false }
                    )
                }
                if (onNotInterested != null) {
                    DropdownMenuItem(
                        text = { Text("Not interested") },
                        leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null, modifier = Modifier.size(18.dp)) },
                        onClick = { onNotInterested(); showMenu = false }
                    )
                }
                if (onBlockChannel != null) {
                    DropdownMenuItem(
                        text = { Text("Don't recommend channel") },
                        leadingIcon = { Icon(Icons.Rounded.Block, null, modifier = Modifier.size(18.dp)) },
                        onClick = { onBlockChannel(); showMenu = false }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, video.url) }
                        context.startActivity(Intent.createChooser(i, "Share video"))
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Play next") },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        PlaybackQueue.addNext(video)
                        Toast.makeText(context, "Will play next", Toast.LENGTH_SHORT).show()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    leadingIcon = { Icon(Icons.Rounded.QueueMusic, null, modifier = Modifier.size(18.dp)) },
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

internal fun formatViews(count: Long): String = when {
    // Trim the trailing ".0" BEFORE appending the suffix — with the "B"/"M"/"K" inside
    // the format string the string ends in a letter, so trimEnd('0') matched nothing
    // and every round count rendered as "2.0M" / "5.0K" instead of "2M" / "5K".
    // Locale.US forces a '.' decimal separator so trimEnd('.') also works on
    // comma-decimal locales (a French/German device otherwise showed "2,0M").
    count >= 1_000_000_000 -> "%.1f".format(java.util.Locale.US, count / 1_000_000_000.0).trimEnd('0').trimEnd('.') + "B"
    count >= 1_000_000     -> "%.1f".format(java.util.Locale.US, count / 1_000_000.0).trimEnd('0').trimEnd('.') + "M"
    count >= 1_000         -> "%.1f".format(java.util.Locale.US, count / 1_000.0).trimEnd('0').trimEnd('.') + "K"
    else                   -> count.toString()
}

internal fun formatDuration(seconds: Long): String {
    val t = seconds.coerceAtLeast(0)  // guard: a negative remaining/seek value rendered "0:-5"
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(java.util.Locale.US, h, m, s)
           else "%d:%02d".format(java.util.Locale.US, m, s)
}
