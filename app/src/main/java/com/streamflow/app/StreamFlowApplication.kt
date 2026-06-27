package com.streamflow.app

import android.app.Application
import com.streamflow.app.di.ServiceLocator

class StreamFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
