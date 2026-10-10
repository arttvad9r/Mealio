package com.arttvad9r.mealio.data.remote.dto

import com.arttvad9r.mealio.data.mapper.toUpdateRequest
import com.arttvad9r.mealio.data.remote.MealieApiFactory
import com.arttvad9r.mealio.domain.model.IngredientRef
import com.arttvad9r.mealio.domain.model.RecipeDraft
import com.arttvad9r.mealio.domain.model.RecipeIngredientDraft
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Serialization of the V1.5 write DTOs, using the exact [MealieApiFactory] JSON
 * (`explicitNulls = false`, `encodeDefaults = false`). The point is that a PATCH
 * body carries ONLY the fields Mealio sets: anything null/default is absent, so
 * Mealie's `exclude_unset` update can never overwrite a field Mealio does not edit.
 */
class RecipeWriteDtoSerializationTest {

    private val json = MealieApiFactory(MealieApiFactory.defaultOkHttp(), { null }).json

    private inline fun <reified T> obj(value: T): JsonObject =
        json.parseToJsonElement(json.encodeToString(value)).jsonObject

    // Fields Mealie owns that a PATCH must never touch.
    private val forbidden = listOf(
        "nutrition", "recipeCategory", "tags", "settings", "orgURL", "orgUrl",
        "recipeYield", "recipeYieldQuantity", "assets", "notes", "extras",
        "comments", "tools", "totalTime", "prepTime", "cookTime", "performTime",
        "dateAdded", "dateUpdated", "userId", "householdId", "groupId", "image", "rating",
    )

