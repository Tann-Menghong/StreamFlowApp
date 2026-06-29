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
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f, targetValue = 1600f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer_x"
    )
    return Brush.linearGradient(
        colors = listOf(Color.White.copy(0.03f), Color.White.copy(0.10f), Color.White.copy(0.03f)),
        start  = Offset(x - 500f, 0f),
        end    = Offset(x, 0f)
    )
}

@Composable
fun ShimmerVideoCard() {
    val brush = shimmerBrush()
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(brush))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(brush))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth(0.85f).height(13.dp).clip(RoundedCornerShape(5.dp)).background(brush))
                Box(Modifier.fillMaxWidth(0.55f).height(11.dp).clip(RoundedCornerShape(5.dp)).background(brush))
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
