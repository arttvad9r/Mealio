package com.arttvad9r.mealio.data.repository

import com.arttvad9r.mealio.data.mapper.toDomain
import com.arttvad9r.mealio.data.mapper.toUpdateRequest
import com.arttvad9r.mealio.data.remote.ErrorKind
import com.arttvad9r.mealio.data.remote.MealieApi
import com.arttvad9r.mealio.data.remote.MealioException
import com.arttvad9r.mealio.data.remote.dto.CreateRecipeRequest
import com.arttvad9r.mealio.data.remote.dto.ImportRecipeUrlRequest
import com.arttvad9r.mealio.data.remote.dto.ParseIngredientsRequest
import com.arttvad9r.mealio.data.remote.toMealioException
import com.arttvad9r.mealio.domain.model.IngredientRef
import com.arttvad9r.mealio.domain.model.ParsedIngredient
import com.arttvad9r.mealio.domain.model.RecipeDetail
import com.arttvad9r.mealio.domain.model.RecipeDraft
import com.arttvad9r.mealio.domain.model.RecipeSummary
import com.arttvad9r.mealio.domain.recipe.normalizeIngredientText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The recipe reads the UI needs. [RecipeRepository] is the real implementation;
 * tests can substitute a lightweight fake without building the network stack.
 */
interface RecipeSource {
    suspend fun recipes(search: String? = null, categorySlug: String? = null): List<RecipeSummary>
    suspend fun recipe(slug: String): RecipeDetail
}

/**
 * The recipe writes the manual-create UI needs (V1.5). [RecipeRepository] is the
 * real implementation; the create ViewModel takes this interface so its state
 * machine can be unit-tested with a lightweight fake and no network stack.
 */
interface RecipeWriteSource {
    suspend fun createRecipe(name: String): String
    suspend fun updateRecipe(slug: String, draft: RecipeDraft): RecipeDetail

    /**
     * Best-effort server-side ingredient parsing. The caller treats any failure as
     * "no structure available" and keeps the user's lines as note-only ingredients.
     */
    suspend fun parseIngredients(lines: List<String>): List<ParsedIngredient>

    /**
     * The existing Mealie unit whose name equals [name] exactly, or null.
     *
     * The parser reports unit/food as plain names, but Mealie's PATCH resolves those
     * relations by `id` while its request schema still requires `name`, so the name has
     * to be exchanged for an entity that already exists. Best effort by contract: a
     * lookup failure or a miss answers null, which makes the caller write that
     * ingredient note-only instead of failing the save.
     */
    suspend fun findUnitRef(name: String): IngredientRef?

    /** The existing Mealie food whose name equals [name] exactly, or null. */
    suspend fun findFoodRef(name: String): IngredientRef?
}

