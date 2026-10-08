package com.arttvad9r.mealio.data.mapper

import com.arttvad9r.mealio.data.remote.dto.IngredientFoodDto
import com.arttvad9r.mealio.data.remote.dto.IngredientUnitDto
import com.arttvad9r.mealio.data.remote.dto.NutritionDto
import com.arttvad9r.mealio.data.remote.dto.RecipeDetailDto
import com.arttvad9r.mealio.data.remote.dto.RecipeIngredientDto
import com.arttvad9r.mealio.data.remote.dto.RecipeStepDto
import com.arttvad9r.mealio.data.remote.dto.RecipeSummaryDto
import com.arttvad9r.mealio.data.remote.dto.ShoppingItemDto
import com.arttvad9r.mealio.data.remote.dto.ShoppingListOutDto
import com.arttvad9r.mealio.data.remote.dto.ShoppingListSummaryDto
import com.arttvad9r.mealio.domain.model.IngredientLine
import com.arttvad9r.mealio.domain.model.IngredientUnit
import com.arttvad9r.mealio.domain.model.Nutrition
import com.arttvad9r.mealio.domain.model.RecipeDetail
import com.arttvad9r.mealio.domain.model.RecipeSummary
import com.arttvad9r.mealio.domain.model.ShoppingItem
import com.arttvad9r.mealio.domain.model.ShoppingListDetail
import com.arttvad9r.mealio.domain.model.ShoppingListSummary

/** All DTO -> domain conversion lives here, so Mealie quirks stay in the data layer. */

fun IngredientUnitDto?.toDomain(): IngredientUnit? {
    val name = this?.name?.takeIf { it.isNotBlank() } ?: return null
    return IngredientUnit(
        name = name,
        pluralName = pluralName?.takeIf { it.isNotBlank() },
        abbreviation = abbreviation?.takeIf { it.isNotBlank() },
        pluralAbbreviation = pluralAbbreviation?.takeIf { it.isNotBlank() },
        useAbbreviation = useAbbreviation == true,
        fraction = fraction != false,
    )
}

fun NutritionDto?.toDomain(): Nutrition = Nutrition(
    calories = this?.calories?.takeIf { it.isNotBlank() },
    protein = this?.proteinContent?.takeIf { it.isNotBlank() },
    fat = this?.fatContent?.takeIf { it.isNotBlank() },
    carbs = this?.carbohydrateContent?.takeIf { it.isNotBlank() },
)

fun RecipeSummaryDto.toDomain(): RecipeSummary = RecipeSummary(
    slug = slug,
    uuid = id,
    name = name?.takeIf { it.isNotBlank() } ?: slug,
    imageKey = image?.takeIf { it.isNotBlank() },
    categories = recipeCategory.orEmpty().mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } },
    tags = tags.orEmpty().mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } },
    // Nutrition is only returned by the detail endpoint; list items carry none.
    calories = null,
    protein = null,
    fat = null,
    carbs = null,
    totalTimeIso = totalTime ?: performTime ?: cookTime,
    servings = recipeServings?.takeIf { it > 0 },
)

private fun RecipeIngredientDto.toDomain(): IngredientLine = IngredientLine(
    quantity = quantity?.takeIf { it > 0.0 },
    unit = unit.toDomain(),
    foodName = food?.name?.takeIf { it.isNotBlank() }
        ?: food?.let { foodFallback(it) },
    note = note?.takeIf { it.isNotBlank() },
    title = title?.takeIf { it.isNotBlank() },
    originalText = originalText?.takeIf { it.isNotBlank() },
    display = display?.takeIf { it.isNotBlank() },
)

private fun foodFallback(food: IngredientFoodDto): String? =
    food.pluralName?.takeIf { it.isNotBlank() }

private fun RecipeStepDto.toText(): String? =
    text?.takeIf { it.isNotBlank() }
        ?: summary?.takeIf { it.isNotBlank() }
        ?: title?.takeIf { it.isNotBlank() }

fun RecipeDetailDto.toDomain(): RecipeDetail = RecipeDetail(
    slug = slug,
    uuid = id,
    name = name?.takeIf { it.isNotBlank() } ?: slug,
    imageKey = image?.takeIf { it.isNotBlank() },
    categories = recipeCategory.orEmpty().mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } },
    tags = tags.orEmpty().mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } },
    servings = recipeServings?.takeIf { it > 0 },
    nutrition = nutrition.toDomain(),
    ingredients = recipeIngredient.orEmpty().map { it.toDomain() },
    instructions = recipeInstructions.orEmpty().mapNotNull { it.toText() },
    totalTimeIso = totalTime ?: performTime ?: cookTime,
    prepTimeIso = prepTime,
    description = description?.takeIf { it.isNotBlank() },
)

fun ShoppingListSummaryDto.toDomain(): ShoppingListSummary = ShoppingListSummary(
    id = id,
    name = name?.takeIf { it.isNotBlank() } ?: id,
    itemCount = null,
)

fun ShoppingItemDto.toDomain(): ShoppingItem = ShoppingItem(
    id = id,
    shoppingListId = shoppingListId,
    quantity = quantity?.takeIf { it > 0.0 },
    unit = unit.toDomain(),
    foodName = food?.name?.takeIf { it.isNotBlank() },
    note = note?.takeIf { it.isNotBlank() },
    display = display?.takeIf { it.isNotBlank() },
    checked = checked == true,
    foodId = foodId,
    unitId = unitId,
    position = position,
)

fun ShoppingListOutDto.toDomain(): ShoppingListDetail = ShoppingListDetail(
    id = id,
    name = name?.takeIf { it.isNotBlank() } ?: id,
    items = listItems.orEmpty().map { it.toDomain() },
)
