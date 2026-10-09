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
 * One ingredient line. The V1.5 baseline writes free-text lines as Mealie
 * note-only ingredients — `{"note": "500 г куриного филе"}` — leaving [quantity],
 * [unit] and [food] null so they are omitted. The structured fields exist for the
 * server-side ingredient parsing that is part of V1.5 (not the manual baseline).
 */
@Serializable
data class RecipeIngredientWriteDto(
    val quantity: Double? = null,
    val unit: UnitRefDto? = null,
    val food: FoodRefDto? = null,
    val note: String? = null,
)

/** One preparation step. Mealie requires `text`; the order is the array order. */
@Serializable
data class RecipeStepWriteDto(val text: String)

/** Reference to a food by name (Mealie accepts `CreateIngredientFood(name=...)`). */
@Serializable
data class FoodRefDto(val name: String)

/** Reference to a unit by name (Mealie accepts `CreateIngredientUnit(name=...)`). */
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
