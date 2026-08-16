package com.streamflow.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

/** One published release, used by the "Change app version" picker. */
data class ReleaseInfo(
    val version: String,        // "6.2.4" — tag with the leading v stripped
    val tag: String,            // "v6.2.4"
    val downloadUrl: String,
    val notes: String,
    val publishedAt: String,    // "2026-08-16"
    val sizeBytes: Long
)

class UpdateManager(private val context: Context) {

    // Default OkHttpClient has a 10s READ timeout — far too tight for streaming a
    // ~30 MB APK, where a single slow chunk on a mobile connection would abort the
    // whole update. Give the read/write phases room; NO callTimeout so a genuinely
    // large download on a slow link isn't capped by an overall deadline.
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Returns null when already up to date; THROWS when the check itself failed
    // (offline, rate-limited) — swallowing that here made the Settings row claim
    // "Up to date" with no network. Callers decide how to surface the failure.
    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/Tann-Menghong/StreamFlowApp/releases/latest")
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        // .use{} — body?.string() closes on success, but the null-body/error path
        // used to leak the connection (same OkHttp leak class as getSponsorSegments)
        val body = client.newCall(req).execute().use { resp ->
            val b = resp.body?.string()
            if (!resp.isSuccessful || b == null)
                throw java.io.IOException("Update check failed (HTTP ${resp.code})")
            b
        }
        val json = JSONObject(body)
        val tag = json.optString("tag_name").removePrefix("v")
        val notes = json.optString("body", "")
        val assets = json.optJSONArray("assets") ?: return@withContext null
        var url = ""
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) {
                url = a.getString("browser_download_url"); break
            }
        }
        if (url.isEmpty() || !isNewer(tag, currentVersion)) return@withContext null
        UpdateInfo(tag, url, notes)
    }

    /**
     * Every published release that ships an APK, newest first.
     *
     * Sorted by real version number rather than trusting GitHub's ordering: the
     * API sorts by creation date, so a hotfix published out of order — or the
     * day 6.10.0 lands after 6.9.0 — would otherwise put the wrong build on top.
     */
    suspend fun listReleases(): List<ReleaseInfo> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/Tann-Menghong/StreamFlowApp/releases?per_page=50")
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        val body = client.newCall(req).execute().use { resp ->
            val b = resp.body?.string()
            if (!resp.isSuccessful || b == null)
                throw java.io.IOException("Couldn't load versions (HTTP ${resp.code})")
            b
        }
        val arr = JSONArray(body)
        val out = ArrayList<ReleaseInfo>()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            if (r.optBoolean("draft")) continue          // unpublished, has no usable asset
            val assets = r.optJSONArray("assets") ?: continue
            var url = ""
            var size = 0L
            for (j in 0 until assets.length()) {
                val a = assets.optJSONObject(j) ?: continue
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
            if (url.isEmpty()) continue                   // a release with no APK can't be installed
            val tag = r.optString("tag_name")
            out.add(
                ReleaseInfo(
                    version     = tag.removePrefix("v"),
                    tag         = tag,
                    downloadUrl = url,
                    notes       = r.optString("body", ""),
                    publishedAt = r.optString("published_at", "").take(10),
                    sizeBytes   = size
                )
            )
        }
        out.sortedWith { a, b -> compareVersions(b.version, a.version) }
    }

    /**
     * Downloads an APK into the phone's public Downloads folder and returns where
     * it landed, e.g. "Downloads/StreamFlow-v6.1.0.apk".
     *
     * This exists for DOWNGRADES. Android refuses to replace an installed app
     * with a lower versionCode, so the only route to an older build is uninstall
     * then install — and an uninstall wipes the app's own storage, which would
     * delete the very APK needed to finish, leaving the user with no app at all.
     * A file in the shared Downloads collection outlives the uninstall.
     */
    suspend fun downloadToDownloads(
        downloadUrl: String,
        fileName: String,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val resp = client.newCall(Request.Builder().url(downloadUrl).build()).execute()
        if (!resp.isSuccessful) {
            resp.close()
            throw java.io.IOException("Download failed (HTTP ${resp.code})")
        }
        val body = resp.body ?: throw java.io.IOException("Empty download response")
        val total = body.contentLength()

        val cr = context.contentResolver
        var pendingUri: Uri? = null
        var legacyFile: File? = null
        val sink: OutputStream

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Drop an earlier copy of the same name, otherwise repeated attempts
            // pile up as "StreamFlow-v6.1.0 (1).apk" and the user installs a stale one.
            runCatching {
                cr.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(fileName)
                )
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.Downloads.IS_PENDING, 1)   // hidden from other apps until complete
            }
            pendingUri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw java.io.IOException("Couldn't create the file in Downloads")
            sink = cr.openOutputStream(pendingUri)
                ?: throw java.io.IOException("Couldn't write to Downloads")
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            legacyFile = File(dir, fileName)
            sink = FileOutputStream(legacyFile)
        }

        try {
            var done = 0L
            sink.use { out ->
                body.byteStream().use { inp ->
                    val buf = ByteArray(64 * 1024)
                    var n: Int
                    while (inp.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
        } catch (e: Exception) {
            // Never leave a half-written APK behind for the user to install.
            pendingUri?.let { u -> runCatching { cr.delete(u, null, null) } }
            legacyFile?.let { f -> runCatching { f.delete() } }
            throw e
        }

        pendingUri?.let { u ->
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            runCatching { cr.update(u, done, null, null) }
        }
        "Downloads/$fileName"
    }

    /** Opens the system uninstall prompt for StreamFlow itself. */
    fun requestUninstall() {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    suspend fun downloadAndInstall(
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(downloadUrl).build()
        val resp = client.newCall(req).execute()
        // A failed request (rate-limited, deleted release, transient CDN error)
        // still returns a non-null body — without this check, that error page
        // gets written to "StreamFlow-update.apk" and handed to the package
        // installer as if it were real, instead of surfacing a clear failure.
        if (!resp.isSuccessful) {
            resp.close()
            throw java.io.IOException("Download failed (HTTP ${resp.code})")
        }
        val body = resp.body ?: throw java.io.IOException("Empty download response")
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val file = File(dir, "StreamFlow-update.apk")
        val total = body.contentLength()
        var done = 0L
        FileOutputStream(file).use { out ->
            body.byteStream().use { inp ->
                val buf = ByteArray(64 * 1024) // 64 KB: fewer syscalls on a big APK
                var n: Int
                while (inp.read(buf).also { n = it } != -1) {
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress((done * 100 / total).toInt())
                }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    private fun isNewer(latest: String, current: String): Boolean =
        compareVersions(latest, current) > 0

    companion object {
        /** Dotted-version compare: <0 if [a] is older than [b], 0 if equal, >0 if newer. */
        fun compareVersions(a: String, b: String): Int {
            val x = a.split(".")
            val y = b.split(".")
            for (i in 0 until maxOf(x.size, y.size)) {
                val xv = x.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
                val yv = y.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
                if (xv != yv) return xv - yv
            }
            return 0
        }
    }
}
