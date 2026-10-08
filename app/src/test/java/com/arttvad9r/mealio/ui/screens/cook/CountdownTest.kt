package com.arttvad9r.mealio.ui.screens.cook

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownTest {

    @Test
    fun `under an hour shows minutes and seconds`() {
        assertEquals("06:00", formatCountdown(360))
        assertEquals("00:30", formatCountdown(30))
        assertEquals("01:05", formatCountdown(65))
    }

    @Test
    fun `an hour or more shows hours`() {
        assertEquals("1:20:00", formatCountdown(4_800))
        assertEquals("2:00:00", formatCountdown(7_200))
    }

    @Test
    fun `negative clamps to zero`() {
        assertEquals("00:00", formatCountdown(-5))
    }
}
