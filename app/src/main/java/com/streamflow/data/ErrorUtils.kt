package com.streamflow.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// Maps raw exceptions to short, user-readable messages.
fun friendlyError(e: Throwable): String {
    val name = e.javaClass.simpleName
    val msg = e.message ?: ""
    return when {
        e is UnknownHostException            -> "No internet connection. Check your network and try again."
        e is SocketTimeoutException          -> "Connection timed out. Please try again."
        name.contains("AgeRestricted")       -> "This video is age-restricted and can't be played."
        name.contains("Private")             -> "This video is private."
        name.contains("GeographicRestriction") -> "This video isn't available in your country."
        name.contains("Paid")                -> "This video requires a paid membership."
        name.contains("ContentNotAvailable") -> "This video is unavailable."
        name.contains("ReCaptcha")           -> "YouTube is asking for verification. Try again in a few minutes."
        msg.contains("No playable stream")   -> "Couldn't find a playable stream for this video."
        e is IOException                     -> "Network error. Check your connection and try again."
        else                                 -> "Something went wrong. Please try again."
    }
}
