package com.arttvad9r.mealio.domain.format

import java.time.Duration

/** Formats Mealie ISO-8601 duration strings (`PT30M`, `PT1H30M`) for display. */
object DurationFormatter {

    /** Alias kept for screen readability. */
    fun humanReadable(iso: String?, context: android.content.Context? = null): String? =
        format(iso, context)

    /** Returns e.g. `30 мин`, `1 ч 30 мин`, or null when the value is absent/invalid. */
    fun format(iso: String?, context: android.content.Context? = null): String? {
        if (iso.isNullOrBlank()) return null
        val duration = runCatching { Duration.parse(iso) }.getOrNull() ?: return null
        val minutesTotal = duration.toMinutes()
        if (minutesTotal <= 0) return null
        val hours = minutesTotal / 60
        val minutes = minutesTotal % 60
        return when {
            hours > 0 && minutes > 0 -> "$hours ч $minutes мин"
            hours > 0 -> "$hours ч"
            else -> "$minutes мин"
        }
    }
}
