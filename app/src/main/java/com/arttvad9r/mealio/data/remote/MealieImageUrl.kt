package com.arttvad9r.mealio.data.remote

/** Builds Mealie media URLs. Recipe image bytes are keyed by the recipe UUID. */
object MealieImageUrl {

    fun recipeImage(serverUrl: String, recipeUuid: String?, imageKey: String?): String? {
        if (recipeUuid.isNullOrBlank() || imageKey.isNullOrBlank()) return null
        val base = serverUrl.trimEnd('/')
        return "$base/api/media/recipes/$recipeUuid/images/original.webp"
    }
}
