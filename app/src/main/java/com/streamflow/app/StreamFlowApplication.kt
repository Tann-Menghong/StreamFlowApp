package com.streamflow.app

import android.app.Application
import android.util.Log
import com.streamflow.app.di.ServiceLocator

class StreamFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            ServiceLocator.init(this)
        } catch (e: Exception) {
            Log.e("StreamFlow", "ServiceLocator init failed", e)
        }
    }
}
