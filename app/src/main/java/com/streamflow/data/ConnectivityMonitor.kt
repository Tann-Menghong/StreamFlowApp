package com.streamflow.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the device currently has a usable internet connection.
 *
 * Nothing in the app tracked this before, so "you are offline" and "this
 * request failed" surfaced identically — a blank feed with a generic error,
 * when the app actually had a full offline library and downloads available.
 *
 * Reports NET_CAPABILITY_VALIDATED, not merely "a network exists": a phone
 * attached to a captive-portal Wi-Fi has a network and no internet, and
 * treating that as online is exactly what produces mysterious failures.
 */
object ConnectivityMonitor {

    private val _online = MutableStateFlow(true)

    /** True when the device has validated internet access. Starts optimistic so
     *  the first frame never flashes an offline banner before the callback fires. */
    val online: StateFlow<Boolean> = _online

    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return

        // registerDefaultNetworkCallback is API 24+. Below that, fall back to a
        // one-shot read: those releases lack the callback, and a stale-but-sane
        // value beats crashing or pretending to be permanently offline.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            @Suppress("DEPRECATION")
            _online.value = runCatching { cm.activeNetworkInfo?.isConnected == true }
                .getOrDefault(true)
            return
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { refresh(cm) }
            override fun onLost(network: Network) { refresh(cm) }
            override fun onCapabilitiesChanged(
                network: Network, caps: NetworkCapabilities
            ) { refresh(cm) }
        }
        // Never let a monitor take down the app it is monitoring.
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        refresh(cm)
    }

    private fun refresh(cm: ConnectivityManager) {
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                caps != null &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected == true
            }
        }.getOrDefault(true)
        _online.value = ok
    }
}
