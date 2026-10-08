package com.arttvad9r.mealio.domain.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionFormatterTest {

    private val cal = NutritionFormatter.Metric.CALORIES
    private val pro = NutritionFormatter.Metric.PROTEIN

    @Test
    fun `calories get localized unit`() {
        assertEquals("343 ккал", NutritionFormatter.format("343", cal))
    }

    @Test
    fun `integer gram value drops trailing zero`() {
        assertEquals("12 г", NutritionFormatter.format("12.0", pro))
    }

    @Test
    fun `comma decimal separator for fractional grams`() {
        assertEquals("3,4 г", NutritionFormatter.format("3.4", pro))
    }

    @Test
    fun `two decimals kept when significant`() {
        assertEquals("3,45 г", NutritionFormatter.format("3.45", pro))
    }

    @Test
    fun `trailing decimal zero stripped`() {
        assertEquals("62 г", NutritionFormatter.format("62.0", pro))
    }

    @Test
    fun `value already carrying a unit is normalised`() {
        assertEquals("3,4 г", NutritionFormatter.format("3.4 g", pro))
    }

    @Test
    fun `comma input from server is understood`() {
        assertEquals("3,4 г", NutritionFormatter.format("3,4", pro))
    }

    @Test
    fun `blank and null yield null`() {
        assertNull(NutritionFormatter.format(null, pro))
        assertNull(NutritionFormatter.format("", pro))
        assertNull(NutritionFormatter.format("   ", pro))
    }

    @Test
    fun `non-numeric text is passed through untouched`() {
        assertEquals("trace", NutritionFormatter.format("trace", pro))
    }
}
