package com.streamflow.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

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

    private val _metered = MutableStateFlow(false)

    /** True on mobile data (or a metered hotspot). PlaybackService reads this to
     *  apply the user's mobile-data quality preference, which it previously had
     *  no way to see — it returned a flat AUTO or 480P regardless. */
    val metered: StateFlow<Boolean> = _metered

    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { refresh(cm) }
            override fun onLost(network: Network) { refresh(cm) }
            override fun onCapabilitiesChanged(
                network: Network, caps: NetworkCapabilities
            ) { refresh(cm) }
        }

        // registerDefaultNetworkCallback is API 24+, but the REQUEST-based
        // registerNetworkCallback below it is API 21 — so older devices get live
        // updates too, instead of the single stale read this used to do. That
        // read never changed again for the life of the process, which meant a
        // phone that started offline stayed "offline" to the app even after the
        // network came back, and playback recovery had nothing to wait on.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback)
            } else {
                cm.registerNetworkCallback(
                    android.net.NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                    callback
                )
            }
        }
        refresh(cm)
    }

    /**
     * Suspend until the device is online, or [timeoutMs] elapses.
     *
     * Playback recovery uses this instead of counting retries down while the
     * phone is in a tunnel: burning all five attempts in eight seconds with no
     * network wastes them, and then the app is out of retries at the exact
     * moment the signal returns. Returns true if we became (or already were)
     * online.
     */
    suspend fun awaitOnline(timeoutMs: Long): Boolean {
        if (_online.value) return true
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            online.first { it }
        } != null
    }

    /**
     * What "metered" should read after an observation.
     *
     * @param observed true/false when a network was present to measure, null
     *                 when there was none -- which is a missing answer, not a
     *                 negative one. Answering "unmetered" for "no network" is
     *                 what made losing signal on mobile data look like a change
     *                 of network type.
     */
    internal fun nextMetered(previous: Boolean, observed: Boolean?): Boolean =
        observed ?: previous

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

        // "Is this connection metered?" has no answer when there IS no
        // connection, and the previous code answered "no" -- caps == null made
        // the whole expression false, i.e. UNMETERED. Losing signal on mobile
        // data therefore reported a transition to unmetered, and regaining it
        // reported another back, so one tunnel produced two spurious network
        // changes. PlaybackService treats each as a new link and drops the
        // quality step-down it is holding, so a step-down taken FOR the tunnel
        // was discarded inside it. It also opened a window where the user's
        // mobile-data quality cap read as absent, which is the one setting whose
        // whole purpose is not spending their data.
        //
        // Keeping the last known value is the honest answer: the network that
        // just went away is the best available guess at the one coming back.
        val meteredNow = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                    ?: return@runCatching null
                // Absence of NOT_METERED is the reliable signal; asking for a
                // CELLULAR transport misses metered hotspots and tethering.
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            } else {
                @Suppress("DEPRECATION")
                if (cm.activeNetworkInfo == null) null else cm.isActiveNetworkMetered
            }
        }.getOrNull()
        _metered.value = nextMetered(_metered.value, meteredNow)
    }
}
