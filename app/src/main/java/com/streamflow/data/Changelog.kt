package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.0.1"

    val notes = listOf(
        "Fixed: swipe-down-to-minimize on the video now works reliably",
        "It no longer fights with scrolling the page - pull down from the top and it smoothly shrinks into the mini player"
    )
}
