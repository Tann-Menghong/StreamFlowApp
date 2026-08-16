package com.streamflow.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflow.ui.theme.LocalTerminalMode

/**
 * The video open/buffer indicator: an animated ring with a live percentage.
 *
 * Honesty about what the number means, because it comes from two different
 * sources:
 *
 *  - Before playback exists there is no download percentage to report — nothing
 *    in the extractor streams progress. So the ring shows PIPELINE position:
 *    each stage sets a real weight when that step actually completes. It is a
 *    genuine "how far through opening this video are we", not a timer that
 *    creeps upward on its own.
 *  - Once the player is buffering, the number switches to the player's ACTUAL
 *    buffered percentage.
 *
 * The value is animated between updates so the ring sweeps smoothly instead of
 * jumping, and a slow rotation underneath keeps it alive during a long stage.
 */
@Composable
fun VideoLoadingIndicator(
    /** 0f..1f. Stage weight while opening, real buffered fraction once playing. */
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 76.dp,
) {
    val terminal = LocalTerminalMode.current
    val accent = MaterialTheme.colorScheme.primary
    val track = if (terminal) MaterialTheme.colorScheme.outline
                else Color.White.copy(0.18f)

    // Ease toward each new value: stages arrive in jumps, and an un-animated
    // ring snapping from 12% to 40% reads as a glitch rather than progress.
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "load_progress"
    )
    // A continuous sweep under the arc so the indicator never looks frozen while
    // a single slow stage (extraction on a bad connection) is in flight.
    val spin by rememberInfiniteTransition(label = "load_spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "load_spin_deg"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val stroke = if (terminal) 3.dp.toPx() else 4.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                // Track
                drawArc(
                    color = track,
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                // Progress arc — starts at 12 o'clock and sweeps clockwise.
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(
                        width = stroke,
                        // Square ends in TERMINAL: the design system has no round
                        // caps any more than it has round corners.
                        cap = if (terminal) StrokeCap.Butt else StrokeCap.Round
                    )
                )
                // Live tick riding on the spin, so there is always motion.
                drawArc(
                    color = accent.copy(alpha = 0.55f),
                    startAngle = spin - 90f,
                    sweepAngle = 18f,
                    useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
            }
            Text(
                "${(animated * 100).toInt()}%",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (terminal) accent else Color.White
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (terminal) "> ${label.lowercase()}" else label,
            fontSize = 12.sp,
            color = if (terminal) MaterialTheme.colorScheme.onSurfaceVariant
                    else Color.White.copy(0.75f)
        )
    }
}
