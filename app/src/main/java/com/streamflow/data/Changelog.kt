package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.2.5"

    val notes = listOf(
        "FIXED the pop-up \"message\" ad for real 🛡️ — the banner with a photo, a clickbait headline and a Click Here / Hide button is now removed",
        "Root cause: any pop-up wider than 85% of the screen was being skipped as if it were the site's menu bar, so this ad was never even checked",
        "Only a true edge-to-edge top/bottom bar is left alone now; a floating card is always inspected, however wide it is",
        "Also recognises Hide / Dismiss / Skip buttons and Click Here / Download Now style ad buttons as ad signals",
        "Your scroll-to-top, settings and menu buttons are still protected — verified against a full test suite",
        "NEW: Change app version ⬇️ — Settings › About lets you install any release, so you can go back to an older version if a new one misbehaves",
        "Older versions are saved to your Downloads folder and the app walks you through the uninstall step Android requires"
    )
}
