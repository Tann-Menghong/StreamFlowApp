package com.streamflow.app.ui.components

import java.util.Locale

fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "LIVE"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatViewCount(count: Long): String {
    if (count < 0) return ""
    return when {
        count >= 1_000_000_000 -> String.format(Locale.US, "%.1fB views", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM views", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK views", count / 1_000.0)
        else -> "$count views"
    }
}
