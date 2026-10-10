package com.arttvad9r.mealio.data.mapper

import com.arttvad9r.mealio.data.remote.dto.FoodRefDto
import com.arttvad9r.mealio.data.remote.dto.ParsedIngredientDto
import com.arttvad9r.mealio.data.remote.dto.RecipeIngredientWriteDto
import com.arttvad9r.mealio.data.remote.dto.RecipeStepWriteDto
import com.arttvad9r.mealio.data.remote.dto.RecipeUpdateRequest
import com.arttvad9r.mealio.data.remote.dto.UnitRefDto
import com.arttvad9r.mealio.domain.model.ParsedIngredient
import com.arttvad9r.mealio.domain.model.RecipeDraft
import com.arttvad9r.mealio.domain.model.RecipeIngredientDraft

/**
 * Maps the V1.5 write shape to Mealie's PATCH body. Only the fields Mealio edits
 * are set; blank step lines are dropped and lines keep their order.
 */
fun RecipeDraft.toUpdateRequest(): RecipeUpdateRequest = RecipeUpdateRequest(
    name = name.trim(),
    description = description?.trim(),
    recipeServings = servings,
    recipeIngredient = ingredients.map { it.toWriteDto() },
    recipeInstructions = instructions
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { RecipeStepWriteDto(text = it) },
)

/**
 * One ingredient draft as Mealie's `RecipeIngredient` write body.
 *
 * A structured draft sends `quantity` + `unit` + `food` and the user's line as
 * `originalText` (the parser's own output shape); `display` is left out because
 * Mealie builds it from those parts. A draft without usable structure is written
 * note-only — the manual baseline — so the text is never lost. A structured draft
 * that ends up with no quantity/unit/food after normalisation degrades to the same
 * note-only form rather than writing an empty ingredient.
 */
fun RecipeIngredientDraft.toWriteDto(): RecipeIngredientWriteDto {
    val quantity = quantity?.takeIf { it > 0.0 }
    val unit = unitName?.trim()?.takeIf(String::isNotEmpty)?.let(::UnitRefDto)
    val food = foodName?.trim()?.takeIf(String::isNotEmpty)?.let(::FoodRefDto)
    val line = originalText.trim()
    if (quantity == null && unit == null && food == null) {
        return RecipeIngredientWriteDto(note = note?.trim()?.takeIf(String::isNotEmpty) ?: line)
    }
    return RecipeIngredientWriteDto(
        quantity = quantity,
        unit = unit,
        food = food,
        note = note?.trim()?.takeIf(String::isNotEmpty),
        originalText = line.takeIf(String::isNotEmpty),
    )
}

/** Maps a server-parsed ingredient to the domain model. */
fun ParsedIngredientDto.toDomain(): ParsedIngredient = ParsedIngredient(
    input = input,
    quantity = ingredient?.quantity,
    unitName = ingredient?.unit?.name,
    foodName = ingredient?.food?.name,
    note = ingredient?.note,
    display = ingredient?.display,
)
