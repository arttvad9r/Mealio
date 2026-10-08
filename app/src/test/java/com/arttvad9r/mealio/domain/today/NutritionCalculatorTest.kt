package com.arttvad9r.mealio.domain.today

import com.arttvad9r.mealio.domain.model.Nutrition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionCalculatorTest {

    private fun nutrition(
        calories: String? = null,
        protein: String? = null,
        fat: String? = null,
        carbs: String? = null,
    ) = Nutrition(calories = calories, protein = protein, fat = fat, carbs = carbs)

    @Test
    fun `recipe servings 1 keeps values`() {
        val c = NutritionCalculator.contribution(nutrition(calories = "800"), 1.0, 1.0)
        assertEquals(800.0, c.calories, 0.001)
    }

    @Test
    fun `recipe servings 2 halves a single serving`() {
        // Matches the task example: 800 kcal across 2 servings, 1 selected -> 400.
        val c = NutritionCalculator.contribution(nutrition(calories = "800"), 2.0, 1.0)
        assertEquals(400.0, c.calories, 0.001)
    }

    @Test
    fun `selected servings multiply the per-serving value`() {
        val c = NutritionCalculator.contribution(nutrition(calories = "800"), 2.0, 3.0)
        assertEquals(1200.0, c.calories, 0.001)
    }

    @Test
    fun `fractional recipe servings are honoured`() {
        val c = NutritionCalculator.contribution(nutrition(calories = "100"), 2.5, 1.0)
        assertEquals(40.0, c.calories, 0.001)
    }

    @Test
    fun `missing recipe servings assumes one serving`() {
        val c = NutritionCalculator.contribution(nutrition(calories = "500"), null, 2.0)
        assertEquals(1000.0, c.calories, 0.001)
    }

    @Test
    fun `zero recipe servings assumes one serving`() {
        val c = NutritionCalculator.contribution(nutrition(calories = "500"), 0.0, 1.0)
        assertEquals(500.0, c.calories, 0.001)
    }

    @Test
    fun `partial nutrition fills known values and zeroes unknown`() {
        val c = NutritionCalculator.contribution(
            nutrition(calories = "300", protein = null, fat = "10 g", carbs = "garbage"),
            1.0,
            1.0,
        )
        assertEquals(300.0, c.calories, 0.001)
        assertEquals(0.0, c.protein, 0.001)
        assertEquals(10.0, c.fat, 0.001)
        assertEquals(0.0, c.carbs, 0.001)
    }

    @Test
    fun `perServing returns null for unknown value`() {
        assertNull(NutritionCalculator.perServing(null, 2.0))
        assertNull(NutritionCalculator.perServing("garbage", 2.0))
    }

    @Test
    fun `daily sum adds several dishes`() {
        val a = NutritionCalculator.contribution(nutrition(calories = "800", protein = "40"), 2.0, 1.0)
        val b = NutritionCalculator.contribution(nutrition(calories = "200", protein = "5"), 1.0, 2.0)
        val total = NutritionCalculator.sum(listOf(a, b))
        assertEquals(800.0, total.calories, 0.001) // 400 + 400
        assertEquals(30.0, total.protein, 0.001) // 20 + 10
    }

    @Test
    fun `empty sum is zero`() {
        assertEquals(DailyNutrition(), NutritionCalculator.sum(emptyList()))
    }
}
