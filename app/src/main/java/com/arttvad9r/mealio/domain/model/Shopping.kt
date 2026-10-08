package com.arttvad9r.mealio.domain.model

data class ShoppingListSummary(
    val id: String,
    val name: String,
    val itemCount: Int?,
)

data class ShoppingItem(
    val id: String,
    val shoppingListId: String,
    val quantity: Double?,
    val unit: IngredientUnit?,
    val foodName: String?,
    val note: String?,
    val display: String?,
    val checked: Boolean,
    val foodId: String? = null,
    val unitId: String? = null,
    val position: Int? = null,
)

data class ShoppingListDetail(
    val id: String,
    val name: String,
    val items: List<ShoppingItem>,
)
