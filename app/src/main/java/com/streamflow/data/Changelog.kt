package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.13.0"

    val notes = listOf(
        "The loading circle now actually moves while a video is starting ⏳ — it was showing how much of the WHOLE video had buffered, so twenty seconds of data in a forty-minute video read as under 1% and the ring sat at zero, looking frozen through exactly the wait it is there to explain. It now shows how close the video is to being able to play, which is the thing you are waiting for",
        "On a slow connection you can now see the difference between progress and no progress at all while a video loads"
    )
}
