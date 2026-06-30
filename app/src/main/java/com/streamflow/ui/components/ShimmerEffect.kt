package com.streamflow.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
    val base      = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1800f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label         = "shimmer_x"
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start  = Offset(x - 600f, 0f),
        end    = Offset(x, 0f)
    )
}

@Composable
fun ShimmerVideoCard() {
    val brush = shimmerBrush()
    val bg    = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(bg)) {
            Box(Modifier.fillMaxSize().background(brush))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(bg)) {
                Box(Modifier.fillMaxSize().background(brush))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth(0.88f).height(13.dp).clip(RoundedCornerShape(5.dp)).background(bg)) {
                    Box(Modifier.fillMaxSize().background(brush))
                }
                Box(Modifier.fillMaxWidth(0.55f).height(11.dp).clip(RoundedCornerShape(5.dp)).background(bg)) {
                    Box(Modifier.fillMaxSize().background(brush))
                }
            }
        }
    }
}

@Composable
fun ShimmerList(count: Int = 5) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        repeat(count) { ShimmerVideoCard() }
    }
}
