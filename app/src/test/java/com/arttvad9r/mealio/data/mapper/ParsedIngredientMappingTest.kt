package com.arttvad9r.mealio.data.mapper

import com.arttvad9r.mealio.data.remote.MealieApiFactory
import com.arttvad9r.mealio.data.remote.dto.ParseIngredientsRequest
import com.arttvad9r.mealio.data.remote.dto.ParsedIngredientDto
import com.arttvad9r.mealio.domain.model.ParsedIngredient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser wire contract against the real Mealie v3.28.0 response: the DTOs must
 * decode the actual payload (which carries many more keys than Mealio keeps) and map
 * to the domain shape the create flow uses.
 */
class ParsedIngredientMappingTest {

    private val json = MealieApiFactory(MealieApiFactory.defaultOkHttp(), { null }).json

    /** Real `POST /api/parser/ingredients` element for "500 г куриного филе". */
    private val realResponse = """
        [
          {
            "confidence": {"average": 0.998313, "comment": 0.0, "food": 0.997127, "name": null, "quantity": 0.999, "unit": 0.998906},
            "ingredient": {
              "display": "500 г куриного филе",
              "food": {"aliases": [], "description": "", "extras": {}, "id": null, "labelId": null, "name": "куриного филе", "pluralName": null, "substitutions": []},
              "note": "",
              "originalText": "500 г куриного филе",
              "quantity": 500.0,
              "referenceId": "fac916b1-5f3f-4db5-a0e0-549468feca04",
              "title": null,
              "unit": {"abbreviation": "г", "fraction": true, "id": "0d5dd3f7-cc50-4d80-953f-cdc2ff0faa93", "name": "грамм", "pluralName": "граммы", "useAbbreviation": true}
            },
            "input": "500 г куриного филе"
          },
          {
            "confidence": {"average": 0.9, "quantity": 0.0, "unit": null, "food": 0.5, "comment": 0.0, "name": null},
            "ingredient": {
              "display": "соль по вкусу",
              "food": {"id": null, "name": "соль по вкусу"},
              "note": "",
              "originalText": "соль по вкусу",
              "quantity": 0.0,
              "referenceId": "706d4674-d9c5-4987-9da9-c71f0dad1f9a",
              "unit": null
            },
            "input": "соль по вкусу"
          }
        ]
    """.trimIndent()

    private fun decode(): List<ParsedIngredientDto> =
        json.decodeFromString(realResponse)

    @Test
    fun `request body carries only the ingredient lines`() {
        val el = json.parseToJsonElement(
            json.encodeToString(ParseIngredientsRequest(listOf("500 г куриного филе"))),
        ).jsonObject
        assertEquals(setOf("ingredients"), el.keys)
    }

    @Test
    fun `real parser response decodes and maps to the domain shape`() {
        val mapped = decode().map { it.toDomain() }

        assertEquals(2, mapped.size)

        val structured = mapped[0]
        assertEquals("500 г куриного филе", structured.input)
        assertEquals(500.0, structured.quantity!!, 0.0)
        assertEquals("грамм", structured.unitName)
        assertEquals("куриного филе", structured.foodName)
        assertEquals("", structured.note)
        assertEquals("500 г куриного филе", structured.display)
    }

    @Test
    fun `a null unit decodes as a null unit name`() {
        val freeText = decode().map { it.toDomain() }[1]

        assertEquals("соль по вкусу", freeText.foodName)
        assertNull(freeText.unitName)
        assertEquals(0.0, freeText.quantity!!, 0.0)
    }

    @Test
    fun `the decoded response is best-effort safe for the create flow`() {
        // The whole chain: real JSON -> DTO -> domain -> drafts.
        val parsed: List<ParsedIngredient> = decode().map { it.toDomain() }
        val drafts = com.arttvad9r.mealio.domain.recipe.prepareIngredientDrafts(
            lines = listOf("500 г куриного филе", "соль по вкусу"),
            parsed = parsed,
        )

        assertTrue(drafts[0].isStructured)
        assertFalse(drafts[1].isStructured)
    }
}
