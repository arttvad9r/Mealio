package com.arttvad9r.mealio.domain.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationFormatterTest {

    @Test
    fun `parses hours and minutes`() {
        assertEquals("1 ч 30 мин", DurationFormatter.format("PT1H30M"))
    }

    @Test
    fun `parses minutes only`() {
        assertEquals("30 мин", DurationFormatter.format("PT30M"))
    }

    @Test
    fun `parses hours only`() {
        assertEquals("2 ч", DurationFormatter.format("PT2H"))
    }

    @Test
    fun `returns null for blank`() {
        assertNull(DurationFormatter.format(null))
        assertNull(DurationFormatter.format(""))
    }

    @Test
    fun `returns null for garbage`() {
        assertNull(DurationFormatter.format("soon"))
    }

    @Test
    fun `returns null for zero`() {
        assertNull(DurationFormatter.format("PT0M"))
    }
}
