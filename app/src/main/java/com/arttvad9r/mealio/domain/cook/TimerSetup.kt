package com.arttvad9r.mealio.domain.cook

/**
 * The editable duration of a timer the user is about to start. The recipe text
 * only proposes a starting value: the user nudges it a minute at a time and may
 * freely go above or below what the recipe said. Pure, so the stepping rules are
 * unit-tested without Android.
 */
@JvmInline
value class TimerSetup(val totalSeconds: Long) {

    /** One minute up, clamped to the safe maximum. */
    fun increased(): TimerSetup = of(totalSeconds + STEP_SECONDS)

    /** One minute down, clamped to [MIN_SECONDS] — never 00:00 or negative. */
    fun decreased(): TimerSetup = of(totalSeconds - STEP_SECONDS)

    companion object {
        const val STEP_SECONDS = 60L

        /** A timer never opens at zero; the floor is one minute. */
        const val MIN_SECONDS = 60L

        /** A safe technical ceiling, 23:59:00 — no separate setting is exposed. */
        const val MAX_SECONDS = 23 * 3_600L + 59 * 60L

        /** Manual timers open at a neutral five minutes. */
        val MANUAL = TimerSetup(5 * 60L)

        /** Clamps any value into the allowed range. */
        fun of(seconds: Long): TimerSetup =
            TimerSetup(seconds.coerceIn(MIN_SECONDS, MAX_SECONDS))

        /**
         * The proposed starting value for a parsed suggestion. A range opens at
         * its lower bound; the upper bound is not a limit, only the text the
         * user still sees in the instruction.
         */
        fun initialFor(suggestion: TimerSuggestion): TimerSetup = when (suggestion) {
            is TimerSuggestion.Single -> of(suggestion.duration.totalSeconds)
            is TimerSuggestion.Range -> of(suggestion.from.totalSeconds)
        }
    }
}
