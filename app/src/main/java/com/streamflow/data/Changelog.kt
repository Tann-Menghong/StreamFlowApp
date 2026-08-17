package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.6.0"

    val notes = listOf(
        "Website tabs now load the full desktop layout 🖥 — Donghua, Drama, MKissa and your own tabs all match each other, every time",
        "Switch any tab back to the mobile layout from the ⋮ menu, or in Settings — it applies to all tabs at once so they never disagree",
        "Four new themes: Midnight Blue, Cinema Purple, Minimal Graphite and High Contrast",
        "Website tabs show a real loading percentage, and a proper Try again screen instead of a blank white page when a site is down",
        "Added a Forward button to website tabs — going back one page too far no longer means starting over",
        "Search remembers what you looked for and suggests it back, so you don't retype the same title every night",
        "Twelve more player buttons can now be used with a screen reader, including play, rewind and fast-forward",
        "Fixed: long option lists (Equalizer, themes, countries) were cut off on shorter screens with no way to scroll"
    )
}
