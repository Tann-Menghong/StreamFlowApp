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
            isHighPerf = totalRamGb >= 5.5f && !am.isLowRamDevice
        } catch (_: Exception) {
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
