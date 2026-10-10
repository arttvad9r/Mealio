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
 * One existing Mealie unit/food, as the server itself names it, referenced from a write.
 *
 * [name] is not decoration: Mealie's request schema for `recipeIngredient[].unit`/`food`
 * (`IngredientUnit-Input` / `IngredientFood-Input`) declares `name` required, so an
 * id-only object is rejected with HTTP 422 before it ever reaches the DB layer. [name]
 * therefore always comes from the server entity the lookup found — never from the
 * parser's suggestion.
 */
data class IngredientRef(val id: String, val name: String)

/**
 * One ingredient ready to be written. [originalText] is always the trimmed line the
 * user typed — it is what a fallback keeps and what the structured form reports as
 * Mealie's `originalText`.
 *
 * [unitName]/[foodName] are the *parser's* suggestions. They are lookup keys only and
 * are never written: Mealie's PATCH resolves those relations by `id`
 * (db/models/_model_utils/auto_init.py, the MANYTOONE branch does `val.get("id")` and
 * raises `ValueError: Expected 'id' to be provided for unit` for a name-only reference —
 * the HTTP 500 of the first smoke test). [withResolvedRefs] turns them into the existing
 * Mealie [unitRef]/[foodRef]; when neither name resolves, the whole line degrades to the
 * note-only fallback.
 *
 * [quantity], [note] and [originalText] are written whenever the line is structured
 * ([isStructured]); a line without a resolved reference is written note-only, as the
 * manual baseline did. Mealie builds the `display` string server-side, so it is never
 * sent from Android.
 */
data class RecipeIngredientDraft(
    val originalText: String,
    val note: String? = null,
    val quantity: Double? = null,
    val unitName: String? = null,
    val foodName: String? = null,
    val unitRef: IngredientRef? = null,
    val foodRef: IngredientRef? = null,
) {
    /**
     * True when the line can be written as a structured ingredient, i.e. at least one
     * of its parser suggestions was resolved to an existing Mealie entity.
     */
    val isStructured: Boolean
        get() = unitRef != null || foodRef != null

    /** True while the parser names still have to be resolved against the server. */
    val needsRefResolution: Boolean
        get() = unitName != null || foodName != null

    /**
     * Applies the references the server lookup found. A line whose parser suggestions
     * could not be resolved to existing Mealie entities is kept as the user's original
     * line (note-only): writing a made-up unit/food, or a quantity without one, is what
     * broke the first physical smoke test.
     */
    fun withResolvedRefs(unitRef: IngredientRef?, foodRef: IngredientRef?): RecipeIngredientDraft =
        if (unitRef == null && foodRef == null) {
            fallback(originalText)
        } else {
            copy(unitRef = unitRef, foodRef = foodRef, unitName = null, foodName = null)
        }

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
