package com.streamflow.data

/**
 * The numbers behind the Library dashboard.
 *
 * These used to be computed twice, in two different composables, from two
 * different definitions of the same words. `LibraryDashboard` counted "this
 * week" as the seven calendar days its bar chart drew; `HistoryStatsRow`
 * counted it as the last 168 hours. Both were on screen at the same time, both
 * were labelled "week", and they disagreed by whatever had been watched
 * between midnight and now. Neither was reachable from a unit test, so the
 * disagreement could only be found by looking at a phone.
 *
 * One definition, in one place, that the chart and the tiles both read from:
 * a week is the seven calendar days ending today, so `weekCount` is always
 * exactly `dayCounts.sum()`.
 *
 * `dayStartMs` (local midnight) is a parameter rather than something computed
 * here, because midnight is a calendar/time-zone question and this object is
 * deliberately free of Android and of the system clock -- the same reason
 * DownloadReconcile takes an Int instead of reaching for DownloadManager.
 */
object LibraryStats {

    const val DAY_MS = 24L * 60 * 60 * 1000

    /** How many days the activity chart covers. */
    const val WEEK_DAYS = 7

    /** One watched video, reduced to the three fields any of these numbers need. */
    data class Watch(
        val watchedAt: Long,
        val positionMs: Long,
        val uploader: String,
    )

    /**
     * @param dayCounts   [WEEK_DAYS] entries, oldest first, last entry = today
     * @param topChannels most-watched first; ties keep their input order
     */
    data class Summary(
        val todayCount: Int,
        val weekCount: Int,
        val weekMinutes: Long,
        val totalMinutes: Long,
        val dayCounts: List<Int>,
        val topChannels: List<Pair<String, Int>>,
    ) {
        /** True when there is nothing worth expanding the dashboard for. */
        val isEmpty: Boolean get() = weekCount == 0 && totalMinutes == 0L
    }

    fun summarize(
        watches: List<Watch>,
        dayStartMs: Long,
        topChannelLimit: Int = 5,
    ): Summary {
        val weekStart = dayStartMs - (WEEK_DAYS - 1) * DAY_MS

        val dayCounts = IntArray(WEEK_DAYS)
        var weekMs = 0L
        var totalMs = 0L
        val perChannel = LinkedHashMap<String, Int>()

        for (w in watches) {
            totalMs += w.positionMs.coerceAtLeast(0L)

            // A row timestamped in the future (clock change, restored backup)
            // would index past the end of the array. Clamp rather than drop it:
            // it is still a watch, it just cannot be placed on the chart.
            if (w.watchedAt >= weekStart) {
                val slot = ((w.watchedAt - weekStart) / DAY_MS).toInt()
                if (slot in 0 until WEEK_DAYS) {
                    dayCounts[slot]++
                    weekMs += w.positionMs.coerceAtLeast(0L)
                }
            }

            val name = w.uploader.trim()
            if (name.isNotEmpty()) perChannel[name] = (perChannel[name] ?: 0) + 1
        }

        // sortedByDescending is stable, so channels tied on count stay in the
        // order they were first seen rather than shuffling between recompositions.
        val top = perChannel.entries
            .map { it.key to it.value }
            .sortedByDescending { it.second }
            .take(topChannelLimit)

        return Summary(
            todayCount = dayCounts[WEEK_DAYS - 1],
            weekCount = dayCounts.sum(),
            weekMinutes = weekMs / 60_000L,
            totalMinutes = totalMs / 60_000L,
            dayCounts = dayCounts.toList(),
            topChannels = top,
        )
    }

    /** `95` -> `"1h 35m"`, `40` -> `"40m"`. */
    fun formatMinutes(minutes: Long): String {
        val m = minutes.coerceAtLeast(0L)
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }
}
