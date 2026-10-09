package com.arttvad9r.mealio.domain.cook

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerSetupTest {

    @Test
    fun `single duration opens at its value`() {
        val setup = TimerSetup.initialFor(TimerSuggestion.Single(Duration(6 * 60L)))
        assertEquals(6 * 60L, setup.totalSeconds)
    }

    @Test
    fun `range opens at its lower bound`() {
        val setup = TimerSetup.initialFor(
            TimerSuggestion.Range(Duration(6 * 60L), Duration(7 * 60L)),
        )
        assertEquals(6 * 60L, setup.totalSeconds)
    }

    @Test
    fun `increment adds one minute`() {
        assertEquals(7 * 60L, TimerSetup(6 * 60L).increased().totalSeconds)
    }

    @Test
    fun `decrement removes one minute`() {
        assertEquals(5 * 60L, TimerSetup(6 * 60L).decreased().totalSeconds)
    }

    @Test
    fun `cannot go below one minute`() {
        assertEquals(TimerSetup.MIN_SECONDS, TimerSetup(60L).decreased().totalSeconds)
    }

    @Test
    fun `manual default is five minutes`() {
        assertEquals(5 * 60L, TimerSetup.MANUAL.totalSeconds)
    }

    @Test
    fun `stays within the safe maximum`() {
        assertEquals(TimerSetup.MAX_SECONDS, TimerSetup(TimerSetup.MAX_SECONDS).increased().totalSeconds)
    }
}
