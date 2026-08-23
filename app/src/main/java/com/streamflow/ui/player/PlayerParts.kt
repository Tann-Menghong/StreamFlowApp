package com.streamflow.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.streamflow.ui.theme.appShape
import com.streamflow.ui.theme.appShapePercent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pieces of the player screen that do not need its state.
 *
 * PlayerScreen.kt is one composable of nearly three thousand lines, and that
 * size has cost real bugs -- two of the last four fixed there were the same work
 * done twice, invisible because nobody can hold the file in their head. This is
 * the first cut: everything already written as a top-level helper, moved out
 * whole. No state moves, no call sites change, nothing is rewritten.
 *
 * The one non-mechanical part: top-level `private` in Kotlin is FILE-scoped, so
 * these widen to `internal` or PlayerScreen.kt can no longer see them.
 */
internal fun fmtMs(ms: Long): String {
    val s = ms / 1000L; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(java.util.Locale.US, h, m, sec)
           else "%d:%02d".format(java.util.Locale.US, m, sec)
}

// Makes timestamps (seek) and links (open) tappable in the video description
internal fun annotateDescription(
    text: String,
    accent: Color
): androidx.compose.ui.text.AnnotatedString {
    val tsRegex = Regex("\\b(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})\\b")
    val urlRegex = Regex("https?://\\S+")
    data class Marker(val range: IntRange, val tag: String, val value: String)

    val markers = ArrayList<Marker>()
    urlRegex.findAll(text).forEach { m -> markers.add(Marker(m.range, "url", m.value)) }
    tsRegex.findAll(text).forEach { m ->
        val h = m.groupValues[1].toLongOrNull() ?: 0L
        val min = m.groupValues[2].toLongOrNull() ?: 0L
        val sec = m.groupValues[3].toLongOrNull() ?: 0L
        markers.add(Marker(m.range, "timestamp", ((h * 3600 + min * 60 + sec) * 1000L).toString()))
    }

    return androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        var lastEnd = -1
        markers.sortedBy { it.range.first }.forEach { mk ->
            if (mk.range.first <= lastEnd) return@forEach // skip overlaps (e.g. time inside a URL)
            lastEnd = mk.range.last
            addStyle(
                androidx.compose.ui.text.SpanStyle(color = accent, fontWeight = FontWeight.SemiBold),
                mk.range.first, mk.range.last + 1
            )
            addStringAnnotation(mk.tag, mk.value, mk.range.first, mk.range.last + 1)
        }
    }
}

// Chapter boundary tick marks drawn over a seek Slider (draw-only, never
// intercepts touches)
@Composable
internal fun ChapterTicks(
    chapters: List<com.streamflow.data.model.VideoChapter>,
    durationMs: Long,
    modifier: Modifier
) {
    if (chapters.size < 2 || durationMs <= 0L) return
    androidx.compose.foundation.Canvas(modifier) {
        val tickW = 2.dp.toPx()
        val tickH = 5.dp.toPx()
        chapters.drop(1).forEach { ch ->
            val x = (ch.startMs.toFloat() / durationMs) * size.width
            drawRect(
                color = Color.Black.copy(0.6f),
                topLeft = Offset(x - tickW / 2, size.height / 2 - tickH / 2),
                size = androidx.compose.ui.geometry.Size(tickW, tickH)
            )
        }
    }
}

// Amber dots marking the user's "clip moment" bookmarks on the seekbar
@Composable
internal fun BookmarkTicks(
    positionsMs: List<Long>,
    durationMs: Long,
    modifier: Modifier
) {
    if (positionsMs.isEmpty() || durationMs <= 0L) return
    androidx.compose.foundation.Canvas(modifier) {
        val r = 3.dp.toPx()
        positionsMs.forEach { pos ->
            val x = (pos.toFloat() / durationMs).coerceIn(0f, 1f) * size.width
            drawCircle(
                color = Color(0xFFFFC107),
                radius = r,
                center = Offset(x, size.height / 2)
            )
        }
    }
}

