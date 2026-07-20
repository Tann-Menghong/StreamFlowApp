package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "5.3.1"

    val notes = listOf(
        "Fixed notification pop-up ads 🔕 — the Donghua/Drama/MKissa sites can no longer sign you up for spam notification ads",
        "Blocks web-push ad networks, the background service workers they use, and auto-denies notification/location permission prompts",
        "Any existing ad subscription from a previous visit is torn down automatically",
        "Plus the Premium Minimal redesign of Settings & Library from 5.3.0"
    )
}
