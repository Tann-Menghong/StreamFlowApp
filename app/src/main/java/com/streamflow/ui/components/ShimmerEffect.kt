package com.streamflow.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun shimmerBrush(): Brush {
    val colors = listOf(
        Color.White.copy(alpha = 0.04f),
        Color.White.copy(alpha = 0.13f),
        Color.White.copy(alpha = 0.04f),
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )
    return Brush.linearGradient(
        colors = colors,
        start = Offset(x - 400f, 0f),
        end   = Offset(x, 0f)
    )
}

@Composable
fun ShimmerVideoCard() {
    val brush = shimmerBrush()
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        // Thumbnail placeholder
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(brush)
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Avatar circle placeholder
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.88f)
                        .height(13.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
        }
    }
}

@Composable
fun ShimmerList(count: Int = 5) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        repeat(count) { ShimmerVideoCard() }
    }
}
