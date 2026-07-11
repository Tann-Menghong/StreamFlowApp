package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.54.0"

    val notes = listOf(
        "Create your own category chips — type any topic in Customize Home and it becomes a Home chip",
        "Optional Search tab in the bottom bar (Settings > Home)",
        "Custom accent color now has Depth and Brightness sliders, not just hue",
        "Font style — choose Default, Serif or Monospace for the whole app",
        "Default Library tab — open Library straight to History, Watch Later, etc.",
        "Remove individual recent searches with the new ✕ button"
    )
}
