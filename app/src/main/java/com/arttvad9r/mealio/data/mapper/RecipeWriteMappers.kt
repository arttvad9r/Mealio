package com.arttvad9r.mealio.data.mapper

import com.arttvad9r.mealio.data.remote.dto.IngredientFoodRefWriteDto
import com.arttvad9r.mealio.data.remote.dto.IngredientUnitRefWriteDto
import com.arttvad9r.mealio.data.remote.dto.ParsedIngredientDto
import com.arttvad9r.mealio.data.remote.dto.RecipeIngredientWriteDto
import com.arttvad9r.mealio.data.remote.dto.RecipeStepWriteDto
import com.arttvad9r.mealio.data.remote.dto.RecipeUpdateRequest
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
 * A structured draft sends `quantity` + `unit` + `food` — references to an existing
 * Mealie entity, each as `{"id": ..., "name": ...}` (the id for the DB layer, the name
 * because the request schema requires it) — and the user's line as `originalText` (the
 * parser's own output shape); `display` is left out because Mealie builds it from
 * those parts. A draft without a resolved reference is written note-only — the manual
 * baseline — so the text is never lost, and the parser's own names are never written.
 */
fun RecipeIngredientDraft.toWriteDto(): RecipeIngredientWriteDto {
    val line = originalText.trim()
    if (!isStructured) {
        return RecipeIngredientWriteDto(note = note?.trim()?.takeIf(String::isNotEmpty) ?: line)
    }
    return RecipeIngredientWriteDto(
        quantity = quantity?.takeIf { it > 0.0 },
        unit = unitRef?.let { IngredientUnitRefWriteDto(it.id, it.name) },
        food = foodRef?.let { IngredientFoodRefWriteDto(it.id, it.name) },
        note = note?.trim()?.takeIf(String::isNotEmpty),
        originalText = line.takeIf(String::isNotEmpty),
    )
}

/** Maps a server-parsed ingredient to the domain model. */
/** Maps a server-parsed ingredient to the domain model. Its `id`s are never used: the
 * write path looks the references up itself (the parser answers names, and ids only
 * for entities it happened to recognize). */
fun ParsedIngredientDto.toDomain(): ParsedIngredient = ParsedIngredient(
    input = input,
    quantity = ingredient?.quantity,
    unitName = ingredient?.unit?.name,
    foodName = ingredient?.food?.name,
    note = ingredient?.note,
    display = ingredient?.display,
)
