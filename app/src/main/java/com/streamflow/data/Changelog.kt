package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.63.0"

    val notes = listOf(
        "Polish pack - the app now feels premium from the first second!",
        "Branded splash screen: the gradient play logo greets you on launch",
        "Every icon in the app switched to soft rounded style",
        "Bottom navigation is now a floating pill, matching the mini player",
        "Themed app icon on Android 13+ (tints with your wallpaper)",
        "Channel pages load with smooth skeleton shimmer instead of a spinner"
    )
}
