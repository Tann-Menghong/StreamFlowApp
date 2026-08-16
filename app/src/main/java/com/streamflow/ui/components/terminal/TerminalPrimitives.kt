package com.streamflow.ui.components.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflow.ui.theme.phosphorGlow
import com.streamflow.ui.theme.rememberBlink
import kotlinx.coroutines.delay

/*
 * Structural primitives for the TERMINAL design style.
 *
 * These exist because a colour/shape swap cannot express ASCII pane headers,
 * bracket buttons or block cursors. Everything else — fills, text colour,
 * corner radius — comes from MaterialTheme so it stays in one place.
 */

/** Terminal text with phosphor bloom. The glow is the single most identifying
 *  detail of the aesthetic, so it lives in a primitive rather than being
 *  re-specified at call sites. */
@Composable
fun GlowText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = LocalTextStyle.current,
    glow: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        style = style.copy(shadow = if (glow) phosphorGlow(color) else null),
    )
}

/**
 * A horizontal run of dashes that fills whatever space is left.
 *
 * Drawn as a dashed line rather than a repeated "-" string so the pane header
 * fits ANY width exactly — measuring monospace glyphs to fill a Row is where
 * ASCII layouts usually break on a phone.
 */
@Composable
private fun DashFill(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier.height(1.dp)) {
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f),
        )
    }
}

/**
 * A "window": black pane, 1px border, ASCII title bar.
 *
 * This is the terminal equivalent of a Card, and the direct replacement for
 * SettingsCard. Title is forced upper-case — the design system's rule, applied
 * once here instead of at every call site.
 */
