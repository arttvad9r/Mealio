package com.arttvad9r.mealio.domain.cook

import org.junit.Assert.assertEquals
import org.junit.Test

class CookTimerParserTest {

    private fun suggestions(text: String): List<TimerSuggestion> =
        CookTimerParser.parseSuggestions(text)

    private fun only(text: String): TimerSuggestion {
        val result = suggestions(text)
        assertEquals("expected exactly one suggestion for \"$text\", got $result", 1, result.size)
        return result.single()
    }

    private fun seconds(text: String): Long {
        val single = only(text)
        check(single is TimerSuggestion.Single) { "expected Single for \"$text\", got $single" }
        return single.duration.totalSeconds
    }

    private fun listSeconds(text: String): List<Long> = suggestions(text).map { suggestion ->
        check(suggestion is TimerSuggestion.Single) { "expected Single, got $suggestion" }
        suggestion.duration.totalSeconds
    }

    private fun range(text: String): Pair<Long, Long> {
        val single = only(text)
        check(single is TimerSuggestion.Range) { "expected Range for \"$text\", got $single" }
        return single.from.totalSeconds to single.to.totalSeconds
    }

    // --- required RU cases -----------------------------------------------------

    @Test
    fun `ru fry six minutes`() = assertEquals(6 * 60, seconds("Обжарить 6 минут"))

    @Test
    fun `ru boil twenty min`() = assertEquals(20 * 60, seconds("Варить 20 мин"))

    @Test
    fun `ru leave for one hour`() = assertEquals(60 * 60, seconds("Оставить на 1 час"))

    @Test
    fun `ru bake one hour twenty minutes`() =
        assertEquals(80 * 60, seconds("Запекать 1 час 20 минут"))

    @Test
    fun `ru range six to eight`() = assertEquals(6 * 60L to 8 * 60L, range("Готовить 6–8 минут"))

    @Test
    fun `ru two independent times`() =
        assertEquals(listOf(5 * 60L, 10 * 60L), listSeconds("5 минут, затем ещё 10 минут"))

    // --- required EN cases -----------------------------------------------------

    @Test
    fun `en cook for six minutes`() = assertEquals(6 * 60, seconds("Cook for 6 minutes"))

    @Test
    fun `en simmer twenty min`() = assertEquals(20 * 60, seconds("Simmer for 20 min"))

    @Test
    fun `en rest for one hour`() = assertEquals(60 * 60, seconds("Rest for 1 hour"))

    @Test
    fun `en cook one hour twenty minutes`() =
        assertEquals(80 * 60, seconds("Cook for 1 hour 20 minutes"))

    @Test
    fun `en range six to eight`() = assertEquals(6 * 60L to 8 * 60L, range("Cook for 6-8 minutes"))

    // --- false positives -------------------------------------------------------

    @Test
    fun `no time at all`() {
        assertEquals(emptyList<TimerSuggestion>(), suggestions("Нарезать лук кубиками."))
        assertEquals(emptyList<TimerSuggestion>(), suggestions("Step 5"))
        assertEquals(emptyList<TimerSuggestion>(), suggestions("180 °C"))
        assertEquals(emptyList<TimerSuggestion>(), suggestions("Add 2 eggs and 300 g flour"))
        assertEquals(emptyList<TimerSuggestion>(), suggestions(""))
    }

    // --- extra coverage --------------------------------------------------------

    @Test
    fun `seconds and abbreviations`() {
        assertEquals(30, seconds("Whisk 30 seconds"))
        assertEquals(15, seconds("Взбивать 15 секунд"))
    }

    @Test
    fun `other range separators`() {
        assertEquals(6 * 60L to 8 * 60L, range("Обжарить 6 - 8 мин"))
        assertEquals(6 * 60L to 8 * 60L, range("Simmer 6–8 mins"))
    }

    @Test
    fun `independent suggestions keep text order`() {
        val result = suggestions("Fry 5 minutes, then rest 10 minutes")
        assertEquals(2, result.size)
        assertEquals(5 * 60L, (result[0] as TimerSuggestion.Single).duration.totalSeconds)
        assertEquals(10 * 60L, (result[1] as TimerSuggestion.Single).duration.totalSeconds)
    }
}
