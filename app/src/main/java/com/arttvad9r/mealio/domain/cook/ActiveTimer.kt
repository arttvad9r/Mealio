package com.arttvad9r.mealio.domain.cook

/**
 * A running Cook Mode timer, stored as an absolute deadline rather than a
 * decremented counter: the remaining time is always derived from the deadline,
 * so it cannot drift when the UI recomposes, briefly hangs, or the user moves
 * between steps.
 *
 * [deadlineMillis] is a monotonic clock reading (see the ViewModel, which feeds
 * `SystemClock.elapsedRealtime()`), so it is immune to wall-clock changes.
 */
data class ActiveTimer(
    val id: Long,
    val totalSeconds: Long,
    val deadlineMillis: Long,
    /** 1-based number of the step this timer was started from, for its label. */
    val stepNumber: Int,
)

/** Seconds left, never negative. */
fun ActiveTimer.remainingSeconds(nowMillis: Long): Long =
    ((deadlineMillis - nowMillis) / 1_000L).coerceAtLeast(0L)

fun ActiveTimer.isFinished(nowMillis: Long): Boolean = deadlineMillis <= nowMillis

/**
 * The immutable set of active timers. Independent of any ViewModel or
 * dispatcher, so the deadline and independence rules are unit-tested directly.
 */
data class ActiveTimers(
    val items: List<ActiveTimer> = emptyList(),
) {
    /** Ids are monotonic: never reuse one, so a removed timer's id stays gone. */
    private val nextId: Long get() = (items.maxOfOrNull { it.id } ?: 0L) + 1L

    fun start(totalSeconds: Long, stepNumber: Int, nowMillis: Long): ActiveTimers {
        if (totalSeconds <= 0) return this
        val timer = ActiveTimer(
            id = nextId,
            totalSeconds = totalSeconds,
            deadlineMillis = nowMillis + totalSeconds * 1_000L,
            stepNumber = stepNumber,
        )
        return copy(items = items + timer)
    }

    fun remove(id: Long): ActiveTimers = copy(items = items.filterNot { it.id == id })

    fun hasRunning(nowMillis: Long): Boolean = items.any { !it.isFinished(nowMillis) }

    /** Ids of the timers whose countdown has reached zero. */
    fun finishedIds(nowMillis: Long): Set<Long> =
        items.filter { it.isFinished(nowMillis) }.map { it.id }.toSet()
}
