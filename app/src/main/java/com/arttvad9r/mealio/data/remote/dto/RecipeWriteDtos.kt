package com.arttvad9r.mealio.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Write DTOs for V1.5 recipe CRUD. Each one mirrors the exact JSON body Mealie
 * v3.28.0 accepts (schemas `CreateRecipe`, `Recipe`, `ScrapeRecipe`,
 * `IngredientsRequest`); they are kept apart from the read DTOs in
 * [MealieDtos.kt] because a request must carry only the fields Mealio means to
 * change.
 *
 * Every optional field defaults to null and the shared JSON
 * (`explicitNulls = false`, `encodeDefaults = false`) therefore omits it from the
 * serialized body. That is what makes PATCH safe: a field Mealie owns but Mealio
 * does not edit (nutrition, categories, tags, settings, orgURL, ...) is never
 * present in the request, so it is never overwritten.
 */

/**
 * Body for `POST /api/recipes`. Mealie's `CreateRecipe` schema accepts only
 * `name`; the rest of the content is applied by a following PATCH. The endpoint
 * answers with the new recipe's slug as a bare JSON string.
 */
@Serializable
data class CreateRecipeRequest(val name: String)

/**
 * Body for `PATCH /api/recipes/{slug}`. Mealie parses this as a full `Recipe`
 * and applies `model_dump(exclude_unset=True)`, so only the keys present in the
 * JSON are written. Only the V1.5 editable fields live here — everything else
 * (nutrition, categories, tags, settings, metadata, durations, ...) is absent by
 * construction and stays untouched.
 */
@Serializable
data class RecipeUpdateRequest(
    val name: String? = null,
    val description: String? = null,
    @SerialName("recipeServings") val recipeServings: Double? = null,
    @SerialName("recipeIngredient") val recipeIngredient: List<RecipeIngredientWriteDto>? = null,
    @SerialName("recipeInstructions") val recipeInstructions: List<RecipeStepWriteDto>? = null,
)

/**
 * One ingredient line. A line whose parser suggestions could not be resolved to
 * existing Mealie entities is written note-only — `{"note": "500 г куриного филе"}` —
 * with [quantity], [unit] and [food] null so they are omitted. A structured line sets
 * [quantity]/[unit]/[food] and carries the user's line in [originalText], exactly like
 * Mealie's own parser output. Mealie builds `display` server-side, so it is never sent
 * from Android.
 *
 * [unit]/[food] are references to entities that already exist on the server and carry
 * their `id` — never a name: Mealie's PATCH builds the ingredient through
 * `RecipeIngredientModel`, whose `auto_init` resolves a MANYTOONE relation by primary
 * key only and raises `ValueError: Expected 'id' to be provided for unit` for a
 * name-only reference (HTTP 500).
 */
@Serializable
data class RecipeIngredientWriteDto(
    val quantity: Double? = null,
    val unit: IngredientUnitRefWriteDto? = null,
    val food: IngredientFoodRefWriteDto? = null,
    val note: String? = null,
    @SerialName("originalText") val originalText: String? = null,
)

/** One preparation step. Mealie requires `text`; the order is the array order. */
@Serializable
data class RecipeStepWriteDto(val text: String)

/**
 * Food reference in a PATCH body: an already existing food, looked up by exact name
 * through `GET /api/foods`.
 *
 * Both keys are needed, verified against Mealie v3.28.0: [id] because the DB layer
 * resolves the relation by primary key (`auto_init.py`, MANYTOONE) — a name-only
 * reference raises `ValueError: Expected 'id' to be provided for food` (HTTP 500) —
 * and [name] because the request schema (`IngredientFood-Input`) declares it required,
 * so an id-only object never gets past validation (HTTP 422). [name] is always the
 * server's own name for that id.
 */
@Serializable
data class IngredientFoodRefWriteDto(val id: String, val name: String)

/**
 * Unit reference in a PATCH body: an already existing unit, looked up by exact name
 * through `GET /api/units`. See [IngredientFoodRefWriteDto] for why both keys are sent.
 */
@Serializable
data class IngredientUnitRefWriteDto(val id: String, val name: String)

/** Reference to a food as Mealie's *parser* reports it — read-only, never written. */
@Serializable
data class FoodRefDto(val name: String)

/** Reference to a unit as Mealie's *parser* reports it — read-only, never written. */
@Serializable
data class UnitRefDto(val name: String)

/**
 * Body for `POST /api/recipes/create/url`. Only `url`: Mealie defaults
 * `includeTags`/`includeCategories` to false. Answers with the new slug string.
 */
@Serializable
data class ImportRecipeUrlRequest(val url: String)

/**
 * Body for `POST /api/parser/ingredients`. Only `ingredients`: Mealie defaults the
 * parser to `nlp`, which is the parser V1.5 uses. Answers with a list of
 * [ParsedIngredientDto].
 */
@Serializable
data class ParseIngredientsRequest(val ingredients: List<String>)

/** Response of `PUT /api/recipes/{slug}/image`: the new image cache key. */
@Serializable
data class UpdateImageResponseDto(val image: String? = null)

/** One element of the list returned by `POST /api/parser/ingredients`. */
@Serializable
data class ParsedIngredientDto(
    val input: String? = null,
    val ingredient: ParsedIngredientDetailDto? = null,
)

/** The parsed ingredient, reduced to the fields Mealio keeps. */
@Serializable
data class ParsedIngredientDetailDto(
    val quantity: Double? = null,
    val unit: UnitRefDto? = null,
    val food: FoodRefDto? = null,
    val note: String? = null,
    val display: String? = null,
)
