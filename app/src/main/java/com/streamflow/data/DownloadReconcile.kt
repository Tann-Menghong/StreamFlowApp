package com.streamflow.data

/**
 * Decides what a stored download row should say, given what the system
 * DownloadManager currently knows about it.
 *
 * A row is only ever moved off DOWNLOADING by ACTION_DOWNLOAD_COMPLETE. That
 * broadcast is not guaranteed to arrive: the user can clear the transfer from
 * the system Downloads UI, DownloadManager ages entries out of its own
 * database, and a receiver can be missed while the app is being killed. When it
 * does not arrive, the row shows "Downloading…" forever -- a spinner that will
 * never resolve, beside a cancel button for a transfer that no longer exists.
 * There is no path out of that state, not even the retry button, because retry
 * is only offered on FAILED.
 *
 * Kept free of android.* so it is testable on the JVM, the same reason
 * CustomTabs parses URLs with string operations. The status values are
 * DownloadManager's own constants, restated here rather than imported.
 */
object DownloadReconcile {

    const val DOWNLOADING = "DOWNLOADING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"

    // android.app.DownloadManager status constants
    const val STATUS_PENDING = 1
    const val STATUS_RUNNING = 2
    const val STATUS_PAUSED = 4
    const val STATUS_SUCCESSFUL = 8
    const val STATUS_FAILED = 16

    /**
     * The status this row should have, or null to leave it alone.
     *
     * [dmStatus] is null when DownloadManager has no record of the id at all.
     *
     * Only DOWNLOADING rows are ever touched. A DONE row whose transfer has
     * since been aged out of DownloadManager is still a downloaded file on
     * disk, and marking it FAILED would throw away a working download over a
     * bookkeeping detail.
     */
    fun resolve(rowStatus: String, dmStatus: Int?): String? {
        if (rowStatus != DOWNLOADING) return null
        return when (dmStatus) {
            null -> FAILED              // gone from DownloadManager: it will never finish
            STATUS_SUCCESSFUL -> DONE   // a completion broadcast we missed
            STATUS_FAILED -> FAILED
            // PENDING, RUNNING and PAUSED are all still in flight. PAUSED in
            // particular is what a wifiOnly download looks like on mobile data,
            // and calling that a failure would break the feature next to it.
            else -> null
        }
    }
}
