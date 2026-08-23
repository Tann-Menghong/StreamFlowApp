package com.streamflow.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Run a suspending block over a list in parallel, but never more than [limit] at
 * a time.
 *
 * Both callers previously launched one coroutine per item with no bound. That
 * was written to fix a real problem -- fetching channels one after another kept
 * the upload-check worker alive for minutes -- but it replaced "too slow" with
 * "all at once", and the second is only invisible because most people have few
 * subscriptions. Someone with 150 of them had 150 NewPipe extractions in flight
 * inside a background worker: the OkHttp dispatcher caps sockets at 24 per host,
 * so the rest sat parked holding response buffers while every completed fetch
 * competed for CPU to parse. On a low-RAM phone that is a memory spike and a
 * stuttering foreground, for a job nobody asked to happen now.
 *
 * A permit count keeps the parallelism that made it fast -- the network is
 * latency-bound, so a handful of concurrent fetches captures nearly all of the
 * win -- while bounding what can be resident at once. Failures stay the
 * caller's business; this only governs how many run together.
 */
suspend fun <T, R> List<T>.mapParallel(limit: Int, block: suspend (T) -> R): List<R> {
    if (isEmpty()) return emptyList()
    if (size == 1) return listOf(block(this[0]))
    val gate = Semaphore(limit.coerceAtLeast(1))
    return coroutineScope {
        map { item -> async { gate.withPermit { block(item) } } }.awaitAll()
    }
}

/** How many channel extractions may run at once. Chosen against the work rather
 *  than the device: these are latency-bound HTTPS round trips, so a handful
 *  saturates the useful parallelism, and each one that completes wants CPU to
 *  parse. Raising it buys little and costs memory on the phones least able to
 *  spare it. */
const val CHANNEL_FETCH_PARALLELISM = 6
