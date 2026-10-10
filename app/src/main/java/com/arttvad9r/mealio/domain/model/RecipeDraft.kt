package com.arttvad9r.mealio.domain.model

/**
 * The V1.5 editable recipe fields — the minimal write shape Mealio sends to Mealie.
 *
 * Ingredients are write-ready drafts ([RecipeIngredientDraft]): either a structured
 * ingredient produced by Mealie's server-side parser, or a note-only fallback that
 * keeps the user's text verbatim. [instructions] are plain
 * step texts ordered by their position in the list. Fields Mealio does not edit
 * (nutrition, categories, tags, settings, ...) are deliberately absent, so a write
 * can never touch them.
 */
data class RecipeDraft(
    val name: String,
    val description: String? = null,
    val servings: Double? = null,
    val ingredients: List<RecipeIngredientDraft> = emptyList(),
    val instructions: List<String> = emptyList(),
)

/**
 * One ingredient ready to be written. [originalText] is always the trimmed line the
 * user typed — it is what a fallback keeps and what the structured form reports as
 * Mealie's `originalText`.
 *
 * [quantity], [unitName] and [foodName] are set only when Mealie's parser produced a
 * usable result; when all three are null the ingredient is written as note-only, as
 * the manual baseline did. Mealie builds the `display` string server-side, so it is
 * never sent from Android.
 */
data class RecipeIngredientDraft(
    val originalText: String,
    val quantity: Double? = null,
    val unitName: String? = null,
    val foodName: String? = null,
    val note: String? = null,
) {
    /** True when the parser produced structure worth writing (not a note-only line). */
    val isStructured: Boolean
        get() = quantity != null || unitName != null || foodName != null

    companion object {
        /** The safe fallback for a line the parser did not (or could not) structure. */
        fun fallback(line: String) = RecipeIngredientDraft(originalText = line, note = line)
    }
}

/** One server-parsed ingredient line (POST /api/parser/ingredients). */
data class ParsedIngredient(
    val input: String?,
    val quantity: Double?,
    val unitName: String?,
    val foodName: String?,
    val note: String?,
    val display: String?,
)
