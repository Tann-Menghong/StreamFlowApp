package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.9.0"

    val notes = listOf(
        "Video quality now drops itself when your connection can't keep up 📶 — after three stalls in a minute StreamFlow steps down a level and carries on from the same spot, instead of buffering forever at a quality your signal can't carry",
        "Opening a video survives a bad moment. A single timeout used to give you an error screen and a Retry button; now it quietly tries again, and waits for signal if you've lost it",
        "Error messages tell you the truth. \"Video unavailable\", \"not available in your country\" and \"StreamFlow couldn't read this video — check for an app update\" are three different problems and now read as three different problems",
        "Auto-play to the next episode finally respects your quality setting. The app skipped straight to Auto whenever it advanced on its own — including on mobile data, where you'd deliberately set it lower",
        "New: Settings › Playback opens with Playback health — whether your phone is allowed to keep playing in the background, whether the media notification is permitted, and whether battery saver is interfering. On Vivo, iQOO, Xiaomi and similar phones this is the setting that decides whether background audio works at all, and it used to be buried three levels down",
        "The player can now be used with a screen reader. Skip forward, skip back and play/pause were swipe and double-tap gestures only, which meant they were invisible to TalkBack and unusable without sight",
        "Fixed: a video the app couldn't open — deleted, private or blocked — is now skipped straight away instead of being retried three times first"
    )
}
