package com.streamflow.data

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Is any activity of this app currently started (i.e. visible to the user)?
 *
 * PlaybackService needs this to decide who owns end-of-video advancement. The
 * player screen shows a 5-second countdown with a Cancel button, which only
 * makes sense while somebody is looking at it; with the screen off or the app
 * in the background the service has to advance by itself, immediately.
 *
 * Implemented with ActivityLifecycleCallbacks rather than
 * androidx.lifecycle:lifecycle-process so this costs no new dependency, and it
 * works unchanged all the way down to minSdk 21 (the callback API is API 14).
 *
 * Counting started/stopped rather than resumed/paused is deliberate: during a
 * configuration change or PiP transition the count dips through the old
 * activity being stopped after the new one starts, never to zero, so a rotation
 * cannot be mistaken for the user leaving.
 */
object AppForeground {

    @Volatile
    private var startedActivities = 0

    /** True while at least one activity is between onStart and onStop. */
    val isForeground: Boolean get() = startedActivities > 0

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