// Draws the storyboard frame for a playback position by cropping the right
// cell out of YouTube's sprite-sheet page (loaded and cached via Coil)
@Composable
internal fun StoryboardPreview(
    sb: com.streamflow.data.model.Storyboard,
    positionMs: Long,
    modifier: Modifier
) {
    val perPage = (sb.framesPerPageX * sb.framesPerPageY).coerceAtLeast(1)
    val frameIdx = (positionMs / sb.durationPerFrameMs.coerceAtLeast(1))
        .toInt().coerceIn(0, (sb.totalCount - 1).coerceAtLeast(0))
    val pageIdx = (frameIdx / perPage).coerceIn(0, sb.urls.size - 1)
    val inPage = frameIdx % perPage
    val col = inPage % sb.framesPerPageX
    val rowIdx = inPage / sb.framesPerPageX

    val context = LocalContext.current
    var pageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    // Keyed on the page URL, not the index: a new video's storyboard usually
    // starts at the same pageIdx (0), which kept showing the OLD video's frames
    LaunchedEffect(sb.urls[pageIdx]) {
        // allowHardware(false): Canvas cropping needs a software bitmap
        val request = coil.request.ImageRequest.Builder(context)
            .data(sb.urls[pageIdx])
            .allowHardware(false)
            .build()
        val drawable = try { coil.Coil.imageLoader(context).execute(request).drawable } catch (_: Exception) { null }
        (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let {
            pageBitmap = it.asImageBitmap()
        }
    }

    val bmp = pageBitmap ?: return
    androidx.compose.foundation.Canvas(modifier.background(Color.Black)) {
        drawImage(
            image = bmp,
            srcOffset = androidx.compose.ui.unit.IntOffset(col * sb.frameWidth, rowIdx * sb.frameHeight),
            srcSize = androidx.compose.ui.unit.IntSize(sb.frameWidth, sb.frameHeight),
            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
        )
    }
}

// YouTube-style pill action chip (icon + label); `active` fills it with the accent
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = appShape(20.dp),
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            Modifier.height(34.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, null,
                tint = if (active) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
        }
    }
}

// Average color of the video thumbnail (tiny 24px decode) for the ambient glow
internal suspend fun averageThumbColor(context: android.content.Context, url: String): Color? = try {
    val req = coil.request.ImageRequest.Builder(context)
        .data(url).size(24).allowHardware(false).build()
    val bmp = (coil.Coil.imageLoader(context).execute(req).drawable
        as? android.graphics.drawable.BitmapDrawable)?.bitmap
    if (bmp == null) null else {
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val c = bmp.getPixel(x, y)
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                x += 2
            }
            y += 2
        }
        if (n == 0) null
        else Color(red = (r / n) / 255f, green = (g / n) / 255f, blue = (b / n) / 255f)
    }
} catch (_: Exception) { null }

/**
 * Shown when automatic playback recovery has stopped trying.
 *
 * Deliberately a scrim over the video rather than a toast: a toast disappears
 * after two seconds and leaves the same unexplained frozen frame behind. This
 * states what happened and gives the one action that helps — a full re-extract,
 * not another prepare() of the URL that already failed.
 */

/**
 * What the quality button should SAY.
 *
 * An app-imposed ceiling wins over the height this screen extracted, because
 * after an automatic step-down the service re-extracted underneath it and the
 * screen's own number is stale. Reporting a quality the user is demonstrably
 * not watching is worse than reporting nothing.
 */
internal fun qualityButtonLabel(auto: Boolean, currentQuality: Int, loweredTo: String?): String =
    when {
        loweredTo != null -> com.streamflow.data.QualityLadder.label(loweredTo)
        auto && currentQuality > 0 -> "Auto (${currentQuality}p)"
        currentQuality > 0 -> "${currentQuality}p"
        else -> "Auto"
    }

/**
 * Say why the quality is lower than the user asked for.
 *
 * The step-down fires a toast once and is then invisible forever, so a user who
 * looks at the menu two minutes later has no way to find out why their video is
 * soft -- or that picking anything here overrides it. One sentence, only while
 * an override is actually in force.
 */
@Composable
internal fun LoweredQualityNotice(loweredTo: String?) {
    if (loweredTo == null) return
    Text(
        "Lowered to ${com.streamflow.data.QualityLadder.label(loweredTo)} " +
            "for your connection. Pick a quality to override.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .widthIn(max = 240.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
internal fun BoxScope.PlaybackStoppedOverlay(message: String, onRetry: () -> Unit) {
    Box(
        Modifier
            .matchParentSize()
            .background(Color.Black.copy(0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = Color.White.copy(0.85f),
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                color = Color.White,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}

/**
 * The rounded scrim shown on the tapped half while double-tap seeks accumulate.
 *
 * Reads three values and writes none -- the whole reason it could be lifted out
 * of PlayerScreen unchanged.
 */
@Composable
internal fun SeekFeedbackOverlay(visible: Boolean, direction: Int, accumulatedMs: Long) {
    if (!visible || direction == 0) return
    val back = direction < 0
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(if (back) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.3f)
                .clip(
                    if (back) appShapePercent(0, 100, 100, 0)
                    else appShapePercent(100, 0, 0, 100)
                )
                .background(Color.White.copy(0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (back) "◀◀◀" else "▶▶▶",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("${accumulatedMs / 1000}s",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
