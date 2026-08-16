package com.streamflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflow.ui.components.terminal.AsciiBar
import com.streamflow.ui.components.terminal.TerminalPane
import com.streamflow.ui.theme.LocalTerminalMode

/*
 * Shared dashboard vocabulary for the Library and Settings screens.
 *
 * Both screens were growing their own stat cells, bordered panes and bar charts
 * (LibraryScreen alone had StatCell, StatTile AND a hand-rolled day chart). One
 * set of primitives here means a dashboard reads identically wherever it appears
 * and picks up the TERMINAL treatment in one place rather than four.
 */

/** A bordered dashboard section. TerminalPane in CLI mode, flat card otherwise. */
@Composable
fun DashboardPane(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalTerminalMode.current) {
        TerminalPane(modifier = modifier, title = title, content = content)
        return
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (title != null) {
                Text(
                    title.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.75f),
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)
                )
            }
            content()
        }
    }
}

/**
 * One metric: a big confident number over its label.
 *
 * The number carries the meaning, so it gets the weight; the label and the
 * qualifier step down in size and colour rather than competing with it.
 */
@Composable
fun DashboardTile(
    value: String,
    label: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val terminal = LocalTerminalMode.current
    val valueColor = accent ?: if (terminal) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface
    if (terminal) {
        // A readout field, not a card: label above, value below, no chrome.
        // Panes already supply the framing, so nesting boxes would just add noise.
        Column(modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                label.uppercase(),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                style = MaterialTheme.typography.titleLarge
            )
            if (sub != null) Text(
                sub,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall
            )
        }
        return
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(0.6f)),
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp, color = valueColor, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(0.85f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * A labelled proportion: `Downloads  [||||||....]  62%`.
 *
 * TERMINAL renders the character bar the design system calls for; the other
 * styles get a filled track. Same data either way.
 */
@Composable
fun DashboardMeter(
    label: String,
    fraction: Float,
    valueLabel: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val safe = fraction.coerceIn(0f, 1f)
    Column(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f), maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(valueLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        if (LocalTerminalMode.current) {
            AsciiBar(progress = safe, cells = 24, color = color, showPercent = false)
        } else {
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(0.15f))
            ) {
                Box(
                    Modifier.fillMaxWidth(safe).fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp)).background(color)
                )
            }
        }
    }
}

/**
 * Activity over time as vertical bars — the 7-day watch chart.
 *
 * Heights are relative to the busiest day so the shape of a quiet week is still
 * readable; an absolute scale would flatten every low week into nothing. Empty
 * days keep a visible stub so the axis reads as seven slots, not five.
 */
@Composable
fun DashboardBarChart(
    values: List<Int>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    barHeight: Int = 28,
) {
    if (values.isEmpty()) return
    val maxValue = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val terminal = LocalTerminalMode.current
    val active = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.18f)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { i, v ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (v > 0) Text("$v", fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .width(if (terminal) 12.dp else 16.dp)
                        .height((4 + barHeight * v / maxValue).dp)
                        .then(
                            if (terminal) Modifier
                            else Modifier.clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        )
                        .background(if (v > 0) active else idle)
                )
                Spacer(Modifier.height(3.dp))
                labels.getOrNull(i)?.let {
                    Text(it, fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.8f),
                        maxLines = 1)
                }
            }
        }
    }
}

/**
 * A ranked row with a proportional bar behind it — "top channels", leaderboard
 * style. The bar is drawn behind the text rather than beside it so long channel
 * names keep the full row width instead of being squeezed into a column.
 */
@Composable
fun DashboardRankRow(
    rank: Int,
    name: String,
    value: String,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val terminal = LocalTerminalMode.current
    val safe = fraction.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp)) {
        Box(
            Modifier
                .fillMaxWidth(safe)
                .height(24.dp)
                .then(if (terminal) Modifier else Modifier.clip(RoundedCornerShape(6.dp)))
                .background(MaterialTheme.colorScheme.primary.copy(if (terminal) 0.22f else 0.14f))
        )
        Row(
            Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (terminal) "$rank." else "$rank",
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
