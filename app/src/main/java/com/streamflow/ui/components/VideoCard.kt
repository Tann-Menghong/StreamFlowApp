package com.streamflow.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.model.VideoItem

@Composable
fun VideoCard(video: VideoItem, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label         = "card_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(bottom = 20.dp)
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model             = video.thumbnailUrl,
                contentDescription = null,
                contentScale      = ContentScale.Crop,
                modifier          = Modifier.fillMaxSize()
            )
            if (video.duration > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text       = formatDuration(video.duration),
                        color      = Color.White,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = video.uploaderName.firstOrNull()?.uppercase() ?: "?",
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text      = video.title,
                    style     = MaterialTheme.typography.titleSmall,
                    maxLines  = 2,
                    overflow  = TextOverflow.Ellipsis,
                    color     = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = buildString {
                        append(video.uploaderName)
                        if (video.viewCount > 0) append("  ·  ${formatViews(video.viewCount)} views")
                    },
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun HeroVideoCard(video: VideoItem, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label         = "hero_scale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        AsyncImage(
            model              = video.thumbnailUrl,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.88f))))
        )
        if (video.duration > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .background(Color.Black.copy(0.75f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(formatDuration(video.duration), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .padding(end = 70.dp)
        ) {
            Text(
                video.title,
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(video.uploaderName)
                    if (video.viewCount > 0) append("  ·  ${formatViews(video.viewCount)} views")
                },
                color    = Color.White.copy(0.75f),
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ContinueWatchingCard(entity: HistoryEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
        ) {
            AsyncImage(
                model              = entity.thumbnailUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(0.88f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    "▶ ${formatDuration(entity.position / 1000)}",
                    color      = Color.White,
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            entity.title,
            fontSize   = 11.sp,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            lineHeight = 15.sp
        )
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
