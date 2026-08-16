package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.3.3"

    val notes = listOf(
        "VIDEO IS FIXED ✅ — found the real cause this time, and it explains everything",
        "A buffering indicator added in 6.3.0 was placed above the code that draws the video, and Kotlin only runs the first match — so the video view was never created at all",
        "That's why sound played while the screen stayed black, and why my two earlier attempts changed nothing",
        "Added an automatic check that fails the build if this mistake is ever made again",
        "Sorry it took three tries. Your website tabs and everything else from 6.3.1 are unchanged"
    )
}