@Composable
fun TerminalPane(
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = MaterialTheme.colorScheme.outline
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, border)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (title != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlowText("+-", color = border, glow = false,
                    style = MaterialTheme.typography.labelSmall)
                GlowText(
                    " ${title.uppercase()} ",
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DashFill(Modifier.weight(1f), border)
                GlowText("-+", color = border, glow = false,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        content()
    }
}

/**
 * `[ LABEL ]` — the terminal button.
 *
 * Pressed state is inverted video (fill with the ink colour, text goes to the
 * background colour) rather than a ripple, which is both the design system's
 * rule and a much more visible focus/press affordance than a ripple on black.
 */
@Composable
fun BracketButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ink = if (enabled) color else MaterialTheme.colorScheme.outline
    val fg = if (pressed && enabled) MaterialTheme.colorScheme.background else ink
    val bg = if (pressed && enabled) ink else Color.Transparent

    Box(
        modifier
            .background(bg)
            .border(1.dp, ink)
            .androidxClickable(interaction, enabled, onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlowText(
            "[ ${label.uppercase()} ]",
            color = fg,
            glow = !pressed,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

// Kept separate so the clickable's role/semantics stay correct and the
// no-indication choice (inverted video replaces the ripple) is explicit.
private fun Modifier.androidxClickable(
    interaction: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit,
) = this.clickable(
    interactionSource = interaction,
    indication = null,
    enabled = enabled,
    role = androidx.compose.ui.semantics.Role.Button,
    onClick = onClick,
)

/** `[OK]` / `[ERR]` / `[WARN]` status codes. */
@Composable
fun StatusTag(state: String, modifier: Modifier = Modifier) {
    val color = when (state.uppercase()) {
        "OK", "DONE", "LIVE" -> MaterialTheme.colorScheme.primary
        "ERR", "FAIL"        -> MaterialTheme.colorScheme.error
        else                 -> MaterialTheme.colorScheme.secondary
    }
    GlowText(
        "[${state.uppercase()}]",
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
    )
}

/** ASCII divider: `================` or `----------------`. */
@Composable
fun AsciiRule(modifier: Modifier = Modifier, double: Boolean = false) {
    val c = MaterialTheme.colorScheme.outline
    if (double) {
        Column(modifier.fillMaxWidth()) {
            DashFill(Modifier.fillMaxWidth(), c)
            Spacer(Modifier.height(2.dp))
            DashFill(Modifier.fillMaxWidth(), c)
        }
    } else {
        DashFill(modifier.fillMaxWidth(), c)
    }
}

/**
 * Raw-data progress: `[||||||||......]`.
 *
 * The design system explicitly rejects smooth progress bars, so this renders
 * actual characters. Cell count is a parameter because the same primitive
 * serves a wide settings row and a narrow inline meter.
 */
@Composable
fun AsciiBar(
    progress: Float,
    modifier: Modifier = Modifier,
    cells: Int = 20,
    color: Color = MaterialTheme.colorScheme.primary,
    showPercent: Boolean = true,
) {
    val pct = (progress.coerceIn(0f, 1f) * 100).toInt()
    val filled = (progress.coerceIn(0f, 1f) * cells).toInt()
    val bar = "[" + "|".repeat(filled) + ".".repeat((cells - filled).coerceAtLeast(0)) + "]"
    Row(
        modifier.semantics { contentDescription = "$pct percent" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlowText(bar, color = color, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        if (showPercent) {
            Spacer(Modifier.width(6.dp))
            GlowText(
                "$pct%".padStart(4),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                glow = false,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}

/** The blinking block cursor — the heartbeat of the interface. */
@Composable
fun BlinkCursor(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    glyph: String = "█",
) {
    val on by rememberBlink()
    GlowText(
        if (on) glyph else " ",
        modifier = modifier,
        color = color,
        style = LocalTextStyle.current,
        maxLines = 1,
    )
}

/**
 * Headline revealed character by character, then a blinking cursor.
 *
 * The full string is exposed to accessibility services up front via
 * contentDescription — a screen reader announcing a headline one character at
 * a time on every recomposition would be unusable.
 */
@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    charDelayMs: Long = 28,
    showCursor: Boolean = true,
) {
    var shown by remember(text) { mutableStateOf(0) }
    LaunchedEffect(text) {
        shown = 0
        while (shown < text.length) {
            delay(charDelayMs)
            shown++
        }
    }
    Row(modifier.semantics { contentDescription = text }, verticalAlignment = Alignment.Bottom) {
        GlowText(text.take(shown), color = color, style = style)
        if (showCursor) BlinkCursor(color = color)
    }
}

/**
 * A shell prompt input: no box, no focus ring — just `user@host:~$ ` and the
 * caret. A block cursor is shown only while the field is empty; once the user
 * is typing, the real caret has to lead so editing mid-string behaves normally.
 */
@Composable
fun TerminalPrompt(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    prompt: String = "user@streamflow:~$",
    placeholder: String = "",
    imeAction: ImeAction = ImeAction.Search,
    onSubmit: () -> Unit = {},
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlowText(
            "$prompt ",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = primary,
                    shadow = phosphorGlow(primary),
                ),
                cursorBrush = SolidColor(primary),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onSearch = { onSubmit() },
                    onDone = { onSubmit() },
                    onGo = { onSubmit() },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (value.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BlinkCursor(color = primary)
                    if (placeholder.isNotEmpty()) {
                        GlowText(
                            " $placeholder",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            glow = false,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * ASCII wordmark. Rendered as preformatted text at a fixed small size so the
 * character grid holds together on narrow screens; it is decorative, so it is
 * hidden from screen readers behind a single readable label.
 */
@Composable
fun AsciiLogo(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val art = """
         ___ _____ ___ ___   _   __  __ ___ _    _____      __
        / __|_   _| _ \ __| /_\ |  \/  | __| |  / _ \ \    / /
        \__ \ | | |   / _| / _ \| |\/| | _|| |_| (_) \ \/\/ /
        |___/ |_| |_|_\___/_/ \_\_|  |_|_| |____\___/ \_/\_/
    """.trimIndent()
    Text(
        text = art,
        modifier = modifier.semantics { contentDescription = "StreamFlow" },
        color = color,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 7.sp,
            lineHeight = 9.sp,
            shadow = phosphorGlow(color, radius = 6f),
        ),
    )
}
