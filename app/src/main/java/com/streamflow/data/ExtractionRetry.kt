package com.streamflow.data

import kotlinx.coroutines.delay

/**
 * Retry an extraction, but only for the reasons that can actually change.
 *
 * Opening a video made exactly one attempt at the slowest, least reliable step
 * in the whole pipeline. A single timeout on a train — the case this app is
 * used in most — produced a flat error screen and a Retry button the user had
 * to press themselves, while playback that had ALREADY started would have
 * recovered from the identical failure without them noticing. The two halves of
 * the same journey behaved completely differently.
 *
 * Deliberately narrow:
 *
 *  - Only TRANSIENT and OFFLINE are retried. A removed video, a geo-block or a
 *    broken extractor reach the same answer however many times they are asked,
 *    and the battery spent asking is pure waste.
 *  - Three attempts, not five. This runs while the user is looking at a loading
 *    screen, so the whole sequence has to stay inside a few seconds — unlike
 *    mid-playback recovery, which is racing a silent gap rather than a
 *    watched spinner.
 *  - Backoff comes from PlaybackRecovery, so the two recovery paths cannot
 *    drift apart in timing.
 *  - OFFLINE waits for the network instead of spending an attempt against it,
 *    for the same reason mid-playback recovery does.
 */
object ExtractionRetry {

    const val MAX_ATTEMPTS = 3

    /** Bounded wait for signal. Short: someone is watching a spinner. */
    private const val OFFLINE_WAIT_MS = 15_000L

    /**
     * @param attempts how many tries in total. The default suits a user staring
     *   at a loading screen; callers acting on a button press the user expects
     *   to respond immediately (skip to next) should ask for fewer.
     * @param waitForNetwork whether an OFFLINE failure may pause for signal. A
     *   press of "next" on a headset must not hang for half a minute.
     */
    suspend fun <T> run(
        what: String,
        attempts: Int = MAX_ATTEMPTS,
        waitForNetwork: Boolean = true,
        block: suspend () -> T
    ): T {
        var attempt = 0
        // At most ONE wait for signal per sequence. Waiting on every retryable
        // failure multiplied the budget by the attempt count: three attempts
        // meant the user could watch a spinner for 15 s + 15 s + backoff before
        // being told what a single immediate error would have said.
        var waitedForNetwork = false
        while (true) {
            attempt++
            try {
                return block()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val kind = classifyExtractionError(e)
                if (!kind.isRetryable || attempt >= attempts) {
                    PlaybackLog.warn(
                        "extract",
                        "$what failed: $kind after $attempt attempt(s)" +
                            if (kind.isRetryable) " (gave up)" else " (not retryable)"
                    )
                    throw e
                }
                PlaybackLog.info("extract", "$what $kind, retry $attempt/$attempts")
                if (kind.needsNetwork && waitForNetwork && !waitedForNetwork &&
                    !ConnectivityMonitor.online.value
                ) {
                    waitedForNetwork = true
                    ConnectivityMonitor.awaitOnline(OFFLINE_WAIT_MS)
                }
                delay(PlaybackRecovery.backoffMs(attempt))
            }
        }
    }
}
