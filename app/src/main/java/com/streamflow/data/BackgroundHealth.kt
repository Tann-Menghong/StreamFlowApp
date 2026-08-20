package com.streamflow.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager

/**
 * Whether this phone will actually let StreamFlow keep playing in the
 * background — which is not the same question as whether the app is written
 * correctly.
 *
 * v6.7.0 fixed the app's own reasons for background playback stopping, and
 * v6.8.0 made the failures visible. What neither could fix is an OEM that kills
 * the media service regardless: on Vivo, iQOO, Xiaomi, Huawei and their
 * relatives, an app without a battery exemption is stopped on a timer no matter
 * what it is doing. The user experiences that as StreamFlow being broken.
 *
 * The exemption has always been available in Settings, three levels deep, where
 * the people who most need it are the least likely to find it. This object
 * exists so the app can say plainly which of the three prerequisites for
 * uninterrupted background audio are in place.
 */
object BackgroundHealth {

    /**
     * Manufacturers whose stock power management stops background media
     * services aggressively enough that the exemption is effectively required.
     * Matched loosely because the same behaviour ships under several brand
     * names from the same parent.
     */
    private val AGGRESSIVE = listOf(
        "vivo", "iqoo", "xiaomi", "redmi", "poco", "huawei", "honor",
        "oppo", "realme", "oneplus", "meizu", "tecno", "infinix"
    )

    val isAggressiveOem: Boolean by lazy {
        val make = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        AGGRESSIVE.any { make.contains(it) }
    }

    /** Below API 23 there is no exemption to grant, and no doze to be exempt
     *  from — so the honest answer there is "yes, nothing is stopping us". */
    fun isBatteryExempt(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) true
        else {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        }
    } catch (_: Exception) {
        // Unknowable is reported as fine: claiming a problem we cannot verify
        // would send the user chasing a setting that may already be correct.
        true
    }

    /** The media notification is how playback is controlled once the app is in
     *  the background. Without the permission there are no controls at all. */
    fun hasNotificationPermission(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
        else context.checkSelfPermission(
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { true }

    /** System-wide battery saver throttles background work for every app.
     *  isPowerSaveMode is API 21, which is this app's minSdk — no guard needed. */
    fun isSystemPowerSaveOn(context: Context): Boolean = try {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
    } catch (_: Exception) { false }

    /**
     * One line summarising all three, for the settings header.
     * Deliberately blunt about the OEM case — a vague "some features may be
     * limited" is what let this go unnoticed for so long.
     */
    fun summary(context: Context): String {
        val exempt = isBatteryExempt(context)
        val notif = hasNotificationPermission(context)
        return when {
            !exempt && isAggressiveOem ->
                "This phone will stop background playback — protection is off"
            !exempt -> "Battery protection is off"
            !notif -> "Notification permission is off — no lock-screen controls"
            isSystemPowerSaveOn(context) -> "Protected, but system battery saver is on"
            else -> "Protected — background playback should not be interrupted"
        }
    }

    /** True when something is actually wrong and worth drawing attention to. */
    fun needsAttention(context: Context): Boolean =
        !isBatteryExempt(context) || !hasNotificationPermission(context)

    /**
     * The exemption dialog, or the app's settings page when an OEM build blocks
     * it (several do).
     */
    fun exemptionIntents(context: Context): List<Intent> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            add(
                Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:${context.packageName}")
                )
            )
        }
        add(
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}")
            )
        )
    }
}
