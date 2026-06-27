package com.streamflow.app.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/Tann-Menghong/StreamFlowApp/releases/latest"

/**
 * Checks GitHub Releases for a newer build and can fetch + launch its installer, since the app
 * is sideloaded rather than distributed through a store that would handle updates itself.
 */
class UpdateManager(private val client: OkHttpClient) {

    suspend fun checkForUpdate(currentVersionName: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .build()

        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            response.body?.string()
        } ?: return@withContext null

        val json = JSONObject(body)
        val remoteVersion = json.optString("tag_name").removePrefix("v")
        if (remoteVersion.isBlank() || !isNewer(remoteVersion, currentVersionName)) {
            return@withContext null
        }

        val assets = json.optJSONArray("assets") ?: return@withContext null
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name").endsWith(".apk")) {
                apkUrl = asset.optString("browser_download_url")
                break
            }
        }
        val downloadUrl = apkUrl ?: return@withContext null

        UpdateInfo(
            versionName = remoteVersion,
            downloadUrl = downloadUrl,
            releaseNotes = json.optString("body")
        )
    }

    suspend fun download(context: Context, downloadUrl: String): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(downloadUrl).build()
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "streamflow-update.apk")

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
            val body = checkNotNull(response.body) { "Empty download response" }
            apkFile.outputStream().use { output -> body.byteStream().copyTo(output) }
        }
        apkFile
    }

    fun install(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }
}
