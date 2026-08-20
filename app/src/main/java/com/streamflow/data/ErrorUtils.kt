package com.streamflow.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Why an extraction failed, in the terms that decide what to do about it.
 *
 * Playback recovery was thorough once a video had started and threadbare
 * before it: getVideoDetails made exactly one attempt, and every caller
 * collapsed the cause into `catch (_: Exception)`. So "the video was removed",
 * "you are in a tunnel" and "YouTube changed something the extractor cannot
 * parse" arrived at the UI as the same flat error — even though the first
 * cannot be retried, the second should be, and the third means the app itself
 * needs an update.
 *
 * This mirrors what PlaybackRecovery.plan already does for playback errors.
 */
enum class ExtractionError {
    /** No usable network at all. Worth waiting for, not worth retrying against. */
    OFFLINE,

    /** A timeout or a dropped connection. The same request will probably work. */
    TRANSIENT,

    /** Removed, private, or never existed. Retrying reaches the same answer. */
    UNAVAILABLE,

    /** Blocked in this country. */
    GEO_BLOCKED,

    /** Sign-in required to confirm age. */
    AGE_RESTRICTED,

    /** Members-only or purchase required. */
    PAID,

    /** YouTube wants a CAPTCHA. Time, not retries, is what fixes this. */
    CAPTCHA,

    /** The extractor could not parse the page — YouTube changed something.
     *  Retrying is pointless; a NewPipeExtractor bump is the actual fix. */
    EXTRACTOR_BROKEN,

    UNKNOWN
}

fun classifyExtractionError(e: Throwable): ExtractionError {
    val name = e.javaClass.simpleName
    val msg = e.message ?: ""
    return when {
        e is UnknownHostException -> ExtractionError.OFFLINE
        e is SocketTimeoutException -> ExtractionError.TRANSIENT
        name.contains("AgeRestricted") -> ExtractionError.AGE_RESTRICTED
        name.contains("GeographicRestriction") -> ExtractionError.GEO_BLOCKED
        name.contains("Paid") -> ExtractionError.PAID
        name.contains("ReCaptcha") -> ExtractionError.CAPTCHA
        name.contains("Private") ||
            name.contains("SoundCloudGoPlus") ||
            name.contains("ContentNotAvailable") ||
            name.contains("ContentNotSupported") -> ExtractionError.UNAVAILABLE
        // NewPipe raises ParsingException (and ExtractionException) when the
        // page shape it expects is gone. That is our problem, not the network's.
        name.contains("Parsing") -> ExtractionError.EXTRACTOR_BROKEN
        msg.contains("No playable stream") -> ExtractionError.EXTRACTOR_BROKEN
        e is IOException -> ExtractionError.TRANSIENT
        else -> ExtractionError.UNKNOWN
    }
}

/**
 * Only two classes are worth another attempt. Retrying anything else spends
 * battery to reach a conclusion we already have — the same reasoning that keeps
 * PlaybackRecovery from retrying a FATAL plan.
 */
val ExtractionError.isRetryable: Boolean
    get() = this == ExtractionError.TRANSIENT || this == ExtractionError.OFFLINE

/** True when waiting for a connection is the sensible first move. */
val ExtractionError.needsNetwork: Boolean
    get() = this == ExtractionError.OFFLINE

fun ExtractionError.userMessage(): String = when (this) {
    ExtractionError.OFFLINE -> "No internet connection. Check your network and try again."
    ExtractionError.TRANSIENT -> "Network error. Check your connection and try again."
    ExtractionError.UNAVAILABLE -> "This video is unavailable."
    ExtractionError.GEO_BLOCKED -> "This video isn't available in your country."
    ExtractionError.AGE_RESTRICTED -> "This video is age-restricted and can't be played."
    ExtractionError.PAID -> "This video requires a paid membership."
    ExtractionError.CAPTCHA -> "YouTube is asking for verification. Try again in a few minutes."
    // Named honestly. "Something went wrong" sends the user to retry forever;
    // this tells them the truth, which is that a newer StreamFlow is the fix.
    ExtractionError.EXTRACTOR_BROKEN ->
        "StreamFlow couldn't read this video. YouTube may have changed — check Settings for an app update."
    ExtractionError.UNKNOWN -> "Something went wrong. Please try again."
}

// Maps raw exceptions to short, user-readable messages.
fun friendlyError(e: Throwable): String = classifyExtractionError(e).userMessage()
