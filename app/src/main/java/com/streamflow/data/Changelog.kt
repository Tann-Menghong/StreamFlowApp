package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "5.6.0"

    val notes = listOf(
        "Fixed bookmarks 🔖 — opening a saved moment for a video you hadn't watched yet now correctly jumps to that moment instead of starting from the beginning",
        "Smoother background & headset listening — audio no longer stalls when the screen turns off, and resuming from a Bluetooth button respects your data-saver quality",
        "More reliable app updates — the update download now survives slow connections instead of timing out on big files",
        "Notifications now cover ALL your bell-enabled channels, even if you follow more than 20 — they take turns each check",
        "Even more pop-up / redirect ad patterns blocked on the streaming tabs",
        "Faster thumbnails (shared warm connections) and less wasted network while typing a search"
    )
}
