package com.streamflow.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Is this connection metered?" when there is no connection.
 *
 * The old code answered false -- UNMETERED -- because a null NetworkCapabilities
 * made the whole expression false. Losing signal on mobile data therefore
 * looked like a change of network TYPE, not a loss of network, and two things
 * downstream believed it:
 *
 *  - PlaybackService treats a metered transition as a new link and drops the
 *    quality step-down it is holding. A step-down taken because of the tunnel
 *    was discarded inside the tunnel, then re-earned on the way out.
 *  - basePreference() reads it to apply the user's mobile-data quality cap. For
 *    as long as the wrong value stood, the one setting whose entire purpose is
 *    not spending mobile data read as inapplicable.
 */
class MeteredStateTest {

    @Test
    fun `an observation is taken at face value`() {
        assertTrue(ConnectivityMonitor.nextMetered(previous = false, observed = true))
        assertFalse(ConnectivityMonitor.nextMetered(previous = true, observed = false))
    }

    @Test
    fun `no network keeps the last known answer`() {
        // The network that just went away is the best available guess at the one
        // coming back, and far better than asserting the opposite of it.
        assertTrue(ConnectivityMonitor.nextMetered(previous = true, observed = null))
        assertFalse(ConnectivityMonitor.nextMetered(previous = false, observed = null))
    }

    @Test
    fun `a tunnel on mobile data reports no change at all`() {
        // The regression this exists for, walked end to end: metered, signal
        // lost, signal regained. The old code produced true -> false -> true,
        // two spurious network changes; the fix produces one steady value.
        var metered = ConnectivityMonitor.nextMetered(previous = false, observed = true)
        val entering = metered
        metered = ConnectivityMonitor.nextMetered(metered, observed = null)  // tunnel
        val inside = metered
        metered = ConnectivityMonitor.nextMetered(metered, observed = true)  // out
        assertTrue(entering)
        assertTrue("metered must not flip while offline", inside)
        assertTrue(metered)
    }

    @Test
    fun `wifi to offline to wifi is likewise steady`() {
        var metered = ConnectivityMonitor.nextMetered(previous = true, observed = false)
        metered = ConnectivityMonitor.nextMetered(metered, observed = null)
        assertFalse(metered)
        metered = ConnectivityMonitor.nextMetered(metered, observed = false)
        assertFalse(metered)
    }
}
