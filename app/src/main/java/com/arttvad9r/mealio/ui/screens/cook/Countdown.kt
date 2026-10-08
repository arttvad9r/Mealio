package com.arttvad9r.mealio.ui.screens.cook

/**
 * Renders a countdown as `mm:ss`, or `h:mm:ss` above an hour. Pure, so it is
 * unit-tested without an Android context; the timer length itself is worded
 * through [com.arttvad9r.mealio.domain.format.DurationFormatter.formatTimer].
 */
fun formatCountdown(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3_600
    val minutes = (safe % 3_600) / 60
    val secs = safe % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%02d:%02d".format(minutes, secs)
    }
}
