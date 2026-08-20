package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.8.0"

    val notes = listOf(
        "The next button on your headphones, car stereo or smartwatch now works 🎧 — it used to do nothing at all, because only StreamFlow's own notification knew how to skip. Press and hold back to restart the episode, press twice to go back one",
        "Downloaded videos actually play now. They were being opened in the wrong player, which could not read files saved on your phone — so offline videos get background audio, lock-screen controls and resume position for the first time",
        "Fixed: the 5-second \"next video\" countdown with its Cancel button disappeared after the first video and never came back. Auto-play would jump straight to the next video with no way to stop it",
        "Picture-in-picture works again after watching more than one video in a row",
        "The mini player now tells you when playback is reconnecting or has stopped, instead of just going quiet — you had to open the full player to find out anything was wrong",
        "Gentler on the battery while listening with the screen off: the mini player was waking your phone twice a second to move a progress bar nobody could see",
        "Watch progress is saved less often but just as safely — a third of the background disk activity, with no extra risk of losing your place",
        "The video cache no longer takes 768 MB on a phone that is nearly full. It now sizes itself from your free storage instead of your RAM",
        "Replaying a video you just finished will auto-play the next one again (it only worked the first time)",
        "New: Settings › About › Playback log records what actually happened when playback stopped — errors, retries and network drops — so a problem can be reported instead of guessed at. Kept on your phone, cleared when you close the app"
    )
}
