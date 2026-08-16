package com.streamflow.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version ordering, used by the updater and by the "Change app version" picker
 * to decide what counts as an upgrade versus a downgrade.
 *
 * Getting this wrong is not cosmetic: a downgrade misread as an upgrade would
 * send the user down the install path that requires uninstalling the app first.
 */
class VersionCompareTest {

    private fun cmp(a: String, b: String) = UpdateManager.compareVersions(a, b)

    @Test fun `equal versions compare equal`() {
        assertTrue(cmp("6.3.5", "6.3.5") == 0)
    }

    @Test fun `patch ordering`() {
        assertTrue(cmp("6.3.5", "6.3.4") > 0)
        assertTrue(cmp("6.3.4", "6.3.5") < 0)
    }

    @Test fun `minor beats patch`() {
        assertTrue(cmp("6.4.0", "6.3.9") > 0)
    }

    @Test fun `major beats minor`() {
        assertTrue(cmp("7.0.0", "6.9.9") > 0)
    }

    @Test fun `numeric not lexicographic`() {
        // The bug this guards: as strings, "6.10.0" sorts BEFORE "6.9.0", which
        // would make a newer release look older and hide the update.
        assertTrue(cmp("6.10.0", "6.9.0") > 0)
        assertTrue(cmp("6.2.10", "6.2.9") > 0)
    }

    @Test fun `missing segments are treated as zero`() {
        assertTrue(cmp("6.3", "6.3.0") == 0)
        assertTrue(cmp("6.3.1", "6.3") > 0)
    }

    @Test fun `garbage segments do not throw`() {
        // Tag names are user-controlled on GitHub; a malformed one must not crash
        // the update check.
        cmp("6.3.x", "6.3.0")
        cmp("", "6.3.0")
        assertTrue(true)
    }
}
