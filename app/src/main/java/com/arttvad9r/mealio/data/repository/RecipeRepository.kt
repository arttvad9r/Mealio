package com.arttvad9r.mealio.data.repository

import com.arttvad9r.mealio.data.mapper.toDomain
import com.arttvad9r.mealio.data.remote.toMealioException
import com.arttvad9r.mealio.domain.model.RecipeDetail
import com.arttvad9r.mealio.domain.model.RecipeSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecipeRepository(private val connection: ConnectionRepository) {

    private fun api() = connection.api()

    suspend fun recipes(
        search: String? = null,
        categorySlug: String? = null,
        perPage: Int = 60,
    ): List<RecipeSummary> = withContext(Dispatchers.IO) {
        val api = api() ?: return@withContext emptyList()
        try {
            api.recipes(
                page = 1,
                perPage = perPage,
                search = search?.takeIf { it.isNotBlank() },
                categories = categorySlug?.let { listOf(it) },
                orderBy = "name",
                orderDirection = "asc",
            ).items.map { it.toDomain() }
        } catch (t: Throwable) {
            throw t.toMealioException()
        }
    }

    suspend fun categories(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val api = api() ?: return@withContext emptyList()
        try {
            api.categories().items.mapNotNull { dto ->
                val name = dto.name
                val slug = dto.slug
                if (name.isNullOrBlank() || slug.isNullOrBlank()) null else name to slug
            }
        } catch (t: Throwable) {
            throw t.toMealioException()
        }
    }

    suspend fun recipe(slug: String): RecipeDetail = withContext(Dispatchers.IO) {
        val api = api() ?: throw com.arttvad9r.mealio.data.remote.MealioException(
            com.arttvad9r.mealio.data.remote.ErrorKind.UNKNOWN,
        )
        try {
            api.recipe(slug).toDomain()
        } catch (t: Throwable) {
            throw t.toMealioException()
        }
    }
}
