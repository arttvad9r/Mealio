package com.arttvad9r.mealio.domain.cook

/**
 * Local extraction of cooking durations from an instruction step's text.
 *
 * Mealie ships no structured per-step duration, so times are read out of the
 * step body. Supports the two UI languages (EN/RU) and the obvious forms: a
 * single duration ("6 minutes"), a compound one ("1 hour 20 minutes"), a plain
 * range ("6–8 minutes") and several independent durations in one step
 * ("5 minutes, then 10 minutes" — each becomes its own suggestion).
 *
 * Deliberately conservative: a number counts only when followed by a known time
 * unit, so "180 °C", "2 eggs", "300 g" and "Step 5" yield nothing. No attempt is
 * made to understand the meaning of the actions.
 *
 * Pure and locale-independent: no Android, no Compose, no regex in the UI.
 */
enum class TimeUnit(val secondsPerUnit: Long) {
    SECOND(1),
    MINUTE(60),
    HOUR(3_600),
}

/** A single duration; [totalSeconds] is the source of truth. */
data class Duration(val totalSeconds: Long) {

    val minutes: Int get() = (totalSeconds / 60).toInt()

    /** True for a value worth a timer (rejects a bare "0" or a huge guess). */
    val isPlausible: Boolean get() = totalSeconds in 1..MAX_SECONDS

    companion object {
        private const val MAX_SECONDS = 12 * 3_600L

        fun of(value: Double, unit: TimeUnit): Duration? =
            Duration((value * unit.secondsPerUnit).toLong()).takeIf { it.totalSeconds > 0 }
    }
}

/**
 * One thing a step can start a timer for: a single duration or an obvious range
 * the user picks between. The UI turns a [Range] into two separate choices and
 * never starts a timer on its own.
 */
sealed interface TimerSuggestion {
    data class Single(val duration: Duration) : TimerSuggestion

    data class Range(val from: Duration, val to: Duration) : TimerSuggestion
}

object CookTimerParser {

    private val NUMBER = """(\d+(?:[.,]\d+)?)"""
    private const val WORD = """([\p{L}]+)?"""

    private val UNIT_WORDS: Map<TimeUnit, Set<String>> = mapOf(
        TimeUnit.SECOND to setOf(
            "сек", "секунда", "секунды", "секунд", "секунду",
            "second", "seconds", "sec", "secs",
        ),
        TimeUnit.MINUTE to setOf(
            "мин", "минута", "минуты", "минут", "минуту",
            "minute", "minutes", "min", "mins",
        ),
        TimeUnit.HOUR to setOf(
            "ч", "час", "часа", "часов", "часу",
            "hour", "hours", "hr", "hrs",
        ),
    )

    private val UNIT_BY_WORD: Map<String, TimeUnit> =
        UNIT_WORDS.entries.flatMap { (unit, words) -> words.map { it to unit } }.toMap()

    // A number, then (optionally) the word right after it — the word decides the
    // unit; an empty or non-unit word (eggs, g, °C) leaves the unit unresolved.
    private val TOKEN = Regex("""(?<![\p{L}\d])$NUMBER\s*$WORD""")

    private val CONNECTOR = Regex("""^\s*(?:и|and|,)?\s*$""")
    private val DASH = Regex("""^\s*(?:-|–|—)\s*$""")

    private data class Token(val value: Double, val unit: TimeUnit?, val start: Int, val end: Int)

    /**
     * Every timer suggestion found in [text], in the order they appear. Empty
     * when the step names no time at all — the UI then shows no timer control.
     */
    fun parseSuggestions(text: String): List<TimerSuggestion> {
        if (text.isBlank()) return emptyList()
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return emptyList()

        val consumed = BooleanArray(tokens.size)
        // (start index, suggestion), so the result can be kept in text order.
        val found = mutableListOf<Pair<Int, TimerSuggestion>>()

        // Ranges first, so "6–8 минут" is not also read as a lone "8".
        for (i in 0 until tokens.size - 1) {
            if (consumed[i] || consumed[i + 1]) continue
            val left = tokens[i]
            val right = tokens[i + 1]
            val unit = right.unit ?: continue
            if (!DASH.matches(text.substring(left.end, right.start))) continue
            val from = Duration.of(left.value, left.unit ?: unit) ?: continue
            val to = Duration.of(right.value, unit) ?: continue
            if (from == to) continue
            val ordered = listOf(from, to).filter { it.isPlausible }.sortedBy { it.totalSeconds }
            if (ordered.size != 2) continue
            found += i to TimerSuggestion.Range(ordered[0], ordered[1])
            consumed[i] = true
            consumed[i + 1] = true
        }

        // The rest: a compound run ("1 hour 20 minutes") or a single duration.
        var i = 0
        while (i < tokens.size) {
            val startUnit = tokens[i].unit
            if (consumed[i] || startUnit == null) {
                i++
                continue
            }
            var total = startUnit.secondsPerUnit * tokens[i].value
            var lastUnit: TimeUnit = startUnit
            consumed[i] = true
            var j = i + 1
            while (j < tokens.size && !consumed[j]) {
                if (!CONNECTOR.matches(text.substring(tokens[j - 1].end, tokens[j].start))) break
                val unit = tokens[j].unit
                val base = lastUnit
                // A compound duration always descends (hour -> minute -> second);
                // two equal units ("5 минут и 10 минут") are separate timers.
                if (unit != null && unit.secondsPerUnit >= base.secondsPerUnit) break
                val resolved = unit ?: smaller(base)
                total += resolved.secondsPerUnit * tokens[j].value
                lastUnit = resolved
                consumed[j] = true
                j++
            }
            val duration = Duration(total.toLong())
            if (duration.isPlausible) found += i to TimerSuggestion.Single(duration)
            i = j
        }

        return found.sortedBy { it.first }.map { it.second }
    }

    private fun tokenize(text: String): List<Token> =
        TOKEN.findAll(text).mapNotNull { match ->
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            Token(
                value = value,
                unit = UNIT_BY_WORD[match.groupValues[2].lowercase()],
                start = match.range.first,
                end = match.range.last + 1,
            )
        }.toList()

    private fun smaller(unit: TimeUnit): TimeUnit = when (unit) {
        TimeUnit.HOUR -> TimeUnit.MINUTE
        TimeUnit.MINUTE -> TimeUnit.SECOND
        TimeUnit.SECOND -> TimeUnit.MINUTE
    }
}
