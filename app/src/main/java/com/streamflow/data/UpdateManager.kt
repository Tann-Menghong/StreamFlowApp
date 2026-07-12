package com.streamflow.data

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient()

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/Tann-Menghong/StreamFlowApp/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            val body = client.newCall(req).execute().body?.string() ?: return@withContext null
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
        } catch (e: Exception) { null }
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
                val buf = ByteArray(8192)
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

    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }; val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true; if (lv < cv) return false
        }
        return false
    }
}
