package com.arttvad9r.mealio.data.remote

import com.arttvad9r.mealio.data.remote.dto.AddRecipeToShoppingDto
import com.arttvad9r.mealio.data.remote.dto.AppAboutDto
import com.arttvad9r.mealio.data.remote.dto.CreateRecipeRequest
import com.arttvad9r.mealio.data.remote.dto.ImportRecipeUrlRequest
import com.arttvad9r.mealio.data.remote.dto.PageDto
import com.arttvad9r.mealio.data.remote.dto.ParseIngredientsRequest
import com.arttvad9r.mealio.data.remote.dto.ParsedIngredientDto
import com.arttvad9r.mealio.data.remote.dto.RecipeCategoryDto
import com.arttvad9r.mealio.data.remote.dto.RecipeDetailDto
import com.arttvad9r.mealio.data.remote.dto.RecipeSummaryDto
import com.arttvad9r.mealio.data.remote.dto.RecipeUpdateRequest
import com.arttvad9r.mealio.data.remote.dto.ShoppingItemDto
import com.arttvad9r.mealio.data.remote.dto.ShoppingItemUpdateDto
import com.arttvad9r.mealio.data.remote.dto.ShoppingListOutDto
import com.arttvad9r.mealio.data.remote.dto.ShoppingListSummaryDto
import com.arttvad9r.mealio.data.remote.dto.UpdateImageResponseDto
import com.arttvad9r.mealio.data.remote.dto.UserOutDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface MealieApi {

    @GET("api/app/about")
    suspend fun about(): AppAboutDto

    @GET("api/users/self")
    suspend fun self(): UserOutDto

    @GET("api/recipes")
    suspend fun recipes(
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 50,
        @Query("search") search: String? = null,
        @Query("categories") categories: List<String>? = null,
        @Query("orderBy") orderBy: String? = null,
        @Query("orderDirection") orderDirection: String? = null,
    ): PageDto<RecipeSummaryDto>

    @GET("api/recipes/{slug}")
    suspend fun recipe(@Path("slug") slug: String): RecipeDetailDto

    // --- Recipe write (V1.5). Mealie quirks live in the DTOs / repository. ---

    /** Creates an empty recipe from its name. Answers with the new slug string. */
    @POST("api/recipes")
    suspend fun createRecipe(@Body body: CreateRecipeRequest): String

    /** Partial update. The body carries only the keys present (Mealie exclude_unset). */
    @PATCH("api/recipes/{slug}")
    suspend fun updateRecipe(
        @Path("slug") slug: String,
        @Body body: RecipeUpdateRequest,
    ): RecipeDetailDto

    /** Deletes a recipe by slug. Mealie answers with the deleted Recipe. */
    @DELETE("api/recipes/{slug}")
    suspend fun deleteRecipe(@Path("slug") slug: String): RecipeDetailDto

    /**
     * Uploads or replaces a recipe image. Mealie wants multipart/form-data with the
     * file part `image` and a plain form field `extension` (e.g. "jpg"). Answers
     * with the new image cache key.
     */
    @Multipart
    @PUT("api/recipes/{slug}/image")
    suspend fun uploadRecipeImage(
        @Path("slug") slug: String,
        @Part image: MultipartBody.Part,
        @Part("extension") extension: RequestBody,
    ): UpdateImageResponseDto

    /** Deletes a recipe image. Mealie answers with a success object we ignore. */
    @DELETE("api/recipes/{slug}/image")
    suspend fun deleteRecipeImage(@Path("slug") slug: String)

    /** Imports a recipe from a URL (scrape). Answers with the new slug string. */
    @POST("api/recipes/create/url")
    suspend fun importRecipeFromUrl(@Body body: ImportRecipeUrlRequest): String

    /** Parses free-text ingredient lines server-side (Mealie's default parser: nlp). */
    @POST("api/parser/ingredients")
    suspend fun parseIngredients(@Body body: ParseIngredientsRequest): List<ParsedIngredientDto>

    @GET("api/organizers/categories")
    suspend fun categories(
        @Query("perPage") perPage: Int = -1,
    ): PageDto<RecipeCategoryDto>

    @GET("api/households/shopping/lists")
    suspend fun shoppingLists(
        @Query("perPage") perPage: Int = -1,
    ): PageDto<ShoppingListSummaryDto>

    @GET("api/households/shopping/lists/{id}")
    suspend fun shoppingList(@Path("id") id: String): ShoppingListOutDto

    @PUT("api/households/shopping/items/{id}")
    suspend fun updateShoppingItem(
        @Path("id") id: String,
        @Body body: ShoppingItemUpdateDto,
    ): ShoppingItemDto

    @POST("api/households/shopping/lists/{id}/recipe")
    suspend fun addRecipeToShoppingList(
        @Path("id") listId: String,
        @Body body: List<AddRecipeToShoppingDto>,
    ): ShoppingListOutDto
}
