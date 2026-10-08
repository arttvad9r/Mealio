package com.arttvad9r.mealio.data.remote

import com.arttvad9r.mealio.data.remote.dto.AddRecipeToShoppingDto
import com.arttvad9r.mealio.data.remote.dto.AppAboutDto
import com.arttvad9r.mealio.data.remote.dto.PageDto
import com.arttvad9r.mealio.data.remote.dto.RecipeCategoryDto
import com.arttvad9r.mealio.data.remote.dto.RecipeDetailDto
import com.arttvad9r.mealio.data.remote.dto.RecipeSummaryDto
import com.arttvad9r.mealio.data.remote.dto.ShoppingItemDto
import com.arttvad9r.mealio.data.remote.dto.ShoppingItemUpdateDto
import com.arttvad9r.mealio.data.remote.dto.ShoppingListOutDto
import com.arttvad9r.mealio.data.remote.dto.ShoppingListSummaryDto
import com.arttvad9r.mealio.data.remote.dto.UserOutDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
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
