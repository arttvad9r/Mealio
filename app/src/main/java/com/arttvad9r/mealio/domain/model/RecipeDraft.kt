package com.arttvad9r.mealio.domain.model

/**
 * The V1.5 editable recipe fields — the minimal write shape Mealio sends to Mealie.
 *
 * Ingredients are free-text lines that are stored as Mealie note-only ingredients
 * (the manual baseline before NLP parsing is wired in); [instructions] are plain
 * step texts ordered by their position in the list. Fields Mealio does not edit
 * (nutrition, categories, tags, settings, ...) are deliberately absent, so a write
 * can never touch them.
 */
data class RecipeDraft(
    val name: String,
    val description: String? = null,
    val servings: Double? = null,
    val ingredients: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
)

/** One server-parsed ingredient line (POST /api/parser/ingredients). */
data class ParsedIngredient(
    val input: String?,
    val quantity: Double?,
    val unitName: String?,
    val foodName: String?,
    val note: String?,
    val display: String?,
)
