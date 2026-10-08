package com.arttvad9r.mealio.domain.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyTargetTest {

    @Test
    fun `default is 2300`() {
        assertEquals(2300, DailyTarget.DEFAULT_CALORIES)
    }

    @Test
    fun `valid input parses`() {
        assertEquals(2000, DailyTarget.parseInput("2000"))
        assertEquals(500, DailyTarget.parseInput("500"))
        assertEquals(10000, DailyTarget.parseInput("10000"))
        assertEquals(2300, DailyTarget.parseInput("  2300  "))
    }

    @Test
    fun `below the minimum is rejected`() {
        assertNull(DailyTarget.parseInput("499"))
        assertNull(DailyTarget.parseInput("0"))
        assertNull(DailyTarget.parseInput("-100"))
    }

    @Test
    fun `above the maximum is rejected`() {
        assertNull(DailyTarget.parseInput("10001"))
    }

    @Test
    fun `empty and non numeric input is rejected`() {
        assertNull(DailyTarget.parseInput(""))
        assertNull(DailyTarget.parseInput("   "))
        assertNull(DailyTarget.parseInput("abc"))
        assertNull(DailyTarget.parseInput("20 00"))
    }

    @Test
    fun `coerce clamps to the bounds`() {
        assertEquals(500, DailyTarget.coerce(0))
        assertEquals(500, DailyTarget.coerce(-10))
        assertEquals(10000, DailyTarget.coerce(99999))
        assertEquals(2300, DailyTarget.coerce(2300))
    }

    @Test
    fun `progress uses calories over target`() {
        assertEquals(1317f / 2300f, DailyTarget.progress(1317.0, 2300), 0.0001f)
        assertEquals(1317f / 2000f, DailyTarget.progress(1317.0, 2000), 0.0001f)
    }

    @Test
    fun `progress clamps when calories exceed the target`() {
        assertEquals(1f, DailyTarget.progress(2450.0, 2000), 0.0001f)
    }

    @Test
    fun `progress is zero for an empty day`() {
        assertEquals(0f, DailyTarget.progress(0.0, 2300), 0.0001f)
    }
}
