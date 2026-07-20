package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "5.5.0"

    val notes = listOf(
        "Even stronger ad blocking 🛡️ — added dozens more pop-up/pop-under and push-ad networks, plus Google Safe Browsing on the streaming tabs to stop known malicious redirect pages before they load",
        "Live TV now only keeps the screen awake while a channel is actually playing — big battery win if you pause and walk away 🔋",
        "Faster, more reliable networking — every request now has a hard time-out so a bad connection can never freeze loading, and the home widget won't hang on a dead thumbnail",
        "Home feed stops quietly re-searching once you've truly reached the end, saving data and battery",
        "Fixed rare double-loads when scrolling channel and search results quickly, and a case where AI summaries wouldn't retry after a hiccup",
        "Weekly auto-backups now clean up after themselves — only the 6 most recent are kept"
    )
}
