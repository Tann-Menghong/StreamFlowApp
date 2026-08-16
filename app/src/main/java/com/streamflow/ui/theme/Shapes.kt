package com.streamflow.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/*
 * Theme-aware corner radii.
 *
 * MaterialTheme.shapes already squares off every component that uses the theme
 * default (Card, Button, AlertDialog, TextField, Menu). But a direct
 * `RoundedCornerShape(12.dp)` at a call site ignores the theme entirely, and the
 * app had ~130 of those plus ~35 CircleShape uses — so in the TERMINAL style
 * those all stayed rounded while everything around them went square.
 *
 * Routing them through these helpers keeps the radius a THEME decision instead
 * of a per-call-site constant. The alternative — an `if (terminal)` at every one
 * of those sites — would have spread the design system across sixteen files and
 * guaranteed the next new call site forgot about it.
 *
 * Overloads resolve by arity, so appShape(12.dp) and the four-corner form never
 * collide.
 */

/** Uniform radius. Square in TERMINAL, [radius] everywhere else. */
@Composable
@ReadOnlyComposable
fun appShape(radius: Dp): Shape =
    if (LocalTerminalMode.current) RectangleShape else RoundedCornerShape(radius)

/** Per-corner radii, e.g. a card whose top corners round into a thumbnail. */
@Composable
@ReadOnlyComposable
fun appShape(topStart: Dp, topEnd: Dp, bottomEnd: Dp, bottomStart: Dp): Shape =
    if (LocalTerminalMode.current) RectangleShape
    else RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)

/**
 * A pill/circle — avatars, icon buttons, chips.
 *
 * Square in TERMINAL: "absolutely no rounded corners" includes the round ones,
 * and a circular avatar next to a square thumbnail is the single most obvious
 * tell that a screen was missed by the sweep.
 */
@Composable
@ReadOnlyComposable
fun appCircle(): Shape =
    if (LocalTerminalMode.current) RectangleShape else CircleShape

/**
 * Percentage-based rounding, used by the segmented tab pills.
 * Square in TERMINAL, otherwise rounded by percent of the smaller side.
 */
@Composable
@ReadOnlyComposable
fun appShapePercent(percent: Int): Shape =
    if (LocalTerminalMode.current) RectangleShape else RoundedCornerShape(percent)

/**
 * Percentage rounding per corner — the half-capsule seek-feedback zones in the
 * player, which round only the edge facing the centre of the screen.
 */
@Composable
@ReadOnlyComposable
fun appShapePercent(topStart: Int, topEnd: Int, bottomEnd: Int, bottomStart: Int): Shape =
    if (LocalTerminalMode.current) RectangleShape
    else RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
