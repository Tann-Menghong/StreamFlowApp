package com.streamflow.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures crashes so failures stop being invisible.
 *
 * The app is distributed as an APK from GitHub, so there is no Play Console and
 * no store vitals. Before this, the only signal that something had broken was a
 * user describing it — which is exactly how a black video player survived three
 * releases. A stack trace turns "video is black" into a line number.
 *
 * Deliberately local and manual. Nothing is uploaded, no SDK is added, and the
 * report is shown to the user before they choose to send it: a stack trace can
 * contain a video title or URL, so silently transmitting one would trade a
 * privacy promise for developer convenience.
 */
object CrashReporter {

    private const val DIR = "crash"
    private const val FILE = "last_crash.txt"

    fun install(context: Context, appVersion: String) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(context, appVersion, thread, error) }
            // Always chain: the system handler is what actually terminates the
            // process and records the crash for the platform. Swallowing it here
            // would leave the app wedged instead of restarting cleanly.
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, appVersion: String, thread: Thread, error: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val when_ = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        File(dir, FILE).writeText(
            buildString {
                appendLine("StreamFlow crash report")
                appendLine("time     : $when_")
                appendLine("version  : v$appVersion")
                appendLine("android  : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("device   : ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("thread   : ${thread.name}")
                appendLine()
                append(stack)
            }
        )
    }

    /** The pending report, or null when the last run ended normally. */
    fun pending(context: Context): String? {
        val f = File(File(context.filesDir, DIR), FILE)
        return if (f.exists()) runCatching { f.readText() }.getOrNull() else null
    }

    /** Called once the user has seen or sent the report. */
    fun clear(context: Context) {
        runCatching { File(File(context.filesDir, DIR), FILE).delete() }
    }

    /** Trimmed for a GitHub issue URL, which has a practical length limit. */
    fun forIssueBody(report: String): String {
        val head = report.lineSequence().take(40).joinToString("\n")
        return if (head.length <= 1500) head else head.take(1500) + "\n… (truncated)"
    }
}
