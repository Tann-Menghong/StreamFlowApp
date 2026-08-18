package com.streamflow.data

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

// Probed once at startup: lets quality/buffer decisions match what the device
// can actually do (e.g. iQOO Z10 Turbo Pro: hardware VP9 + AV1 decode, 12GB+
// RAM, 144Hz panel) instead of one-size-fits-all conservative defaults sized
// for the weakest supported phone.
object DeviceCaps {

    var isHighPerf = false; private set
    var totalRamGb = 0f; private set

    /**
     * Coarse performance tier, used to size playback buffers and caches.
     *
     * isHighPerf alone was a single boolean, so every device that was not a
     * flagship got the same treatment as a 1 GB Android 5 phone. A Galaxy Note 9
     * (6 GB, API 29, hardware VP9) is neither: it can hold a healthy buffer but
     * cannot absorb a flagship-sized one, and totalMem on a 6 GB device reports
     * roughly 5.5-5.8 GB, so it lands right on the old threshold and could flip
     * between tiers between boots. MID exists so that class of device gets a
     * stable, deliberately chosen budget instead of a coin toss.
     */
    enum class Tier { LOW, MID, HIGH }

    var tier: Tier = Tier.LOW; private set

    /**
     * Hard ceiling on bytes ExoPlayer may hold in its buffer pool.
     *
     * DefaultLoadControl computes this from track types when left unset, and for
     * video that default is 2000 * 64 KB = 128 MB. Nothing in the app ever set
     * it, so on every device — including a 2018 phone with other apps resident —
     * the player was allowed to allocate ~132 MB of buffer before size stopped
     * it. Time limits alone do not bound it: at a high bitrate, 60 s of video is
     * far more than a low-end device can spare, and the OOM lands as a hard
     * crash rather than a stutter.
     */
    val targetBufferBytes: Int
        get() = when (tier) {
            Tier.HIGH -> 80 * 1024 * 1024
            Tier.MID -> 40 * 1024 * 1024
            Tier.LOW -> 20 * 1024 * 1024
        }

    /** How far ahead to buffer, in ms. Larger is smoother on flaky networks but
     *  costs memory and wasted data when the user skips away. */
    val maxBufferMs: Int
        get() = when (tier) {
            Tier.HIGH -> 120_000
            Tier.MID -> 90_000
            Tier.LOW -> 50_000
        }

    /** Back-buffer keeps recently played video in memory so a small rewind is
     *  instant. Off entirely on LOW — there is no memory to spend on comfort. */
    val backBufferMs: Int
        get() = when (tier) {
            Tier.HIGH -> 20_000
            Tier.MID -> 10_000
            Tier.LOW -> 0
        }

    // Hardware decoders are what make high-res VP9/AV1 playback cheap; software
    // decoding the same streams stutters and eats battery on midrange chips.
    val hasHwVp9: Boolean by lazy { hasHardwareDecoder("video/x-vnd.on2.vp9") }
    val hasHwAv1: Boolean by lazy { hasHardwareDecoder("video/av01") }

    // AUTO quality ceiling: devices that hardware-decode VP9/AV1 and have RAM
    // to spare start at 1080p; everything else keeps the safe 720p default.
    val autoMaxHeight: Int
        get() = if (isHighPerf && (hasHwVp9 || hasHwAv1)) 1080 else 720

    fun init(context: Context) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            totalRamGb = mi.totalMem / (1024f * 1024f * 1024f)
            val lowRam = am.isLowRamDevice
            // Thresholds are on REPORTED totalMem, which is always below the
            // marketing figure (the kernel and any carve-outs are excluded), so
            // 3.4 catches nominal-4 GB devices and 7.0 catches nominal-8 GB.
            // A Note 9 (nominal 6 GB, reports ~5.6) therefore lands in MID.
            tier = when {
                lowRam || totalRamGb < 3.4f -> Tier.LOW
                totalRamGb >= 7.0f -> Tier.HIGH
                else -> Tier.MID
            }
            // Left on its original threshold on purpose. isHighPerf already gates
            // AUTO quality, cache sizes and thumbnail bit depth; moving it would
            // quietly drop a 6 GB phone from 1080p to 720p, which is a
            // regression, not a compatibility fix. Tier is the new, finer axis
            // and only governs buffer budgets.
            isHighPerf = totalRamGb >= 5.5f && !lowRam
        } catch (_: Exception) {
            tier = Tier.LOW
            isHighPerf = false
        }
    }

    private fun hasHardwareDecoder(mime: String): Boolean = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder &&
                info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                isHardwareAccelerated(info)
        }
    } catch (_: Exception) { false }

    private fun isHardwareAccelerated(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated
        // Pre-Q heuristic: Google's software codecs are OMX.google.* / *.sw.*
        else !info.name.startsWith("OMX.google.", ignoreCase = true) &&
            !info.name.contains(".sw.", ignoreCase = true)
}
