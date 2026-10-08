package com.arttvad9r.mealio.domain.cook

import org.junit.Assert.assertEquals
import org.junit.Test

class CookTimerParserTest {

    private fun single(text: String): Duration {
        val result = CookTimerParser.parse(text)
        check(result is CookTime.Single) { "expected Single for \"$text\", got $result" }
        return result.duration
    }

    private fun seconds(text: String): Long = single(text).totalSeconds

    @Test
    fun `english minutes word and abbreviation`() {
        assertEquals(6 * 60, seconds("Fry the chicken 6 minutes"))
        assertEquals(20 * 60, seconds("Boil for 20 min"))
        assertEquals(1 * 60, seconds("Rest 1 minute"))
    }

    @Test
    fun `russian minutes and abbreviations`() {
        assertEquals(6 * 60, seconds("Обжарить курицу 6 минут"))
        assertEquals(20 * 60, seconds("Варить 20 мин"))
        assertEquals(1 * 60, seconds("Дать постоять 1 минуту"))
    }

    @Test
    fun `hours in both languages`() {
        assertEquals(3_600, seconds("Leave for 1 hour"))
        assertEquals(3_600, seconds("Оставить на 1 час"))
        assertEquals(2 * 3_600, seconds("Chill 2 hours"))
        assertEquals(2 * 3_600, seconds("Охлаждать 2 часа"))
    }

    @Test
    fun `seconds in both languages`() {
        assertEquals(30, seconds("Whisk 30 seconds"))
        assertEquals(15, seconds("Взбивать 15 секунд"))
    }

    @Test
    fun `compound durations`() {
        assertEquals(3_600 + 20 * 60, seconds("Bake 1 hour 20 minutes"))
        assertEquals(3_600 + 20 * 60, seconds("Запекать 1 час 20 минут"))
        // unit-less tail inherits the next smaller unit
        assertEquals(3_600 + 20 * 60, seconds("Bake 1 hour 20"))
    }

    @Test
    fun `range uses both obvious values`() {
        val dash = CookTimerParser.parse("Обжарить 6–8 минут")
        assertEquals(CookTime.Range(Duration(360), Duration(480)), dash)

        val hyphen = CookTimerParser.parse("Fry 6-8 minutes")
        assertEquals(CookTime.Range(Duration(360), Duration(480)), hyphen)

        val min = CookTimerParser.parse("Simmer 6–8 min")
        assertEquals(CookTime.Range(Duration(360), Duration(480)), min)
    }

    @Test
    fun `no time yields none`() {
        assertEquals(CookTime.None, CookTimerParser.parse("Нарезать лук кубиками."))
        assertEquals(CookTime.None, CookTimerParser.parse(""))
        assertEquals(CookTime.None, CookTimerParser.parse("Add 2 eggs and 100 g flour"))
    }

    @Test
    fun `temperature and quantity numbers are not mistaken for time`() {
        assertEquals(CookTime.None, CookTimerParser.parse("Разогреть духовку до 200 градусов"))
        assertEquals(CookTime.None, CookTimerParser.parse("Mix until smooth, about 3 ingredients"))
    }

    @Test
    fun `only the first time expression is used`() {
        assertEquals(5 * 60, seconds("Fry 5 minutes, then rest 10 minutes"))
    }
}
