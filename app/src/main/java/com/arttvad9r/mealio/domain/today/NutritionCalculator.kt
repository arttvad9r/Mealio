package com.arttvad9r.mealio.domain.today

import com.arttvad9r.mealio.domain.model.Nutrition

/**
 * Turns recipe nutrition into a daily contribution.
 *
 * Mealie stores nutrition values per RECIPE and does NOT scale them when the
 * servings/yield change (confirmed by the Mealie FAQ: "the values you enter are
 * static for the recipe and no scaling is being done when changing Servings").
 * A recipe's per-serving value is therefore `value / recipeServings`, and the
 * contribution of `selectedServings` is `value / recipeServings * selectedServings`.
 *
 * When the recipe has no serving count we assume the stored value already
 * refers to one serving (factor = selectedServings).
 */
object NutritionCalculator {

    fun perServing(value: String?, recipeServings: Double?): Double? {
        val parsed = NutritionParser.parse(value) ?: return null
        val base = recipeServings?.takeIf { it > 0.0 } ?: return parsed
        return parsed / base
    }

    fun contribution(
        nutrition: Nutrition,
        recipeServings: Double?,
        selectedServings: Double,
    ): DailyNutrition = DailyNutrition(
        calories = scaled(nutrition.calories, recipeServings, selectedServings),
        protein = scaled(nutrition.protein, recipeServings, selectedServings),
        fat = scaled(nutrition.fat, recipeServings, selectedServings),
        carbs = scaled(nutrition.carbs, recipeServings, selectedServings),
    )

    fun sum(contributions: List<DailyNutrition>): DailyNutrition =
        contributions.fold(DailyNutrition()) { acc, c -> acc + c }

    private fun scaled(value: String?, recipeServings: Double?, selectedServings: Double): Double =
        perServing(value, recipeServings)?.times(selectedServings) ?: 0.0
}
