package com.arttvad9r.mealio.domain.cook

/**
 * Local extraction of cooking durations from an instruction step's text.
 *
 * Mealie ships no structured per-step duration, so the time is read out of the
 * step body. Supports the two UI languages (EN/RU) and the obvious forms: a
 * single duration ("6 minutes"), a compound one ("1 hour 20 minutes") and a
 * plain range ("6–8 minutes"). Pure and locale-independent — unit-tests without
 * Android.
 *
 * Only the first time expression in a step is used: "don't guess complex
 * semantics".
 */
enum class TimeUnit(val secondsPerUnit: Long) {
    SECOND(1),
    MINUTE(60),
    HOUR(3_600),
}

/** A single duration; [totalSeconds] is the source of truth. */
data class Duration(val totalSeconds: Long) {
    val hours: Int get() = (totalSeconds / 3_600).toInt()
    val minutes: Int get() = ((totalSeconds % 3_600) / 60).toInt()
    val seconds: Int get() = (totalSeconds % 60).toInt()

    /** True for a value worth a timer (rejects the odd bare "1" or a huge guess). */
    val isPlausible: Boolean get() = totalSeconds in 1..(12 * 3_600L)

    companion object {
        fun of(value: Double, unit: TimeUnit): Duration? {
            val seconds = (value * unit.secondsPerUnit).toLong()
            return Duration(seconds).takeIf { it.totalSeconds > 0 }
        }
    }
}

/** What a step's text yielded: nothing, one duration, or a range of two. */
sealed interface CookTime {
    /** No cooking time found. */
    data object None : CookTime

    /** A single duration, ready for a one-tap timer. */
    data class Single(val duration: Duration) : CookTime

    /** Two candidate durations; the user picks which one to run. */
    data class Range(val from: Duration, val to: Duration) : CookTime
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

    // A number, then (optionally) the word right after it — the word is what
    // decides the unit; empty or non-unit words leave the unit unresolved.
    private val TOKEN = Regex("""(?<![\p{L}\d])$NUMBER\s*$WORD""")

    private val CONNECTOR = Regex("""^\s*(?:и|and|,)?\s*$""")
    private val DASH = Regex("""^\s*(?:-|–|—)\s*$""")

    private data class Token(val value: Double, val unit: TimeUnit?, val start: Int, val end: Int)

    fun parse(text: String): CookTime {
        if (text.isBlank()) return CookTime.None
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return CookTime.None

        parseRange(text, tokens)?.let { return it }

        for (start in tokens.indices) {
            collectRun(text, tokens, start)?.let { return CookTime.Single(it) }
        }
        return CookTime.None
    }

    private fun tokenize(text: String): List<Token> =
        TOKEN.findAll(text).map { match ->
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@map null
            val word = match.groupValues[2].lowercase()
            Token(
                value = value,
                unit = UNIT_BY_WORD[word],
                start = match.range.first,
                end = match.range.last + 1,
            )
        }.filterNotNull().toList()

    /** A dash between two numbers with a unit on the right is a range. */
    private fun parseRange(text: String, tokens: List<Token>): CookTime.Range? {
        for (i in 0 until tokens.size - 1) {
            val left = tokens[i]
            val right = tokens[i + 1]
            val unit = right.unit ?: continue
            val gap = text.substring(left.end, right.start)
            if (!DASH.matches(gap)) continue
            val from = Duration.of(left.value, left.unit ?: unit) ?: continue
            val to = Duration.of(right.value, unit) ?: continue
            if (from == to) continue
            val ordered = listOf(from, to).filter { it.isPlausible }.sortedBy { it.totalSeconds }
            if (ordered.size == 2) return CookTime.Range(ordered[0], ordered[1])
        }
        return null
    }

    /**
     * Accumulates connected tokens into one duration. The run must be anchored by
     * at least one explicit unit word; a unit-less number inside a run inherits
     * the next smaller unit of its predecessor ("1 hour 20" -> 1 h 20 min).
     */
    private fun collectRun(text: String, tokens: List<Token>, start: Int): Duration? {
        val startUnit = tokens[start].unit ?: return null
        var total = startUnit.secondsPerUnit * tokens[start].value
        var lastUnit: TimeUnit = startUnit
        var i = start + 1
        while (i < tokens.size) {
            val gap = text.substring(tokens[i - 1].end, tokens[i].start)
            if (!CONNECTOR.matches(gap)) break
            val token = tokens[i]
            val unit = token.unit ?: smaller(lastUnit)
            total += unit.secondsPerUnit * token.value
            lastUnit = unit
            i++
        }
        return Duration(total.toLong()).takeIf { it.isPlausible }
    }

    private fun smaller(unit: TimeUnit): TimeUnit = when (unit) {
        TimeUnit.HOUR -> TimeUnit.MINUTE
        TimeUnit.MINUTE -> TimeUnit.SECOND
        TimeUnit.SECOND -> TimeUnit.MINUTE
    }
}
