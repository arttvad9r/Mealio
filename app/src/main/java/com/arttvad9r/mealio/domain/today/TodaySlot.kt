package com.arttvad9r.mealio.domain.today

import kotlinx.serialization.Serializable

/**
 * One slot of the "Today" screen. The five concrete slots map to a Mealie
 * recipe category by NAME (never a hard-coded UUID): a recipe belongs to a slot
 * when one of its category names equals [categoryName], ignoring case.
 * [EXTRA] accepts any recipe.
 */
@Serializable
enum class TodaySlot(val categoryName: String?) {
    BREAKFAST("Завтрак"),
    MAIN("Основное"),
    SIDE("Гарнир"),
    VEGETABLES("Овощи"),
    SNACK("Перекус"),
    EXTRA(null);

    /** True when a recipe with these Mealie category names can fill this slot. */
    fun matches(recipeCategories: List<String>): Boolean {
        val target = categoryName ?: return true
        return recipeCategories.any { it.trim().equals(target, ignoreCase = true) }
    }
}
