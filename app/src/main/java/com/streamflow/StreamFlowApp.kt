package com.streamflow

import android.app.Application
import com.streamflow.data.OkHttpDownloader
import com.streamflow.data.local.AppDatabase
import com.streamflow.data.local.AppPreferences
import org.schabi.newpipe.extractor.NewPipe

class StreamFlowApp : Application() {
    val database by lazy { AppDatabase.get(this) }
    val prefs by lazy { AppPreferences.get(this) }

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(OkHttpDownloader.instance)
    }
}
