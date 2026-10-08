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
 * Bounds and default for the user-configurable daily calorie target. The value
 * itself is a user setting (see SettingsStore); this only guards it, so a bad
 * stored or typed value can never reach the UI.
 */
object DailyTarget {
    const val DEFAULT_CALORIES: Int = 2300
    const val MIN_CALORIES: Int = 500
    const val MAX_CALORIES: Int = 10000

    /**
     * Parses a user-typed kcal amount. Returns null when the input is empty, not
     * a number, or outside [MIN_CALORIES]..[MAX_CALORIES] — the caller shows a
     * localised error instead of saving a bad value.
     */
    fun parseInput(raw: String): Int? {
        val value = raw.trim().toIntOrNull() ?: return null
        return value.takeIf { it in MIN_CALORIES..MAX_CALORIES }
    }

    /** Keeps a stored or programmatic target inside the allowed bounds. */
    fun coerce(value: Int): Int = value.coerceIn(MIN_CALORIES, MAX_CALORIES)

    /**
     * Summary-bar fill fraction for `calories / target`, clamped to 0..1 so a
     * day that exceeds the goal never renders past a full bar. The number shown
     * next to the bar is NOT clamped — the real value stays visible.
     */
    fun progress(calories: Double, target: Int): Float {
        if (target <= 0) return 0f
        return (calories / target).coerceIn(0.0, 1.0).toFloat()
    }
}