class RecipeRepository(private val connection: ConnectionRepository) :
    RecipeSource, RecipeWriteSource {

    private fun api() = connection.api()

    /**
     * Fetches every recipe matching the search/category filter. Mealie treats
     * `perPage = -1` as "no limit" (repository_generic.py: per_page == -1 ->
     * limit = None), so one request returns all recipes without manual paging.
     */
    override suspend fun recipes(
        search: String?,
        categorySlug: String?,
    ): List<RecipeSummary> = withContext(Dispatchers.IO) {
        val api = api() ?: return@withContext emptyList()
        try {
            api.recipes(
                page = 1,
                perPage = -1,
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

    override suspend fun recipe(slug: String): RecipeDetail = withContext(Dispatchers.IO) {
        val api = api() ?: throw com.arttvad9r.mealio.data.remote.MealioException(
            com.arttvad9r.mealio.data.remote.ErrorKind.UNKNOWN,
        )
        try {
            api.recipe(slug).toDomain()
        } catch (t: Throwable) {
            throw t.toMealioException()
        }
    }

    // --- Write operations (V1.5). Same IO dispatcher and MealieException mapping
    // --- as the reads above; no second error system. ---

    /**
     * Creates a recipe and returns its slug. Mealie's POST accepts only a name, so
     * the content of a new recipe is applied by a following [updateRecipe].
     */
    override suspend fun createRecipe(name: String): String = withContext(Dispatchers.IO) {
        val api = apiOrThrow()
        try {
            api.createRecipe(CreateRecipeRequest(name.trim()))
        } catch (t: Throwable) {
            throw t.toMealioException()
        }
    }

    /**
     * Applies the V1.5 editable fields to an existing recipe and returns the updated
     * detail. The PATCH body contains only those fields, so nothing Mealio does not
     * edit (nutrition, categories, tags, settings, ...) is touched.
     */
    override suspend fun updateRecipe(slug: String, draft: RecipeDraft): RecipeDetail =
        withContext(Dispatchers.IO) {
            val api = apiOrThrow()
            try {
                api.updateRecipe(slug, draft.toUpdateRequest()).toDomain()
            } catch (t: Throwable) {
                throw t.toMealioException()
            }
        }

    suspend fun deleteRecipe(slug: String): Unit = withContext(Dispatchers.IO) {
        val api = apiOrThrow()
        try {
            api.deleteRecipe(slug)
        } catch (t: Throwable) {
            throw t.toMealioException()
        }
    }

    /**
     * Uploads or replaces a recipe image. Mealie wants multipart/form-data with the
     * file part `image` and a plain form field `extension` (e.g. "jpg"); [bytes] is
     * sent under the part filename `image.<extension>`. Returns the new image cache
     * key, or null if Mealie did not report one.
     */
    suspend fun uploadRecipeImage(
        slug: String,
        bytes: ByteArray,
        extension: String,
    ): String? = withContext(Dispatchers.IO) {
        val api = apiOrThrow()
        val ext = extension.trim().removePrefix(".")
        try {
            val (imagePart, extensionPart) = recipeImageParts(bytes, ext)
            api.uploadRecipeImage(slug, imagePart, extensionPart).image
        } catch (t: Throwable) {
            throw t.toMealioException()
        }
    }

    suspend fun deleteRecipeImage(slug: String): Unit = withContext(Dispatchers.IO) {
        val api = apiOrThrow()
        try {
            api.deleteRecipeImage(slug)
        } catch (t: Throwable) {
            throw t.toMealioException()
        }
    }

    /** Imports a recipe by URL (server-side scrape) and returns the new slug. */
    suspend fun importRecipeFromUrl(url: String): String = withContext(Dispatchers.IO) {
        val api = apiOrThrow()
        try {
            api.importRecipeFromUrl(ImportRecipeUrlRequest(url.trim()))
        } catch (t: Throwable) {
            throw t.toMealioException()
        }
    }

    /** Parses free-text ingredient lines server-side. */
    override suspend fun parseIngredients(lines: List<String>): List<ParsedIngredient> =
        withContext(Dispatchers.IO) {
            val api = apiOrThrow()
            try {
                api.parseIngredients(ParseIngredientsRequest(lines)).map { it.toDomain() }
            } catch (t: Throwable) {
                throw t.toMealioException()
            }
        }

    override suspend fun findUnitRef(name: String): IngredientRef? = withContext(Dispatchers.IO) {
        val api = api() ?: return@withContext null
        lookupRef(name) { api.units(it).items.map { unit -> NamedRef(unit.id, unit.name) } }
    }

    override suspend fun findFoodRef(name: String): IngredientRef? = withContext(Dispatchers.IO) {
        val api = api() ?: return@withContext null
        lookupRef(name) { api.foods(it).items.map { food -> NamedRef(food.id, food.name) } }
    }

    /**
     * Runs a unit/food search and keeps an exact name match. Mealie's search is loose
     * ("куриного филе" also answers "свиное филе", "соль по вкусу" answers 30 unrelated
     * foods), so the answer is filtered here rather than trusted. Any failure is
     * swallowed: the caller falls back to the note-only form, which is always safe.
     */
    private suspend fun lookupRef(
        name: String,
        search: suspend (String) -> List<NamedRef>,
    ): IngredientRef? = try {
        exactRef(name, search(name))
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        null
    }

    private fun apiOrThrow(): MealieApi =
        api() ?: throw MealioException(ErrorKind.UNKNOWN, "not connected")
}

/** A unit/food on the server, reduced to what resolving a parser name needs. */
internal data class NamedRef(val id: String?, val name: String?)

/**
 * Normalized key for comparing a parser name with a Mealie entity name: trimmed,
 * case-folded and with runs of whitespace collapsed, plus `ё` folded to `е` (Mealie
 * normalizes names the same way before storing them). The domain owns the rule, because
 * the unit sanity layer compares against the user's own line with it too.
 */
internal fun refNameKey(value: String?): String = normalizeIngredientText(value)

/**
 * The existing entity whose name matches [name] exactly, or null when nothing does. The
 * returned reference carries the *server's* name, which is what a write has to send
 * along with the id. Top-level [internal] so the matching rule can be asserted in tests.
 */
internal fun exactRef(name: String, refs: List<NamedRef>): IngredientRef? {
    val key = refNameKey(name)
    if (key.isEmpty()) return null
    return refs
        .firstOrNull { refNameKey(it.name) == key && !it.id.isNullOrBlank() }
        ?.let { IngredientRef(id = it.id!!, name = it.name.orEmpty()) }
}

/**
 * Builds the multipart parts Mealie's `PUT /api/recipes/{slug}/image` expects: the
 * file part `image` (Mealie reads it as raw bytes) and the plain form field
 * `extension` (Mealie uses it to name `original.<extension>`). The part MIME type
 * is cosmetic — Mealie keys the stored file off `extension`, not the Content-Type.
 * Top-level [internal] so the wire contract can be asserted in tests.
 */
internal fun recipeImageParts(
    bytes: ByteArray,
    extension: String,
): Pair<MultipartBody.Part, RequestBody> {
    val ext = extension.trim().removePrefix(".")
    val imagePart = MultipartBody.Part.createFormData(
        name = "image",
        filename = "image.$ext",
        body = bytes.toRequestBody(imageMediaType(ext)),
    )
    val extensionPart = ext.toRequestBody("text/plain".toMediaType())
    return imagePart to extensionPart
}

private fun imageMediaType(extension: String): okhttp3.MediaType = when (extension.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg".toMediaType()
    "png" -> "image/png".toMediaType()
    "webp" -> "image/webp".toMediaType()
    "gif" -> "image/gif".toMediaType()
    "bmp" -> "image/bmp".toMediaType()
    "avif" -> "image/avif".toMediaType()
    else -> "application/octet-stream".toMediaType()
}
