package com.arttvad9r.mealio.domain.today

/** Summed nutrition for the day. Unknown values simply do not contribute. */
data class DailyNutrition(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
) {
    operator fun plus(other: DailyNutrition): DailyNutrition = DailyNutrition(
        calories = calories + other.calories,
        protein = protein + other.protein,
        fat = fat + other.fat,
        carbs = carbs + other.carbs,
    )

    val isEmpty: Boolean
        get() = calories == 0.0 && protein == 0.0 && fat == 0.0 && carbs == 0.0
}

/**
 * The day's targets. Kept in one central place so it can become a real setting
 * later without touching the screen.
 */
object TodayTargets {
    const val CALORIES: Double = 2300.0
}
