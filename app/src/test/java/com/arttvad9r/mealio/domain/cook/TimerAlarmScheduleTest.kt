package com.arttvad9r.mealio.domain.cook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TimerAlarmScheduleTest {

    @Test
    fun `trigger maps a future deadline onto the wall clock`() {
        // 60 s left on the monotonic clock maps to now + 60 s on the wall clock.
        val trigger = wallTriggerAt(
            deadlineMillis = 1_000_000L,
            nowElapsed = 940_000L,
            nowWall = 5_000_000_000L,
        )
        assertEquals(5_000_060_000L, trigger)
    }

    @Test
    fun `a deadline already in the past fires now, not never`() {
        val trigger = wallTriggerAt(
            deadlineMillis = 900_000L,
            nowElapsed = 940_000L,
            nowWall = 5_000_000_000L,
        )
        assertEquals(5_000_000_000L, trigger)
    }

    @Test
    fun `distinct timers get distinct request codes`() {
        assertNotEquals(alarmRequestCode(1L), alarmRequestCode(2L))
    }
}
