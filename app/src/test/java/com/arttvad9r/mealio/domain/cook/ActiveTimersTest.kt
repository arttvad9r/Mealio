package com.arttvad9r.mealio.domain.cook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveTimersTest {

    private val clockStart = 1_000_000L

    @Test
    fun `remaining is derived from the deadline`() {
        val timers = ActiveTimers().start(totalSeconds = 300, stepNumber = 2, nowMillis = clockStart)
        val timer = timers.items.single()

        assertEquals(300L, timer.remainingSeconds(clockStart))
        assertEquals(240L, timer.remainingSeconds(clockStart + 60_000L))
        assertEquals(0L, timer.remainingSeconds(clockStart + 300_000L))
        // Never goes negative once past the deadline.
        assertEquals(0L, timer.remainingSeconds(clockStart + 900_000L))
    }

    @Test
    fun `finished is determined correctly`() {
        val timers = ActiveTimers().start(totalSeconds = 60, stepNumber = 1, nowMillis = clockStart)
        val timer = timers.items.single()

        assertFalse(timer.isFinished(clockStart + 59_000L))
        assertTrue(timer.isFinished(clockStart + 60_000L))
        assertEquals(setOf(timer.id), timers.finishedIds(clockStart + 60_000L))
        assertTrue(timers.finishedIds(clockStart + 30_000L).isEmpty())
    }

    @Test
    fun `two timers run independently`() {
        val first = ActiveTimers().start(totalSeconds = 300, stepNumber = 2, nowMillis = clockStart)
        val second = first.start(totalSeconds = 1_200, stepNumber = 3, nowMillis = clockStart)

        assertEquals(2, second.items.size)
        val a = second.items[0]
        val b = second.items[1]
        assertEquals(2, a.stepNumber)
        assertEquals(3, b.stepNumber)

        val later = clockStart + 300_000L
        assertEquals(0L, a.remainingSeconds(later))
        assertEquals(900L, b.remainingSeconds(later))
        assertEquals(setOf(a.id), second.finishedIds(later))
        assertTrue(second.hasRunning(later))
    }

    @Test
    fun `removing one timer does not affect another`() {
        val first = ActiveTimers().start(totalSeconds = 300, stepNumber = 2, nowMillis = clockStart)
        val second = first.start(totalSeconds = 1_200, stepNumber = 3, nowMillis = clockStart)
        val survivor = second.items[1]

        val afterRemove = second.remove(second.items[0].id)

        assertEquals(listOf(survivor), afterRemove.items)
        assertEquals(1_200L, afterRemove.items.single().remainingSeconds(clockStart))
    }

    @Test
    fun `non-positive and implausible starts are ignored`() {
        assertEquals(0, ActiveTimers().start(0, stepNumber = 1, nowMillis = clockStart).items.size)
        assertEquals(0, ActiveTimers().start(-5, stepNumber = 1, nowMillis = clockStart).items.size)
    }

    @Test
    fun `ids are unique per started timer`() {
        val timers = ActiveTimers()
            .start(60, stepNumber = 1, nowMillis = clockStart)
            .start(60, stepNumber = 1, nowMillis = clockStart)
        assertEquals(2, timers.items.map { it.id }.toSet().size)
    }
}
