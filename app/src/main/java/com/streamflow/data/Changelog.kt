package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.55.0"

    val notes = listOf(
        "On-device AI is here! Free, no account, works offline — powered by a small AI model that runs on your phone",
        "AI video summaries — tap the ✦ button in the player to get the video in 4-6 bullet points",
        "Ask about this video — ask the AI questions and it answers from the video's captions",
        "One-time model download (about 550 MB) in Settings > AI; remove it anytime",
        "Needs Android 7.0 or newer"
    )
}
