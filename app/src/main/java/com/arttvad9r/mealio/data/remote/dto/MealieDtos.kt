package com.arttvad9r.mealio.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Pagination envelope used by every Mealie list endpoint. */
@Serializable
data class PageDto<T>(
    val page: Int? = null,
    @SerialName("per_page") val perPage: Int? = null,
    val total: Int? = null,
    @SerialName("total_pages") val totalPages: Int? = null,
    val items: List<T> = emptyList(),
    val next: String? = null,
    val previous: String? = null,
)

@Serializable
data class AppAboutDto(
    val version: String? = null,
    val production: Boolean? = null,
    @SerialName("allowPasswordLogin") val allowPasswordLogin: Boolean? = null,
)

@Serializable
data class AuthTokenDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
)

@Serializable
data class UserOutDto(
    val id: String? = null,
    val username: String? = null,
    @SerialName("fullName") val fullName: String? = null,
    @SerialName("householdSlug") val householdSlug: String? = null,
    @SerialName("groupSlug") val groupSlug: String? = null,
    @SerialName("householdId") val householdId: String? = null,
)

@Serializable
data class RecipeCategoryDto(
    val id: String? = null,
    val name: String? = null,
    val slug: String? = null,
    @SerialName("recipeCount") val recipeCount: Int? = null,
)

@Serializable
data class RecipeTagDto(
    val id: String? = null,
    val name: String? = null,
    val slug: String? = null,
)

@Serializable
data class RecipeSummaryDto(
    val id: String? = null,
    val name: String? = null,
    val slug: String = "",
    val image: String? = null,
    @SerialName("recipeServings") val recipeServings: Double? = null,
    @SerialName("totalTime") val totalTime: String? = null,
    @SerialName("prepTime") val prepTime: String? = null,
    @SerialName("performTime") val performTime: String? = null,
    @SerialName("cookTime") val cookTime: String? = null,
    @SerialName("recipeCategory") val recipeCategory: List<RecipeCategoryDto>? = null,
    val tags: List<RecipeTagDto>? = null,
    val description: String? = null,
)

@Serializable
data class NutritionDto(
    val calories: String? = null,
    @SerialName("proteinContent") val proteinContent: String? = null,
    @SerialName("fatContent") val fatContent: String? = null,
    @SerialName("carbohydrateContent") val carbohydrateContent: String? = null,
    @SerialName("fiberContent") val fiberContent: String? = null,
    @SerialName("sugarContent") val sugarContent: String? = null,
    @SerialName("sodiumContent") val sodiumContent: String? = null,
)

@Serializable
data class IngredientUnitDto(
    val id: String? = null,
    val name: String? = null,
    @SerialName("pluralName") val pluralName: String? = null,
    val abbreviation: String? = null,
    @SerialName("pluralAbbreviation") val pluralAbbreviation: String? = null,
    @SerialName("useAbbreviation") val useAbbreviation: Boolean? = null,
    val fraction: Boolean? = null,
)

@Serializable
data class IngredientFoodDto(
    val id: String? = null,
    val name: String? = null,
    @SerialName("pluralName") val pluralName: String? = null,
)

@Serializable
data class RecipeIngredientDto(
    val quantity: Double? = null,
    val unit: IngredientUnitDto? = null,
    val food: IngredientFoodDto? = null,
    val note: String? = null,
    val title: String? = null,
    @SerialName("originalText") val originalText: String? = null,
    val display: String? = null,
)

@Serializable
data class RecipeStepDto(
    val id: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val text: String? = null,
)

@Serializable
data class RecipeDetailDto(
    val id: String? = null,
    val name: String? = null,
    val slug: String = "",
    val image: String? = null,
    @SerialName("recipeServings") val recipeServings: Double? = null,
    @SerialName("totalTime") val totalTime: String? = null,
    @SerialName("prepTime") val prepTime: String? = null,
    @SerialName("performTime") val performTime: String? = null,
    @SerialName("cookTime") val cookTime: String? = null,
    @SerialName("recipeCategory") val recipeCategory: List<RecipeCategoryDto>? = null,
    val tags: List<RecipeTagDto>? = null,
    val description: String? = null,
    @SerialName("recipeIngredient") val recipeIngredient: List<RecipeIngredientDto>? = null,
    @SerialName("recipeInstructions") val recipeInstructions: List<RecipeStepDto>? = null,
    val nutrition: NutritionDto? = null,
)

@Serializable
data class ShoppingListSummaryDto(
    val id: String,
    val name: String? = null,
    val extras: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)

@Serializable
data class ShoppingItemRecipeRefDto(
    val id: String? = null,
    @SerialName("recipeId") val recipeId: String? = null,
)

@Serializable
data class ShoppingItemDto(
    val id: String,
    @SerialName("shoppingListId") val shoppingListId: String,
    val quantity: Double? = null,
    val unit: IngredientUnitDto? = null,
    val food: IngredientFoodDto? = null,
    val note: String? = null,
    val display: String? = null,
    val checked: Boolean? = null,
    val position: Int? = null,
    @SerialName("foodId") val foodId: String? = null,
    @SerialName("unitId") val unitId: String? = null,
)

@Serializable
data class ShoppingListOutDto(
    val id: String,
    val name: String? = null,
    @SerialName("listItems") val listItems: List<ShoppingItemDto>? = null,
)

/** Payload for PUT /api/households/shopping/items/{id} (full replace). */
@Serializable
data class ShoppingItemUpdateDto(
    @SerialName("shoppingListId") val shoppingListId: String,
    val checked: Boolean,
    val quantity: Double? = null,
    val note: String? = null,
    val display: String = "",
    val position: Int? = null,
    @SerialName("foodId") val foodId: String? = null,
    @SerialName("unitId") val unitId: String? = null,
)

/** Element of the JSON array body for POST .../lists/{id}/recipe. */
@Serializable
data class AddRecipeToShoppingDto(
    @SerialName("recipeId") val recipeId: String,
    @SerialName("recipeIncrementQuantity") val recipeIncrementQuantity: Double = 1.0,
)
