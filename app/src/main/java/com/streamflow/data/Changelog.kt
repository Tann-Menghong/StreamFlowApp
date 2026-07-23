package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.2.4"

    val notes = listOf(
        "Smarter, professional-grade pop-up blocker 🛡️ — it now weighs several ad clues together instead of one rigid rule, so far fewer ads slip through",
        "Catches floating gift/prize widgets that hide their picture in the background or their badge in a hidden layer (the ones that kept coming back)",
        "Now also scans sticky widgets and background-image banners, while carefully leaving your scroll-to-top and menu buttons alone",
        "Tap the shield icon anytime to instantly clear anything that still gets through"
    )
}
