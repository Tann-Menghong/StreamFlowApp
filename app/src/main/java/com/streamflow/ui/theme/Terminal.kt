package com.streamflow.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * ─── TERMINAL CLI DESIGN SYSTEM ──────────────────────────────────────────────
 *
 * A fourth value for LocalDesignStyle, alongside MODERN / AURORA / CLASSIC.
 *
 * Everything here is a TOKEN, never a one-off. The whole point of routing the
 * phosphor palette through Material's ColorScheme is that the ~76 screens that
 * already read MaterialTheme.colorScheme.* inherit the terminal look without
 * being touched — the alternative (a parallel colour object read screen by
 * screen) would mean two sources of truth and a permanent migration.
 *
 * Structural differences that a colour swap CANNOT express — ASCII pane
 * headers, bracket buttons, block cursors — are opt-in via LocalTerminalMode
 * and the primitives in ui/components/terminal/.
 */

// ── Palette ──────────────────────────────────────────────────────────────────
// A phosphor monitor: deep black ground (not pure #000 — the scanlines need
// something to sit on) with bright green as the only "ink".
object TerminalPalette {
    /** Deep black ground. Deliberately not #000000 so scanlines read as texture. */
    val Background = Color(0xFF0A0A0A)

    /** Classic terminal green — the primary ink, cursors, active states. */
    val Primary = Color(0xFF33FF00)

    /** Amber — warnings, secondary accents, the "other" phosphor. */
    val Secondary = Color(0xFFFFB000)

    /**
     * Dimmed green for BORDERS and inactive chrome only.
     *
     * This is the spec's `muted`. It is deliberately NOT used for text: at
     * 2.2:1 against the background it fails WCAG AA badly. Decorative borders
     * carry no information, so the low contrast is acceptable there.
     */
    val Border = Color(0xFF1F521F)

    /**
     * Readable dimmed green for SECONDARY TEXT (5.9:1 — passes AA).
     *
     * A deliberate addition to the spec. The spec reuses `muted` for
     * "inactive text", which would have made every subtitle and caption in the
     * app unreadable; the brief's own accessibility rule ("contrast is
     * non-negotiable") wins over its colour list.
     */
    val Dim = Color(0xFF1FA30F)

    /** Bright red for errors and destructive actions. */
    val Error = Color(0xFFFF3333)

    /** A barely-lifted ground for input rows and selected list items. */
    val Lift = Color(0xFF0D160D)
}

// ── Colour scheme ────────────────────────────────────────────────────────────
/**
 * The phosphor palette expressed as a Material ColorScheme.
 *
 * `onPrimary = Background` is the load-bearing mapping: it gives every Material
 * component that fills with `primary` (buttons, chips, switches, selected rows)
 * the inverted-video treatment the design system asks for, with no per-call-site
 * work at all.
 */
fun terminalColorScheme(): ColorScheme = darkColorScheme(
    primary            = TerminalPalette.Primary,
    onPrimary          = TerminalPalette.Background,   // inverted video
    primaryContainer   = TerminalPalette.Lift,
    onPrimaryContainer = TerminalPalette.Primary,
    secondary          = TerminalPalette.Secondary,
    onSecondary        = TerminalPalette.Background,
    secondaryContainer = TerminalPalette.Lift,
    onSecondaryContainer = TerminalPalette.Secondary,
    tertiary           = TerminalPalette.Secondary,
    onTertiary         = TerminalPalette.Background,
    background         = TerminalPalette.Background,
    onBackground       = TerminalPalette.Primary,
    surface            = TerminalPalette.Background,   // panes are black + border
    onSurface          = TerminalPalette.Primary,
    surfaceVariant     = TerminalPalette.Lift,
    onSurfaceVariant   = TerminalPalette.Dim,          // readable secondary text
    outline            = TerminalPalette.Border,
    outlineVariant     = TerminalPalette.Border,
    error              = TerminalPalette.Error,
    onError            = TerminalPalette.Background,
    errorContainer     = Color(0xFF2A0808),
    onErrorContainer   = TerminalPalette.Error,
    scrim              = Color(0xE60A0A0A),
)

