package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.1.1"

    val notes = listOf(
        "Swipe-to-minimize polished!",
        "A gentle vibration now confirms exactly when your pull will minimize the video",
        "Fast flicks minimize even from a short pull - no need to drag all the way",
        "Letting go too early now springs back smoothly instead of snapping instantly",
        "The video visibly rounds into a mini-player shape as you pull it down"
    )
}
