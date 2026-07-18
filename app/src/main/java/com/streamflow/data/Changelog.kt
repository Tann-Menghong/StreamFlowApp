package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.19.0"

    val notes = listOf(
        "Sharing a YouTube link to StreamFlow while it's already open no longer launches a second copy of the app",
        "Fixed: after switching videos, the previous video's comments could show up on the new one",
        "Fixed: after switching videos, the previous video's sponsor-skip segments could make the new video jump at the wrong times",
        "Fixed: seek previews could show frames from the previous video after switching",
        "Fixed: rare crash when tapping picture-in-picture in fullscreen on some phones",
        "Fixed: rare crash while scrubbing with seek previews on certain videos",
        "Fixed: picking a video quality that fails to load no longer leaves the quality menu showing the wrong selection",
        "Update downloads now say when they fail, and retries restart the progress bar properly",
        "Fixed a network connection leak in video loading",
        "Backup description now lists everything that's included"
    )
}
