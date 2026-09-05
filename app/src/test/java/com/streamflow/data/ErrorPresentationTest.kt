package com.streamflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorPresentationTest {

    // The rule this file exists to enforce. Home and Search both offered
    // "Retry" for every failure, including the ones ErrorUtils had already
    // classified as not worth retrying -- so the app suggested an action it
    // knew would fail, and the user pressed it until they gave up.
    @Test fun `no error offers a plain retry against its own isRetryable verdict`() {
        for (e in ExtractionError.entries) {
            val view = ErrorPresentation.of(e)
            if (view.action == ErrorPresentation.Action.RETRY && !e.isRetryable) {
                assertTrue(
                    "$e offers RETRY but ErrorUtils says it is not retryable, and it " +
                        "is not a documented manual-retry exception",
                    e in ErrorPresentation.MANUAL_RETRY_EXCEPTIONS
                )
            }
        }
    }

    // The exception list is a deliberate carve-out, not a dumping ground: if it
    // grows to cover most errors the invariant above stops meaning anything.
    @Test fun `the manual-retry carve-out stays small and deliberate`() {
        assertEquals(
            setOf(ExtractionError.CAPTCHA, ExtractionError.UNKNOWN),
            ErrorPresentation.MANUAL_RETRY_EXCEPTIONS
        )
    }

    @Test fun `a broken extractor sends the user to the update check, not retry`() {
        val v = ErrorPresentation.of(ExtractionError.EXTRACTOR_BROKEN)
        assertEquals(ErrorPresentation.Action.CHECK_FOR_UPDATE, v.action)
        assertEquals("Check for update", v.actionLabel)
    }

    @Test fun `being offline is not the same action as a timeout`() {
        assertEquals(
            ErrorPresentation.Action.WAIT_FOR_NETWORK,
            ErrorPresentation.of(ExtractionError.OFFLINE).action
        )
        assertEquals(
            ErrorPresentation.Action.RETRY,
            ErrorPresentation.of(ExtractionError.TRANSIENT).action
        )
    }

    // A deleted video, a private video and a members-only video are all dead
    // ends. Offering a button implies the app can do something about it.
    @Test fun `dead ends offer no button`() {
        for (e in listOf(
            ExtractionError.UNAVAILABLE,
            ExtractionError.PRIVATE,
            ExtractionError.GEO_BLOCKED,
            ExtractionError.AGE_RESTRICTED,
            ExtractionError.PAID,
        )) {
            val v = ErrorPresentation.of(e)
            assertEquals("$e should be a dead end", ErrorPresentation.Action.DISMISS, v.action)
            assertNull("$e should have no button label", v.actionLabel)
        }
    }

    @Test fun `every error has a title and a body`() {
        for (e in ExtractionError.entries) {
            val v = ErrorPresentation.of(e)
            assertTrue("$e has a blank title", v.title.isNotBlank())
            assertTrue("$e has a blank body", v.body.isNotBlank())
            assertNotNull("$e has no glyph", v.glyph)
        }
    }

    // Titles are headings, not sentences: they sit above the body text in the
    // shared ErrorState and a full stop there reads as truncated copy.
    @Test fun `titles are headings, not sentences`() {
        for (e in ExtractionError.entries) {
            val title = ErrorPresentation.of(e).title
            assertTrue("$e title ends in a full stop: \"$title\"", !title.endsWith("."))
            assertTrue("$e title is too long to fit one line: \"$title\"", title.length <= 42)
        }
    }

    // A locked glyph on a network failure, or an offline glyph on a deleted
    // video, tells the user the wrong thing before they read a word.
    @Test fun `glyphs match the class of failure`() {
        assertEquals(ErrorPresentation.Glyph.OFFLINE, ErrorPresentation.of(ExtractionError.OFFLINE).glyph)
        assertEquals(ErrorPresentation.Glyph.BROKEN, ErrorPresentation.of(ExtractionError.EXTRACTOR_BROKEN).glyph)
        assertEquals(ErrorPresentation.Glyph.UNAVAILABLE, ErrorPresentation.of(ExtractionError.UNAVAILABLE).glyph)
        for (e in listOf(ExtractionError.PRIVATE, ExtractionError.GEO_BLOCKED, ExtractionError.PAID)) {
            assertEquals(ErrorPresentation.Glyph.LOCKED, ErrorPresentation.of(e).glyph)
        }
    }

    // classifyExtractionError is the only way an ExtractionError is ever
    // produced, so the two halves must stay connected end to end.
    @Test fun `a real offline exception reaches the offline presentation`() {
        val v = ErrorPresentation.of(classifyExtractionError(java.net.UnknownHostException("no dns")))
        assertEquals("You're offline", v.title)
        assertEquals(ErrorPresentation.Action.WAIT_FOR_NETWORK, v.action)
    }

    @Test fun `a parsing failure reaches the update-check presentation`() {
        class ParsingException(msg: String) : Exception(msg)
        val v = ErrorPresentation.of(classifyExtractionError(ParsingException("bad json")))
        assertEquals(ErrorPresentation.Action.CHECK_FOR_UPDATE, v.action)
    }
}
