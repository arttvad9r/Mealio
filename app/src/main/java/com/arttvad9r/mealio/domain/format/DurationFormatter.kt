package com.arttvad9r.mealio.domain.format

import android.content.Context
import com.arttvad9r.mealio.R
import java.time.Duration

/**
 * Formats Mealie ISO-8601 duration strings (`PT30M`, `PT1H30M`) for display.
 *
 * The hour/minute wording is client-owned and always comes from Android string
 * resources (via [Context]) or an injected renderer — this file holds no literal
 * English/Russian wording.
 */
object DurationFormatter {

    /** Alias kept for screen readability. */
    fun humanReadable(iso: String?, context: Context? = null): String? =
        format(iso, context)

    /** Returns e.g. `30 min`, `1 h 30 min`, or null when the value is absent/invalid. */
    fun format(iso: String?, context: Context? = null): String? =
        format(iso) { hours, minutes ->
            val res = context?.resources
            when {
                hours > 0 && minutes > 0 -> res?.getString(R.string.duration_hours_minutes, hours, minutes)
                hours > 0 -> res?.getString(R.string.duration_hours, hours)
                else -> res?.getString(R.string.duration_minutes, minutes)
            }
        }

    /**
     * Locale-aware core: parses the ISO duration and hands the parts to [render],
     * which supplies the localised wording. Kept public so parsing can be tested
     * without an Android [Context].
     */
    fun format(iso: String?, render: (hours: Int, minutes: Int) -> String?): String? {
        if (iso.isNullOrBlank()) return null
        val duration = runCatching { Duration.parse(iso) }.getOrNull() ?: return null
        val minutesTotal = duration.toMinutes()
        if (minutesTotal <= 0) return null
        return render((minutesTotal / 60).toInt(), (minutesTotal % 60).toInt())
    }
}
