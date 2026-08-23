package com.streamflow.ui.theme

import androidx.compose.ui.graphics.Color

// Refined dark palette: a cool near-black base with clear, evenly-stepped
// elevation layers (bg → surface → surfaceVariant), crisp near-white text and a
// legible-but-clearly-secondary subtext. Cool neutrals read more "product-grade"
// than the old slightly-purple greys.
val BackgroundDark     = Color(0xFF0A0A0F)
val SurfaceDark        = Color(0xFF141419)
val SurfaceVariantDark = Color(0xFF20212B)
val PrimaryRed         = Color(0xFFFF3B3B)
val PrimaryRedDim      = Color(0xFFB51818)
val OnSurfaceDark      = Color(0xFFF3F4F8)
val SubtextDark        = Color(0xFF7C7E8E)

// AMOLED: true black base, faint cool lift on the cards so they still separate
val BackgroundAmoled   = Color(0xFF000000)
val SurfaceAmoled      = Color(0xFF0A0A0E)

// Light: clean cool off-white base, pure-white cards, crisp near-black text
val BackgroundLight    = Color(0xFFF5F6F9)
val SurfaceLight       = Color(0xFFFFFFFF)
val SurfaceVariantLight= Color(0xFFECEEF3)
val PrimaryRedLight    = Color(0xFFCC0F0F)
val OnSurfaceLight     = Color(0xFF11131A)
val SubtextLight       = Color(0xFF5A5E6E)

// Warm Paper: off-white with a paper/sepia cast instead of a blue one. The app
// had six dark themes and exactly one light one, so anyone who prefers a light
// interface had no choice at all. Warm neutrals are also easier on the eyes in
// daylight than the cool grey of LIGHT, which is the condition light themes are
// actually used in.
// Contrast measured, not eyeballed: body 14.8:1 on background (AAA),
// subtext 5.5:1 (AA).
val BackgroundPaper     = Color(0xFFF7F3EC)
val SurfacePaper        = Color(0xFFFFFDF8)
val SurfaceVariantPaper = Color(0xFFEFE8DC)
val OnSurfacePaper      = Color(0xFF231F1A)
val SubtextPaper        = Color(0xFF6B6156)
val OutlinePaper        = Color(0xFFDDD4C6)
val OutlineVariantPaper = Color(0xFFEAE3D7)

// Nordic Frost: the light counterpart to Midnight Blue — cool blue-grey
// surfaces, so the palette family exists in both directions rather than only
// dark. Body 15.3:1 (AAA), subtext 5.5:1 (AA).
val BackgroundNordic     = Color(0xFFEDF1F7)
val SurfaceNordic        = Color(0xFFFFFFFF)
val SurfaceVariantNordic = Color(0xFFE0E7F0)
val OnSurfaceNordic      = Color(0xFF141B26)
val SubtextNordic        = Color(0xFF56607A)
val OutlineNordic        = Color(0xFFCBD5E4)
val OutlineVariantNordic = Color(0xFFDFE6EF)

/*
 * ── Additional surface themes ───────────────────────────────────────────────
 *
 * The accent picker already recolours primary/secondary, but every accent sat
 * on one of only three backgrounds — so "10 accents" still meant three looks.
 * These are full surface palettes: base, two elevation steps, text, subtext and
 * both outlines, each tuned as a set rather than a background swap.
 *
 * Every one keeps body text at or above the WCAG AA 4.5:1 ratio against its own
 * surface, and subtext at or above the 3:1 large-text floor. That constraint is
 * what stops a "cinematic" theme from becoming unreadable in a dark room, which
 * is the entire situation this app is used in.
 */

// Midnight — deep navy rather than neutral black. Cooler and softer than DARK
// for night viewing; the blue base makes warm accents (red/orange) pop hardest.
val BackgroundMidnight     = Color(0xFF070B14)
val SurfaceMidnight        = Color(0xFF101827)
val SurfaceVariantMidnight = Color(0xFF1B2537)
val OnSurfaceMidnight      = Color(0xFFE8EDF7)
val SubtextMidnight        = Color(0xFF8B99B5)
val OutlineMidnight        = Color(0xFF263248)
val OutlineVariantMidnight = Color(0xFF18202E)

// Cinema — desaturated aubergine. Warm-dark and low-glare, meant for a room
// with the lights off; reads "theatre" without tinting thumbnails purple.
val BackgroundCinema       = Color(0xFF0C0713)
val SurfaceCinema          = Color(0xFF171022)
val SurfaceVariantCinema   = Color(0xFF241934)
val OnSurfaceCinema        = Color(0xFFF1EAF9)
val SubtextCinema          = Color(0xFF9E8FB3)
val OutlineCinema          = Color(0xFF33244A)
val OutlineVariantCinema   = Color(0xFF1F1630)

// Graphite — pure neutral grey, zero colour cast. The one theme that lets
// thumbnail artwork be the only coloured thing on screen.
val BackgroundGraphite     = Color(0xFF131313)
val SurfaceGraphite        = Color(0xFF1D1D1D)
val SurfaceVariantGraphite = Color(0xFF2A2A2A)
val OnSurfaceGraphite      = Color(0xFFEDEDED)
val SubtextGraphite        = Color(0xFF9A9A9A)
val OutlineGraphite        = Color(0xFF3A3A3A)
val OutlineVariantGraphite = Color(0xFF232323)

// High contrast — an accessibility theme, not a style. Pure black behind pure
// white at 21:1, and a subtext that is deliberately NOT dimmed to grey: the
// usual "secondary text" trick is exactly what fails low-vision users, so this
// theme trades visual hierarchy for legibility on purpose. Outlines are solid
// white so control edges are findable without relying on fill contrast.
val BackgroundContrast     = Color(0xFF000000)
val SurfaceContrast        = Color(0xFF000000)
val SurfaceVariantContrast = Color(0xFF1A1A1A)
val OnSurfaceContrast      = Color(0xFFFFFFFF)
val SubtextContrast        = Color(0xFFE6E6E6)
val OutlineContrast        = Color(0xFFFFFFFF)
val OutlineVariantContrast = Color(0xFF8A8A8A)
