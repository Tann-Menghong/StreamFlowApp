package com.streamflow.data.model

data class SponsorSegment(
    val category: String,
    val startMs: Long,
    val endMs: Long
) {
    val color: Long get() = when (category) {
        "sponsor"        -> 0xFF00D400
        "selfpromo"      -> 0xFFFFFF00
        "interaction"    -> 0xFFCC00FF
        "intro"          -> 0xFF00FFFF
        "outro"          -> 0xFF0202ED
        "preview"        -> 0xFF008FD6
        "music_offtopic" -> 0xFFFF9900
        else             -> 0xFF777777
    }
    val label: String get() = when (category) {
        "sponsor"        -> "Sponsor"
        "selfpromo"      -> "Self-promo"
        "interaction"    -> "Interaction"
        "intro"          -> "Intro"
        "outro"          -> "Outro"
        "preview"        -> "Preview"
        "music_offtopic" -> "Music: Off-topic"
        else             -> "Segment"
    }
}
