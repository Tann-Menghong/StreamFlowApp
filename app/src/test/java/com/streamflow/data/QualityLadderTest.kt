package com.streamflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class QualityLadderTest {

    // ── cap: a ceiling, never a floor ────────────────────────────────────────

    @Test
    fun `cap lowers a higher preference`() {
        assertEquals("480P", QualityLadder.cap("1080P", "480P"))
        assertEquals("480P", QualityLadder.cap("720P", "480P"))
    }

    @Test
    fun `cap never raises a lower preference`() {
        // Someone who deliberately picked 360P to save more than 480p would must
        // not be pushed back up by battery saver.
        assertEquals("360P", QualityLadder.cap("360P", "480P"))
    }

    @Test
    fun `cap resolves AUTO to the cap itself`() {
        assertEquals("480P", QualityLadder.cap(QualityLadder.AUTO, "480P"))
    }

    @Test
    fun `cap leaves an unrecognised value alone`() {
        assertEquals("4320P", QualityLadder.cap("4320P", "480P"))
    }

    // ── stepDown ─────────────────────────────────────────────────────────────

    @Test
    fun `step down walks one rung`() {
        assertEquals("720P", QualityLadder.stepDown("1080P", 1080))
        assertEquals("480P", QualityLadder.stepDown("720P", 1080))
        assertEquals("360P", QualityLadder.stepDown("480P", 1080))
    }

    @Test
    fun `step down from AUTO uses what this device would actually have played`() {
        // On a phone whose AUTO ceiling is 720p, stepping down from AUTO must
        // reach 480p. Returning 720p would re-extract the same stream the player
        // is already stalling on and look like the app doing nothing.
        assertEquals("480P", QualityLadder.stepDown(QualityLadder.AUTO, 720))
        assertEquals("720P", QualityLadder.stepDown(QualityLadder.AUTO, 1080))
    }

    @Test
    fun `there is nothing below the lowest rung`() {
        assertNull(QualityLadder.stepDown("360P", 1080))
        assertNull(QualityLadder.stepDown(QualityLadder.AUTO, 360))
    }

    @Test
    fun `labels read as a sentence would`() {
        assertEquals("480p", QualityLadder.label("480P"))
        assertEquals("Auto", QualityLadder.label(QualityLadder.AUTO))
    }
}

/**
 * The classifier decides whether opening a video is retried at all, so the
 * distinction between "the network hiccuped" and "the video is gone" has to be
 * exact — retrying the second wastes battery to reach an answer we already had.
 */
class ExtractionErrorTest {

    private class AgeRestrictedContentException : Exception("age")
    private class GeographicRestrictionException : Exception("geo")
    private class PrivateContentException : Exception("private")
    private class ContentNotAvailableException : Exception("gone")
    private class ReCaptchaException : Exception("captcha")
    private class PaidContentException : Exception("paid")
    private class ParsingException : Exception("could not parse")

    @Test
    fun `no host means offline`() {
        val kind = classifyExtractionError(UnknownHostException("youtube.com"))
        assertEquals(ExtractionError.OFFLINE, kind)
        assertTrue(kind.isRetryable)
        assertTrue(kind.needsNetwork)
    }

    @Test
    fun `timeouts and io failures are transient and retryable`() {
        assertEquals(ExtractionError.TRANSIENT, classifyExtractionError(SocketTimeoutException()))
        assertEquals(ExtractionError.TRANSIENT, classifyExtractionError(IOException("reset")))
        assertTrue(classifyExtractionError(IOException("reset")).isRetryable)
    }

    @Test
    fun `permanent failures are never retried`() {
        val permanent = listOf(
            AgeRestrictedContentException() to ExtractionError.AGE_RESTRICTED,
            GeographicRestrictionException() to ExtractionError.GEO_BLOCKED,
            PrivateContentException() to ExtractionError.UNAVAILABLE,
            ContentNotAvailableException() to ExtractionError.UNAVAILABLE,
            ReCaptchaException() to ExtractionError.CAPTCHA,
            PaidContentException() to ExtractionError.PAID
        )
        for ((e, expected) in permanent) {
            val kind = classifyExtractionError(e)
            assertEquals(e.javaClass.simpleName, expected, kind)
            assertFalse("$expected must not be retried", kind.isRetryable)
        }
    }

    @Test
    fun `a parsing failure blames the extractor, not the network`() {
        val kind = classifyExtractionError(ParsingException())
        assertEquals(ExtractionError.EXTRACTOR_BROKEN, kind)
        assertFalse(kind.isRetryable)
        // The message has to say an app update is the fix. "Something went
        // wrong" sends the user retrying a thing that cannot succeed.
        assertTrue(kind.userMessage().contains("update"))
    }

    @Test
    fun `no playable stream is an extractor problem`() {
        assertEquals(
            ExtractionError.EXTRACTOR_BROKEN,
            classifyExtractionError(Exception("No playable stream found"))
        )
    }

    @Test
    fun `every classification produces a non-empty user message`() {
        for (kind in ExtractionError.values()) {
            assertTrue(kind.name, kind.userMessage().length > 10)
        }
    }

    @Test
    fun `friendlyError still routes through the classifier`() {
        assertEquals(
            ExtractionError.OFFLINE.userMessage(),
            friendlyError(UnknownHostException("youtube.com"))
        )
    }
}
