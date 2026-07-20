package com.streamflow.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.streamflow.StreamFlowApp
import kotlinx.coroutines.flow.first
import java.io.File

// Weekly automatic library backup (Settings > Backup). Writes a date-stamped
// JSON into the public Documents/StreamFlow folder so it survives app
// uninstall — the whole point of a safety-net backup.
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? StreamFlowApp ?: return Result.success()
        if (!app.prefs.autoBackup.first()) return Result.success()

        return try {
            val json = BackupManager.buildBackupJson(app.database).toString(2)
            val name = "StreamFlow-backup-" + java.text.SimpleDateFormat(
                "yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/StreamFlow")
                }
                val uri = applicationContext.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"), values)
                    ?: return Result.retry()
                applicationContext.contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray())
                } ?: return Result.retry()
            } else {
                // Pre-Q: app-specific external dir (no runtime permission needed);
                // still user-visible under Android/data but better than nothing
                val dir = applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir
                File(dir, name).writeText(json)
            }
            pruneOldBackups()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    // A weekly job runs forever, so without pruning the Documents/StreamFlow
    // folder fills with dozens of date-stamped JSONs. Keep only the newest few.
    private fun pruneOldBackups() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = applicationContext.contentResolver
                val uri = MediaStore.Files.getContentUri("external")
                val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
                val selection = MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ? AND " +
                    MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?"
                val args = arrayOf("StreamFlow-backup-%.json", "%StreamFlow%")
                // Newest first by row id (monotonic for our inserts)
                val order = MediaStore.MediaColumns._ID + " DESC"
                resolver.query(uri, projection, selection, args, order)?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    var index = 0
                    while (c.moveToNext()) {
                        if (index++ < KEEP) continue // keep the newest KEEP
                        val id = c.getLong(idCol)
                        try {
                            resolver.delete(
                                android.content.ContentUris.withAppendedId(uri, id), null, null)
                        } catch (_: Exception) {}
                    }
                }
            } else {
                val dir = applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir
                dir.listFiles { f -> f.name.startsWith("StreamFlow-backup-") && f.name.endsWith(".json") }
                    ?.sortedByDescending { it.name } // date-stamped name sorts chronologically
                    ?.drop(KEEP)
                    ?.forEach { runCatching { it.delete() } }
            }
        } catch (_: Exception) {}
    }

    companion object {
        const val WORK_NAME = "auto_backup"
        private const val KEEP = 6
    }
}
