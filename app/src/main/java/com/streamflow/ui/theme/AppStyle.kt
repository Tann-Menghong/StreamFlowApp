package com.streamflow.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

// App-wide style knobs from Settings, provided at the root in MainActivity

// Thumbnail corner radius in dp (SQUARE=4, ROUNDED=12, ROUND=18)
val LocalThumbCorner = staticCompositionLocalOf { 12 }

// Whether long-press / gesture haptics should fire
val LocalHapticsEnabled = staticCompositionLocalOf { true }

// Design style: "MODERN" = card feed, floating pill bars, colorful settings
// badges; "AURORA" = the modern layout plus translucent "glass" surfaces and
// gradient hairline borders; "CLASSIC" = the original flat full-width design.
// Structural checks should use != "CLASSIC" (aurora shares modern's layout);
// == "AURORA" only for the glass/gradient accents.
val LocalDesignStyle = staticCompositionLocalOf { "MODERN" }

fun cornerDpFor(style: String): Int = when (style) {
    "SQUARE" -> 4
    "ROUND"  -> 18
    else     -> 12
}
