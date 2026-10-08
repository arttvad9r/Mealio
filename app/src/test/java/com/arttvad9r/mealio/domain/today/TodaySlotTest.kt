package com.arttvad9r.mealio.domain.today

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodaySlotTest {

    @Test
    fun `breakfast matches the Breakfast category ignoring case`() {
        assertTrue(TodaySlot.BREAKFAST.matches(listOf("завтрак")))
        assertTrue(TodaySlot.BREAKFAST.matches(listOf(" Завтрак ")))
    }

    @Test
    fun `main matches Osnovnoe`() {
        assertTrue(TodaySlot.MAIN.matches(listOf("Основное")))
        assertFalse(TodaySlot.MAIN.matches(listOf("Завтрак")))
    }

    @Test
    fun `side and vegetables map to their categories`() {
        assertTrue(TodaySlot.SIDE.matches(listOf("Гарнир")))
        assertTrue(TodaySlot.VEGETABLES.matches(listOf("Овощи")))
    }

    @Test
    fun `snack matches Perekus`() {
        assertTrue(TodaySlot.SNACK.matches(listOf("Перекус")))
    }

    @Test
    fun `extra accepts any recipe`() {
        assertTrue(TodaySlot.EXTRA.matches(emptyList()))
        assertTrue(TodaySlot.EXTRA.matches(listOf("Что угодно")))
    }

    @Test
    fun `missing category yields no match`() {
        assertFalse(TodaySlot.BREAKFAST.matches(emptyList()))
        assertFalse(TodaySlot.BREAKFAST.matches(listOf("Обед")))
    }
}
