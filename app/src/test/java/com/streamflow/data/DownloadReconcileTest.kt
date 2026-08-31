package com.streamflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadReconcileTest {

    // The bug this exists for: DownloadManager has forgotten the transfer, so
    // the completion broadcast is never coming and the row spins forever.
    @Test fun `unknown id becomes failed`() {
        assertEquals(
            DownloadReconcile.FAILED,
            DownloadReconcile.resolve(DownloadReconcile.DOWNLOADING, null)
        )
    }

    @Test fun `missed completion broadcast becomes done`() {
        assertEquals(
            DownloadReconcile.DONE,
            DownloadReconcile.resolve(
                DownloadReconcile.DOWNLOADING, DownloadReconcile.STATUS_SUCCESSFUL)
        )
    }

    @Test fun `reported failure becomes failed`() {
        assertEquals(
            DownloadReconcile.FAILED,
            DownloadReconcile.resolve(
                DownloadReconcile.DOWNLOADING, DownloadReconcile.STATUS_FAILED)
        )
    }

    @Test fun `in-flight transfers are left alone`() {
        listOf(
            DownloadReconcile.STATUS_PENDING,
            DownloadReconcile.STATUS_RUNNING,
            DownloadReconcile.STATUS_PAUSED
        ).forEach {
            assertNull(
                "status $it is still in flight",
                DownloadReconcile.resolve(DownloadReconcile.DOWNLOADING, it)
            )
        }
    }

    // A wifiOnly download on mobile data sits at PAUSED. Calling that a failure
    // would break the feature shipped beside this one.
    @Test fun `paused is not a failure`() {
        assertNull(
            DownloadReconcile.resolve(
                DownloadReconcile.DOWNLOADING, DownloadReconcile.STATUS_PAUSED)
        )
    }

    // A finished download whose transfer has aged out of DownloadManager is
    // still a file on disk. Never demote it.
    @Test fun `settled rows are never touched`() {
        listOf(DownloadReconcile.DONE, DownloadReconcile.FAILED).forEach { row ->
            listOf(null, DownloadReconcile.STATUS_FAILED, DownloadReconcile.STATUS_SUCCESSFUL)
                .forEach { dm ->
                    assertNull("row $row / dm $dm", DownloadReconcile.resolve(row, dm))
                }
        }
    }

    @Test fun `an unrecognised status is left alone`() {
        assertNull(DownloadReconcile.resolve(DownloadReconcile.DOWNLOADING, 99))
    }
}
