package com.arttvad9r.mealio.data.repository

import com.arttvad9r.mealio.data.mapper.toDomain
import com.arttvad9r.mealio.data.remote.dto.AddRecipeToShoppingDto
import com.arttvad9r.mealio.data.remote.dto.ShoppingItemUpdateDto
import com.arttvad9r.mealio.data.remote.toMealioException
import com.arttvad9r.mealio.domain.model.ShoppingListDetail
import com.arttvad9r.mealio.domain.model.ShoppingListSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShoppingRepository(private val connection: ConnectionRepository) {

    private fun api() = connection.api()

    suspend fun lists(): List<ShoppingListSummary> = withContext(Dispatchers.IO) {
        val api = api() ?: return@withContext emptyList()
        try {
            api.shoppingLists().items.map { it.toDomain() }
        } catch (t: Throwable) {
            throw t.toMealioException()
        }
    }

    suspend fun list(id: String): ShoppingListDetail = withContext(Dispatchers.IO) {
        val api = api() ?: return@withContext ShoppingListDetail(id, id, emptyList())
        try {
            api.shoppingList(id).toDomain()
        } catch (t: Throwable) {
            throw t.toMealioException()
        }
    }

    /** Toggles the checked state of an item, sending the full item back so no data is lost. */
    suspend fun setChecked(item: com.arttvad9r.mealio.domain.model.ShoppingItem, checked: Boolean) =
        withContext(Dispatchers.IO) {
            val api = api() ?: return@withContext
            try {
                api.updateShoppingItem(
                    item.id,
                    ShoppingItemUpdateDto(
                        shoppingListId = item.shoppingListId,
                        checked = checked,
                        quantity = item.quantity,
                        note = item.note,
                        display = item.display ?: "",
                        foodId = item.foodId,
                        unitId = item.unitId,
                    ),
                )
            } catch (t: Throwable) {
                throw t.toMealioException()
            }
        }

    /** Adds every ingredient of a recipe to the given shopping list (Mealie merges them). */
    suspend fun addRecipeToList(listId: String, recipeUuid: String) =
        withContext(Dispatchers.IO) {
            val api = api() ?: return@withContext
            try {
                api.addRecipeToShoppingList(
                    listId,
                    listOf(AddRecipeToShoppingDto(recipeId = recipeUuid)),
                )
            } catch (t: Throwable) {
                throw t.toMealioException()
            }
        }
}