// ── Shape ────────────────────────────────────────────────────────────────────
/**
 * Radius 0, everywhere. Setting this on MaterialTheme kills rounding on every
 * component that uses the theme default — Card, Button, AlertDialog, TextField,
 * Menu — in one place instead of at 164 call sites.
 */
// Material's Shapes only accepts CornerBasedShape, so zero-radius rounded
// corners are the way to express "no radius" here — RectangleShape is a plain
// Shape and is rejected by the type.
private val Square = RoundedCornerShape(0.dp)

val TerminalShapes = Shapes(
    extraSmall = Square,
    small      = Square,
    medium     = Square,
    large      = Square,
    extraLarge = Square,
)

// ── Typography ───────────────────────────────────────────────────────────────
/**
 * Monospace supremacy: every role, every size, one family.
 *
 * The scale snaps to whole sp steps — a modular scale with fractional sizes
 * would break the character grid that makes ASCII rules and progress bars line
 * up. Positive tracking on headers is what gives the "SYSTEM READOUT" feel;
 * the rest stays at 0 so columns of text align.
 */
fun terminalTypography(): Typography {
    val mono = FontFamily.Monospace
    fun t(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
        fontFamily = mono,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )
    return Typography(
        displaySmall   = t(26, 34, FontWeight.Bold, 1.5),
        headlineMedium = t(20, 28, FontWeight.Bold, 1.2),
        titleLarge     = t(18, 26, FontWeight.Bold, 1.0),
        titleMedium    = t(15, 22, FontWeight.Bold, 0.8),
        titleSmall     = t(13, 20, FontWeight.Bold, 0.8),
        bodyLarge      = t(14, 22, FontWeight.Normal),
        bodyMedium     = t(13, 20, FontWeight.Normal),
        bodySmall      = t(12, 18, FontWeight.Normal),
        labelLarge     = t(13, 18, FontWeight.Bold, 1.0),
        labelMedium    = t(11, 16, FontWeight.Bold, 1.0),
        labelSmall     = t(10, 14, FontWeight.Bold, 1.2),
    )
}

// ── Mode flag ────────────────────────────────────────────────────────────────
/**
 * True when the terminal design style is active.
 *
 * Components read this ONLY for structural changes a colour/shape swap cannot
 * express (ASCII pane headers, bracket buttons, block cursors). Anything that
 * is purely colour or corner radius must come from the theme instead, so the
 * terminal look does not leak into 76 files as `if (terminal)` branches.
 */
val LocalTerminalMode = staticCompositionLocalOf { false }

// ── Effects ──────────────────────────────────────────────────────────────────

/** Phosphor persistence: the soft bloom around lit pixels on a CRT. */
fun phosphorGlow(color: Color = TerminalPalette.Primary, radius: Float = 8f) =
    Shadow(color = color.copy(alpha = 0.55f), offset = Offset.Zero, blurRadius = radius)

/**
 * CRT scanlines as a repeating shader rather than N drawn lines.
 *
 * A 1px line every 3px over a 2400px-tall screen would be 800 draw ops per
 * frame; a repeating gradient is one. Applied at the app root over the whole
 * tree, and never consumes touch input.
 */
fun Modifier.crtScanlines(alpha: Float = 0.06f): Modifier = drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to Color.Black.copy(alpha = alpha),
                0.5f to Color.Transparent,
                1.0f to Color.Transparent,
            ),
            start = Offset.Zero,
            end = Offset(0f, 3f),
            tileMode = TileMode.Repeated,
        )
    )
}

/**
 * The cursor heartbeat. A hard on/off square wave — easing would make it read
 * as a "pulse" (soft, modern) instead of a terminal cursor (mechanical).
 */
@Composable
fun rememberBlink(periodMs: Int = 1060): State<Boolean> {
    val transition = rememberInfiniteTransition(label = "blink")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink_phase",
    )
    return androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf { phase < 1f }
    }
}
