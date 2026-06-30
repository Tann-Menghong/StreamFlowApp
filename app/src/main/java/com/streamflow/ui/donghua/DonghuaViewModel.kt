package com.streamflow.ui.donghua

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel

private const val PREFS_NAME = "donghua_prefs"
private const val KEY_LAST_URL = "last_url"
const val DONGHUA_HOME = "https://donghuafun.com/"

class DonghuaViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastUrl(): String = prefs.getString(KEY_LAST_URL, DONGHUA_HOME) ?: DONGHUA_HOME

    fun saveLastUrl(url: String) {
        if (url.startsWith("http")) prefs.edit().putString(KEY_LAST_URL, url).apply()
    }
}
