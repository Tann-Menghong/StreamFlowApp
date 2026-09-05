package com.streamflow.data

/**
 * What to SHOW the user for each kind of extraction failure, and what to let
 * them do about it.
 *
 * ErrorUtils already works out why a load failed -- it can tell "you are in a
 * tunnel" from "this video was deleted" from "YouTube changed something the
 * extractor cannot parse". All of that was then thrown away at the ViewModel
 * boundary: every screen stored `Error(val message: String)`, so the UI
 * received a sentence and nothing it could branch on.
 *
 * The visible cost was the button. Home and Search both offered "Retry" for
 * every failure, including the two where retrying is known to be pointless:
 * EXTRACTOR_BROKEN, where the fix is a newer StreamFlow, and UNAVAILABLE, where
 * the video is simply gone. So the app confidently offered an action it had
 * already worked out would fail, and the user pressed it until they gave up.
 *
 * Keeping this as data rather than as `if` statements inside a composable is
 * what makes it testable: the rule "never offer Retry for an error ErrorUtils
 * says is not retryable" is a unit test here, and unreachable from a Compose
 * preview.
 */
object ErrorPresentation {

    /**
     * Errors where a MANUAL retry button is offered even though
     * [ExtractionError.isRetryable] is false.
     *
     * The two are different questions. isRetryable governs whether the app
     * should retry by itself, where a wrong guess spends the user's battery
     * and data on a request already known to fail. This set governs whether to
     * offer a button, where the user contributes information the classifier
     * does not have: for CAPTCHA, that enough time has passed; for UNKNOWN,
     * that the failure was never identified in the first place and refusing to
     * let them try again would be the app being certain about something it
     * explicitly is not.
     *
     * Anything else offering RETRY against the classification is a bug, and
     * ErrorPresentationTest fails the build for it.
     */
    val MANUAL_RETRY_EXCEPTIONS = setOf(
        ExtractionError.CAPTCHA,
        ExtractionError.UNKNOWN,
    )

    /** The single most useful thing the user can do next. */
    enum class Action {
        /** Try the same request again. Only for genuinely transient failures. */
        RETRY,

        /** There is no connection; retrying now just fails faster. */
        WAIT_FOR_NETWORK,

        /** The extractor cannot read YouTube's current output. A newer build is
         *  the fix, so send the user to the update check rather than a retry. */
        CHECK_FOR_UPDATE,

        /** Nothing to do here -- the content is gone, blocked or paid. */
        DISMISS,
    }

    /** Which glyph to draw. An enum rather than an ImageVector so this file
     *  stays free of Compose and can be unit-tested. */
    enum class Glyph { OFFLINE, TIMEOUT, UNAVAILABLE, LOCKED, BROKEN, GENERIC }

    data class View(
        val title: String,
        val body: String,
        val glyph: Glyph,
        val action: Action,
    ) {
        /** The button label, or null when there is no useful action. */
        val actionLabel: String?
            get() = when (action) {
                Action.RETRY -> "Try again"
                Action.WAIT_FOR_NETWORK -> "Try again"
                Action.CHECK_FOR_UPDATE -> "Check for update"
                Action.DISMISS -> null
            }
    }

    fun of(error: ExtractionError): View = when (error) {
        ExtractionError.OFFLINE -> View(
            title = "You're offline",
            body = "StreamFlow can't reach YouTube. Check your Wi-Fi or mobile data.",
            glyph = Glyph.OFFLINE,
            // Not DISMISS: the connection can come back at any moment and the
            // user is the one who knows when it has.
            action = Action.WAIT_FOR_NETWORK,
        )
        ExtractionError.TRANSIENT -> View(
            title = "Connection problem",
            body = "The request didn't get through. This usually works on a second attempt.",
            glyph = Glyph.TIMEOUT,
            action = Action.RETRY,
        )
        ExtractionError.UNAVAILABLE -> View(
            title = "Video unavailable",
            body = "This video has been removed or never existed.",
            glyph = Glyph.UNAVAILABLE,
            action = Action.DISMISS,
        )
        ExtractionError.PRIVATE -> View(
            title = "Private video",
            body = "The uploader hasn't shared this one publicly.",
            glyph = Glyph.LOCKED,
            action = Action.DISMISS,
        )
        ExtractionError.GEO_BLOCKED -> View(
            title = "Not available here",
            body = "The uploader has blocked this video in your country.",
            glyph = Glyph.LOCKED,
            action = Action.DISMISS,
        )
        ExtractionError.AGE_RESTRICTED -> View(
            title = "Age-restricted",
            body = "YouTube requires a signed-in account to confirm age, and StreamFlow has none by design.",
            glyph = Glyph.LOCKED,
            action = Action.DISMISS,
        )
        ExtractionError.PAID -> View(
            title = "Members only",
            body = "This video needs a paid membership or purchase on YouTube.",
            glyph = Glyph.LOCKED,
            action = Action.DISMISS,
        )
        ExtractionError.CAPTCHA -> View(
            title = "YouTube wants verification",
            body = "Too many requests came from this connection. It clears on its own after a few minutes.",
            glyph = Glyph.BROKEN,
            // Time fixes this, not attempts -- but the user decides when enough
            // time has passed, so the button stays and is honestly labelled.
            action = Action.RETRY,
        )
        ExtractionError.EXTRACTOR_BROKEN -> View(
            title = "StreamFlow can't read YouTube right now",
            body = "YouTube changed something the extractor doesn't understand yet. " +
                "Retrying won't help — a newer version of the app is the fix.",
            glyph = Glyph.BROKEN,
            action = Action.CHECK_FOR_UPDATE,
        )
        ExtractionError.UNKNOWN -> View(
            title = "Couldn't load",
            body = "Something went wrong. Trying again is usually worth a shot.",
            glyph = Glyph.GENERIC,
            action = Action.RETRY,
        )
    }
}