    @Test
    fun `create recipe request serializes only name`() {
        val el = obj(CreateRecipeRequest("Борщ"))
        assertEquals(setOf("name"), el.keys)
        assertEquals("Борщ", el["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `update request omits every field that is not changed`() {
        val el = obj(RecipeUpdateRequest(name = "Новый"))
        assertEquals(setOf("name"), el.keys)
        forbidden.forEach { assertFalse("PATCH must not send $it", it in el) }
    }

    @Test
    fun `update request omits null description but keeps an explicitly emptied one`() {
        assertFalse("description" in obj(RecipeUpdateRequest(name = "X", description = null)))
        val cleared = obj(RecipeUpdateRequest(name = "X", description = ""))
        assertTrue("description" in cleared)
        assertEquals("", cleared["description"]!!.jsonPrimitive.content)
    }

    @Test
    fun `update request carries the full editable write shape`() {
        val el = obj(
            RecipeUpdateRequest(
                name = "Борщ",
                description = "Суп",
                recipeServings = 4.0,
                recipeIngredient = listOf(RecipeIngredientWriteDto(note = "500 г куриного филе")),
                recipeInstructions = listOf(RecipeStepWriteDto("Шаг 1"), RecipeStepWriteDto("Шаг 2")),
            ),
        )
        assertEquals(
            setOf("name", "description", "recipeServings", "recipeIngredient", "recipeInstructions"),
            el.keys,
        )
        assertEquals(4.0, el["recipeServings"]!!.jsonPrimitive.double, 0.0)

        val stepTexts = el["recipeInstructions"]!!.jsonArray.map { it.jsonObject["text"]!!.jsonPrimitive.content }
        assertEquals(listOf("Шаг 1", "Шаг 2"), stepTexts)
    }

    @Test
    fun `note-only ingredient serializes as just a note`() {
        val ingredient = obj(RecipeIngredientWriteDto(note = "2 яйца"))
        assertEquals(setOf("note"), ingredient.keys)
        assertEquals("2 яйца", ingredient["note"]!!.jsonPrimitive.content)
    }

    @Test
    fun `instruction order follows the list order`() {
        val el = obj(
            RecipeUpdateRequest(
                recipeInstructions = listOf(
                    RecipeStepWriteDto("Первый"),
                    RecipeStepWriteDto("Второй"),
                    RecipeStepWriteDto("Третий"),
                ),
            ),
        )
        val stepTexts = el["recipeInstructions"]!!.jsonArray.map { it.jsonObject["text"]!!.jsonPrimitive.content }
        assertEquals(listOf("Первый", "Второй", "Третий"), stepTexts)
    }

    @Test
    fun `url import request serializes only url`() {
        val el = obj(ImportRecipeUrlRequest("https://example.com/recipe"))
        assertEquals(setOf("url"), el.keys)
        assertEquals("https://example.com/recipe", el["url"]!!.jsonPrimitive.content)
        assertFalse("includeTags" in el)
        assertFalse("includeCategories" in el)
    }

    @Test
    fun `parser request serializes the ingredient lines`() {
        val el = obj(ParseIngredientsRequest(listOf("2 яйца", "соль по вкусу")))
        assertEquals(setOf("ingredients"), el.keys)
        val lines = el["ingredients"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("2 яйца", "соль по вкусу"), lines)
        assertFalse("parser" in el)
    }

    @Test
    fun `draft maps to update request keeping editable fields and dropping blanks`() {
        val upd = RecipeDraft(
            name = "  Борщ  ",
            description = "  Суп  ",
            servings = 4.0,
            ingredients = listOf(
                RecipeIngredientDraft.fallback("500 г куриного филе"),
                RecipeIngredientDraft.fallback("соль по вкусу"),
            ),
            instructions = listOf("  Шаг 1  ", ""),
        ).toUpdateRequest()

        val el = obj(upd)
        assertEquals("Борщ", el["name"]!!.jsonPrimitive.content)
        assertEquals("Суп", el["description"]!!.jsonPrimitive.content)

        val notes = el["recipeIngredient"]!!.jsonArray
            .map { it.jsonObject["note"]!!.jsonPrimitive.content }
        assertEquals(listOf("500 г куриного филе", "соль по вкусу"), notes)
        el["recipeIngredient"]!!.jsonArray.forEach {
            assertEquals(setOf("note"), it.jsonObject.keys)
        }

        val steps = el["recipeInstructions"]!!.jsonArray.map { it.jsonObject["text"]!!.jsonPrimitive.content }
        assertEquals(listOf("Шаг 1"), steps)
    }

    @Test
    fun `structured ingredient draft serializes quantity and the resolved reference ids`() {
        val el = obj(
            RecipeDraft(
                name = "Тест NLP",
                ingredients = listOf(
                    RecipeIngredientDraft(
                        originalText = "500 г куриного филе",
                        quantity = 500.0,
                        unitRef = IngredientRef("unit-1", "грамм"),
                        foodRef = IngredientRef("food-1", "куриное филе"),
                    ),
                ),
            ).toUpdateRequest(),
        )

        val ingredient = el["recipeIngredient"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("quantity", "unit", "food", "originalText"), ingredient.keys)
        assertEquals(500.0, ingredient["quantity"]!!.jsonPrimitive.double, 0.0)
        // The DB layer resolves these relations by id (name-only answers 500) while the
        // request schema requires name (id-only answers 422), so both keys are sent.
        assertEquals(setOf("id", "name"), ingredient["unit"]!!.jsonObject.keys)
        assertEquals("unit-1", ingredient["unit"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("грамм", ingredient["unit"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(setOf("id", "name"), ingredient["food"]!!.jsonObject.keys)
        assertEquals("food-1", ingredient["food"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("куриное филе", ingredient["food"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("500 г куриного филе", ingredient["originalText"]!!.jsonPrimitive.content)
        // Mealie builds `display` from the parts above, so it is never sent
        assertFalse("display must be built by Mealie", "display" in ingredient)
        forbidden.forEach { assertFalse("PATCH must not send $it", it in el) }
    }

    @Test
    fun `a name-only parser suggestion never reaches the patch body`() {
        // The first physical smoke test failed exactly here: a draft that still carries
        // the parser's names, but no id, must degrade to the note-only form.
        val el = obj(
            RecipeDraft(
                name = "Тест NLP",
                ingredients = listOf(
                    RecipeIngredientDraft(
                        originalText = "500 г куриного филе",
                        quantity = 500.0,
                        unitName = "грамм",
                        foodName = "куриное филе",
                    ),
                ),
            ).toUpdateRequest(),
        )

        val ingredient = el["recipeIngredient"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("note"), ingredient.keys)
        assertEquals("500 г куриного филе", ingredient["note"]!!.jsonPrimitive.content)
        assertFalse("unit/food must not be sent without an id", "unit" in ingredient)
        assertFalse("unit/food must not be sent without an id", "food" in ingredient)
    }

    @Test
    fun `mixed ingredients keep one structured line and one note-only line in order`() {
        val el = obj(
            RecipeDraft(
                name = "Тест NLP",
                ingredients = listOf(
                    RecipeIngredientDraft(
                        originalText = "2 яйца",
                        quantity = 2.0,
                        foodRef = IngredientRef("food-1", "яйцо"),
                    ),
                    RecipeIngredientDraft.fallback("соль по вкусу"),
                ),
            ).toUpdateRequest(),
        )

        val arr = el["recipeIngredient"]!!.jsonArray.map { it.jsonObject }
        assertEquals(2, arr.size)
        assertEquals(setOf("quantity", "food", "originalText"), arr[0].keys)
        assertEquals(setOf("note"), arr[1].keys)
        assertEquals("соль по вкусу", arr[1]["note"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a line whose relations did not resolve degrades to note-only`() {
        val el = obj(
            RecipeDraft(
                name = "X",
                ingredients = listOf(
                    RecipeIngredientDraft(
                        originalText = "500 г куриного филе",
                        quantity = 500.0,
                        unitName = "грамм",
                        foodName = "куриное филе",
                    ).withResolvedRefs(unitRef = null, foodRef = null),
                ),
            ).toUpdateRequest(),
        )

        val ingredient = el["recipeIngredient"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("note"), ingredient.keys)
        assertEquals("500 г куриного филе", ingredient["note"]!!.jsonPrimitive.content)
    }

    @Test
    fun `note-only baseline stores three free-text lines as three ingredients`() {
        val el = obj(
            RecipeUpdateRequest(
                recipeIngredient = listOf(
                    RecipeIngredientWriteDto(note = "500 г куриного филе"),
                    RecipeIngredientWriteDto(note = "2 яйца"),
                    RecipeIngredientWriteDto(note = "соль по вкусу"),
                ),
            ),
        )
        val arr = el["recipeIngredient"]!!.jsonArray
        assertEquals(3, arr.size)
        assertEquals(
            listOf("500 г куриного филе", "2 яйца", "соль по вкусу"),
            arr.map { it.jsonObject["note"]!!.jsonPrimitive.content },
        )
    }
}
