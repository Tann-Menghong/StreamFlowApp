package com.streamflow.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Size formatting for the Storage page.
 *
 * Worth testing because it is the number a user reads before deciding to delete
 * something: a unit boundary that reports "1024 MB" instead of "1.0 GB", or
 * rounds 900 MB down to "0.9 GB", makes the page look broken.
 */
class StorageStatsTest {

    @Test fun `bytes below a kilobyte`() {
        assertEquals("0 B", StorageStats.format(0))
        assertEquals("512 B", StorageStats.format(512))
        assertEquals("1023 B", StorageStats.format(1023))
    }

    @Test fun `kilobyte boundary`() {
        assertEquals("1 KB", StorageStats.format(1024))
        assertEquals("1023 KB", StorageStats.format(1024L * 1024 - 1024))
    }

    @Test fun `megabyte boundary`() {
        assertEquals("1 MB", StorageStats.format(1024L * 1024))
        assertEquals("768 MB", StorageStats.format(768L * 1024 * 1024))
    }

    @Test fun `gigabyte boundary uses one decimal`() {
        assertEquals("1.0 GB", StorageStats.format(1024L * 1024 * 1024))
        assertEquals("1.5 GB", StorageStats.format(1536L * 1024 * 1024))
    }

    @Test fun `gigabyte formatting is locale independent`() {
        // Explicit Locale.US in the implementation: on a locale that uses a
        // comma decimal separator this would otherwise render "1,5 GB".
        val s = StorageStats.format(1536L * 1024 * 1024)
        assert(s.contains(".")) { "expected a dot decimal separator, got $s" }
    }

    @Test fun `snapshot total is the sum of its parts`() {
        val s = StorageStats.Snapshot(
            mediaCacheBytes = 100,
            imageCacheBytes = 20,
            downloadBytes = 3
        )
        assertEquals(123L, s.totalBytes)
    }

    @Test fun `empty snapshot totals zero`() {
        assertEquals(0L, StorageStats.Snapshot().totalBytes)
    }
}
