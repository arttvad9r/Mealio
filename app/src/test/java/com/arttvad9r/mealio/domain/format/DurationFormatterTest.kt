package com.arttvad9r.mealio.domain.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Parsing is exercised through the Context-free core; the renderer supplies the
 * wording exactly as production resolves it from resources.
 */
class DurationFormatterTest {

    private fun english(iso: String?): String? =
        DurationFormatter.format(iso) { h, m ->
            when {
                h > 0 && m > 0 -> "$h h $m min"
                h > 0 -> "$h h"
                else -> "$m min"
            }
        }

    @Test
    fun `hours and minutes`() = assertEquals("1 h 30 min", english("PT1H30M"))

    @Test
    fun `minutes only`() = assertEquals("30 min", english("PT30M"))

    @Test
    fun `whole hours`() = assertEquals("2 h", english("PT2H"))

    @Test
    fun `null input`() = assertNull(english(null))

    @Test
    fun `blank input`() = assertNull(english(""))

    @Test
    fun `unparseable input`() = assertNull(english("soon"))

    @Test
    fun `zero duration`() = assertNull(english("PT0M"))
}
