package com.streamflow.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.streamflow.data.ErrorPresentation
import com.streamflow.data.ExtractionError

/*
 * The two states every screen has and only some screens had.
 *
 * EmptyState lived here in spirit but not in fact: it was a `private fun` in
 * LibraryScreen.kt, so it could not be used from anywhere else. Every other
 * screen hand-rolled its own -- Search, Channel, Playlists and the player queue
 * each printed a bare centred Text with no icon, no explanation of how to fill
 * the list, and no way out. Four screens, four different answers to the same
 * question, and no way to change them together.
 *
 * Errors were worse, because they also disagreed about what the user could do.
 * See ErrorPresentation for that half.
 */

/**
 * Nothing here yet, and how to change that.
 *
 * [actionLabel]/[onAction] are optional because some empty states genuinely
 * have no next step from where the user is standing -- but most do, and a dead
 * end with an explanation is still a dead end.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp)
        ) {
            // Decorative: the title and subtitle below already say everything
            // this glyph says, so announcing it would repeat the message.
            Icon(
                icon, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.25f),
                modifier = Modifier.size(64.dp).clearAndSetSemantics {}
            )
            Spacer(Modifier.height(16.dp))
            Text(
                title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(0.55f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f),
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/**
 * A failure, named, with the one action that can actually help.
 *
 * The action comes from [ErrorPresentation], not from the calling screen, so
 * "Try again" cannot appear on an error the app has already worked out is not
 * retryable. A screen supplies the handlers; which one is offered is decided by
 * the error itself.
 *
 * [message] overrides the standard body for the rare case where a screen knows
 * something more specific -- Feed, for instance, can say the subscriptions
 * themselves returned nothing rather than describing a generic network fault.
 *
 * [onCheckForUpdate] is optional: screens that cannot reach the update flow
 * fall back to offering nothing rather than a button that goes nowhere.
 */
@Composable
fun ErrorState(
    error: ExtractionError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    onCheckForUpdate: (() -> Unit)? = null,
) {
    val view = ErrorPresentation.of(error)
    val glyph = when (view.glyph) {
        ErrorPresentation.Glyph.OFFLINE -> Icons.Rounded.WifiOff
        ErrorPresentation.Glyph.TIMEOUT -> Icons.Rounded.CloudOff
        ErrorPresentation.Glyph.UNAVAILABLE -> Icons.Rounded.VideocamOff
        ErrorPresentation.Glyph.LOCKED -> Icons.Rounded.Lock
        ErrorPresentation.Glyph.BROKEN -> Icons.Rounded.SyncProblem
        ErrorPresentation.Glyph.GENERIC -> Icons.Rounded.ErrorOutline
    }

    // An action the host cannot perform must not be drawn. CHECK_FOR_UPDATE
    // without a handler would be a button that does nothing at all, which is
    // worse than the wrong button.
    val handler: (() -> Unit)? = when (view.action) {
        ErrorPresentation.Action.RETRY,
        ErrorPresentation.Action.WAIT_FOR_NETWORK -> onRetry
        ErrorPresentation.Action.CHECK_FOR_UPDATE -> onCheckForUpdate
        ErrorPresentation.Action.DISMISS -> null
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp)
        ) {
            Icon(
                glyph, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f),
                modifier = Modifier.size(56.dp).clearAndSetSemantics {}
            )
            Spacer(Modifier.height(16.dp))
            // TalkBack reads the pair as one announcement; two separate nodes
            // made the reason arrive detached from the heading.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${view.title}. ${message ?: view.body}"
                }
            ) {
                Text(
                    view.title, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    message ?: view.body, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            val label = view.actionLabel
            if (label != null && handler != null) {
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(onClick = handler) { Text(label) }
            }
        }
    }
}
