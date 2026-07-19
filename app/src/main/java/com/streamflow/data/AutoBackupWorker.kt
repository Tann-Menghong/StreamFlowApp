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
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "auto_backup"
    }
}
