package com.streamflow.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bounded fan-out for channel fetches.
 *
 * The unbounded version this replaces was fast for the common case and a memory
 * spike for the uncommon one: one coroutine per subscription meant someone with
 * 150 channels ran 150 NewPipe extractions at once inside a background worker.
 */
class ParallelFetchTest {

    @Test
    fun `results keep the order of their inputs`() = runBlocking {
        // The feed pairs each result back to the channel it came from and the
        // worker sorts by upload time; a reordered result list would mislabel
        // every video. Completion order is not input order, so this matters.
        val out = (1..20).toList().mapParallel(4) { n ->
            delay((20 - n).toLong()) // later items finish FIRST
            n * 2
        }
        assertEquals((1..20).map { it * 2 }, out)
    }

    @Test
    fun `never more than the limit run at once`() = runBlocking {
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        (1..50).toList().mapParallel(6) {
            val now = inFlight.incrementAndGet()
            peak.updateAndGet { p -> maxOf(p, now) }
            delay(5)
            inFlight.decrementAndGet()
        }
        assertTrue("peak was ${peak.get()}", peak.get() <= 6)
    }

    @Test
    fun `the limit is actually used, not serialised`() = runBlocking {
        // A semaphore with the permits mis-set would still pass the cap test
        // above while quietly costing all of the speed the fan-out exists for.
        val peak = AtomicInteger(0)
        val inFlight = AtomicInteger(0)
        (1..20).toList().mapParallel(5) {
            val now = inFlight.incrementAndGet()
            peak.updateAndGet { p -> maxOf(p, now) }
            delay(20)
            inFlight.decrementAndGet()
        }
        assertTrue("expected real parallelism, peak was ${peak.get()}", peak.get() > 1)
    }

    @Test
    fun `an empty list does no work`() = runBlocking {
        val ran = AtomicInteger(0)
        val out = emptyList<Int>().mapParallel(4) { ran.incrementAndGet(); it }
        assertEquals(emptyList<Int>(), out)
        assertEquals(0, ran.get())
    }

    @Test
    fun `a single item skips the machinery`() = runBlocking {
        assertEquals(listOf("x1"), listOf(1).mapParallel(4) { "x$it" })
    }

    @Test
    fun `a nonsense limit still runs`() = runBlocking {
        // coerceAtLeast(1) guards Semaphore, which throws on a permit count of
        // zero -- a crash in a background worker rather than a slow one.
        assertEquals(listOf(2, 4), listOf(1, 2).mapParallel(0) { it * 2 })
    }

    @Test
    fun `the chosen parallelism is a handful, not a device count`() {
        assertTrue(CHANNEL_FETCH_PARALLELISM in 2..12)
    }
}
