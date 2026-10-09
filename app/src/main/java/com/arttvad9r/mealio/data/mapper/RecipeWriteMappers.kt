package com.arttvad9r.mealio.data.mapper

import com.arttvad9r.mealio.data.remote.dto.ParsedIngredientDto
import com.arttvad9r.mealio.data.remote.dto.RecipeIngredientWriteDto
import com.arttvad9r.mealio.data.remote.dto.RecipeStepWriteDto
import com.arttvad9r.mealio.data.remote.dto.RecipeUpdateRequest
import com.arttvad9r.mealio.domain.model.ParsedIngredient
import com.arttvad9r.mealio.domain.model.RecipeDraft

/**
 * Maps the V1.5 write shape to Mealie's PATCH body. Only the fields Mealio edits
 * are set; blank ingredient/step lines are dropped and lines keep their order.
 * Ingredient lines become note-only ingredients — the manual baseline — so no
 * quantity/unit/food keys are emitted for them.
 */
fun RecipeDraft.toUpdateRequest(): RecipeUpdateRequest = RecipeUpdateRequest(
    name = name.trim(),
    description = description?.trim(),
    recipeServings = servings,
    recipeIngredient = ingredients
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { RecipeIngredientWriteDto(note = it) },
    recipeInstructions = instructions
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { RecipeStepWriteDto(text = it) },
)

/** Maps a server-parsed ingredient to the domain model. */
fun ParsedIngredientDto.toDomain(): ParsedIngredient = ParsedIngredient(
    input = input,
    quantity = ingredient?.quantity,
    unitName = ingredient?.unit?.name,
    foodName = ingredient?.food?.name,
    note = ingredient?.note,
    display = ingredient?.display,
)
