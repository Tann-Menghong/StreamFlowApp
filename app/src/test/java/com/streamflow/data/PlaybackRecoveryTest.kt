package com.streamflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryTest {

    // ── plan(): transient network failures must be retried in place ──────────

    @Test
    fun `connection failure retries the same stream`() {
        assertEquals(
            RecoveryPlan.RETRY,
            PlaybackRecovery.plan(PlaybackRecovery.CODE_IO_NETWORK_CONNECTION_FAILED)
        )
    }

    @Test
    fun `connection timeout retries the same stream`() {
        assertEquals(
            RecoveryPlan.RETRY,
            PlaybackRecovery.plan(PlaybackRecovery.CODE_IO_NETWORK_CONNECTION_TIMEOUT)
        )
    }

    @Test
    fun `player timeout retries the same stream`() {
        assertEquals(RecoveryPlan.RETRY, PlaybackRecovery.plan(PlaybackRecovery.CODE_TIMEOUT))
    }

    @Test
    fun `server errors retry rather than re-extract`() {
        // 500-599 is the CDN having a bad moment; the signed URL is still valid.
        for (status in listOf(500, 502, 503, 504)) {
            assertEquals(
                "status $status",
                RecoveryPlan.RETRY,
                PlaybackRecovery.plan(PlaybackRecovery.CODE_IO_BAD_HTTP_STATUS, status)
            )
        }
    }

    @Test
    fun `rate limiting and request timeout retry`() {
        assertEquals(
            RecoveryPlan.RETRY,
            PlaybackRecovery.plan(PlaybackRecovery.CODE_IO_BAD_HTTP_STATUS, 429)
        )
        assertEquals(
            RecoveryPlan.RETRY,
            PlaybackRecovery.plan(PlaybackRecovery.CODE_IO_BAD_HTTP_STATUS, 408)
        )
    }

    // ── plan(): rejected URLs must be re-extracted, never retried ────────────

    @Test
    fun `403 re-extracts because the signed url is dead`() {
        // The whole point: retrying a 403 can only ever produce another 403.
        assertEquals(
            RecoveryPlan.REEXTRACT,
            PlaybackRecovery.plan(PlaybackRecovery.CODE_IO_BAD_HTTP_STATUS, 403)
        )
    }

    @Test
    fun `401 and 410 re-extract`() {
        assertEquals(
            RecoveryPlan.REEXTRACT,
            PlaybackRecovery.plan(PlaybackRecovery.CODE_IO_BAD_HTTP_STATUS, 401)
        )
        assertEquals(
            RecoveryPlan.REEXTRACT,
            PlaybackRecovery.plan(PlaybackRecovery.CODE_IO_BAD_HTTP_STATUS, 410)
        )
    }

    @Test
    fun `remote file not found re-extracts`() {
        assertEquals(
            RecoveryPlan.REEXTRACT,
            PlaybackRecovery.plan(PlaybackRecovery.CODE_IO_FILE_NOT_FOUND)
        )
    }

    @Test
    fun `html error page instead of media re-extracts`() {
        assertEquals(
            RecoveryPlan.REEXTRACT,
            PlaybackRecovery.plan(PlaybackRecovery.CODE_IO_INVALID_HTTP_CONTENT_TYPE)
        )
    }

    // ── plan(): genuinely unplayable media must stop ─────────────────────────

    @Test
    fun `decoder and parsing failures are fatal`() {
        // 3001 = container malformed, 4001 = decoder init failed. Retrying these
        // burns battery to reach the same answer.
        assertEquals(RecoveryPlan.FATAL, PlaybackRecovery.plan(3001))
        assertEquals(RecoveryPlan.FATAL, PlaybackRecovery.plan(4001))
        assertEquals(RecoveryPlan.FATAL, PlaybackRecovery.plan(4003))
    }

    @Test
    fun `permission and cleartext failures are fatal`() {
        assertEquals(RecoveryPlan.FATAL, PlaybackRecovery.plan(PlaybackRecovery.CODE_IO_NO_PERMISSION))
        assertEquals(
            RecoveryPlan.FATAL,
            PlaybackRecovery.plan(PlaybackRecovery.CODE_IO_CLEARTEXT_NOT_PERMITTED)
        )
    }

    @Test
    fun `local media is never retried or re-extracted`() {
        // A downloaded file that fails has no network to wait for and no stream
        // to re-extract; retrying just fails again on a loop.
        assertEquals(
            RecoveryPlan.FATAL,
            PlaybackRecovery.plan(
                PlaybackRecovery.CODE_IO_NETWORK_CONNECTION_FAILED, isRemote = false
            )
        )
        assertEquals(
            RecoveryPlan.FATAL,
            PlaybackRecovery.plan(PlaybackRecovery.CODE_IO_FILE_NOT_FOUND, isRemote = false)
        )
    }

    // ── backoff ──────────────────────────────────────────────────────────────

    @Test
    fun `backoff grows exponentially`() {
        assertEquals(1_000L, PlaybackRecovery.backoffMs(1))
        assertEquals(2_000L, PlaybackRecovery.backoffMs(2))
        assertEquals(4_000L, PlaybackRecovery.backoffMs(3))
        assertEquals(8_000L, PlaybackRecovery.backoffMs(4))
    }

    @Test
    fun `backoff is capped so recovery never looks abandoned`() {
        assertEquals(12_000L, PlaybackRecovery.backoffMs(5))
        assertEquals(12_000L, PlaybackRecovery.backoffMs(9))
        // Must not overflow into a negative or absurd delay at large attempts.
        assertEquals(12_000L, PlaybackRecovery.backoffMs(999))
    }

    @Test
    fun `backoff handles zero and negative attempts`() {
        assertEquals(1_000L, PlaybackRecovery.backoffMs(0))
        assertEquals(1_000L, PlaybackRecovery.backoffMs(-3))
    }

    @Test
    fun `whole retry sequence stays under 30 seconds`() {
        // The ceiling exists so the user is not left staring at a frozen player.
        val total = (1..PlaybackRecovery.MAX_ATTEMPTS).sumOf { PlaybackRecovery.backoffMs(it) }
        assertTrue("total was $total ms", total <= 30_000L)
    }
}

class AutoAdvanceTest {

    @Test
    fun `only the first claimant advances`() {
        AutoAdvance.reset()
        assertTrue(AutoAdvance.claim("https://x/1"))
        // The player screen and the service both react to the same STATE_ENDED;
        // without this guard the second one would skip an episode.
        assertFalse(AutoAdvance.claim("https://x/1"))
        assertFalse(AutoAdvance.claim("https://x/1"))
    }

    @Test
    fun `a different video gets its own claim`() {
        AutoAdvance.reset()
        assertTrue(AutoAdvance.claim("https://x/1"))
        assertTrue(AutoAdvance.claim("https://x/2"))
    }

    @Test
    fun `reset lets the same video advance again when replayed`() {
        AutoAdvance.reset()
        assertTrue(AutoAdvance.claim("https://x/1"))
        AutoAdvance.reset()
        assertTrue(AutoAdvance.claim("https://x/1"))
    }
}
