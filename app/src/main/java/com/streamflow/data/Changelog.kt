package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.3.1"

    val notes = listOf(
        "FIXED: video not showing 🔴 — 6.3.0 broke the picture on some devices (sound played, screen stayed black). Sorry about that; please update",
        "The cause was a playback setting added in 6.3.0 that let the app pick video formats your device can't actually decode. It's been removed",
        "NEW: add your own website tabs 🌐 — Settings › Home › Your own tabs. Type any address and it becomes a tab",
        "Your tabs use the same ad-blocking browser as Donghua and Drama, so pop-up blocking and fullscreen work the same way",
        "Each tab keeps its own logins and cookies, and picks its own icon and name",
        "The bottom bar now scrolls sideways when you have lots of tabs, instead of squashing them together"
    )
}
