package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.0.0"

    val notes = listOf(
        "New: App Lock 🔒 — turn it on in Settings › Playback to require your fingerprint or PIN every time you open StreamFlow",
        "Locks instantly on cold start and whenever you return from the background, with no flash of content",
        "Uses your device's own secure unlock (biometrics or PIN) — nothing new to set up",
        "Small reliability tidy-ups under the hood"
    )
}
