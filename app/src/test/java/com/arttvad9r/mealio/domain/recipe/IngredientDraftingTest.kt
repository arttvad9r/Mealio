package com.arttvad9r.mealio.domain.recipe

import com.arttvad9r.mealio.domain.model.ParsedIngredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line → draft conversion: Mealie's parser result is used when it carries usable
 * structure, and every line that is not structured (or that the parser never saw)
 * falls back to a note-only draft keeping the user's text. Shapes mirror the real
 * Mealie v3.28.0 `POST /api/parser/ingredients` response.
 */
class IngredientDraftingTest {

    private fun parsed(
        input: String,
        quantity: Double? = null,
        unitName: String? = null,
        foodName: String? = null,
        note: String? = null,
        display: String? = null,
    ) = ParsedIngredient(input, quantity, unitName, foodName, note, display)

    @Test
    fun `structured parser result becomes a structured draft`() {
        val drafts = prepareIngredientDrafts(
            lines = listOf("500 г куриного филе"),
            parsed = listOf(
                parsed("500 г куриного филе", quantity = 500.0, unitName = "грамм", foodName = "куриное филе"),
            ),
        )

        val draft = drafts.single()
        assertTrue(draft.isStructured)
        assertEquals(500.0, draft.quantity!!, 0.0)
        assertEquals("грамм", draft.unitName)
        assertEquals("куриное филе", draft.foodName)
        assertNull(draft.note)
    }

    @Test
    fun `quantity keeps the parser's numeric type`() {
        val drafts = prepareIngredientDrafts(
            lines = listOf("2 яйца"),
            parsed = listOf(parsed("2 яйца", quantity = 2.0, foodName = "яйцо")),
        )

        assertEquals(2.0, drafts.single().quantity!!, 0.0)
    }

    @Test
    fun `unit is mapped by name`() {
        val drafts = prepareIngredientDrafts(
            lines = listOf("1 ст. л. оливкового масла"),
            parsed = listOf(parsed("1 ст. л. оливкового масла", quantity = 1.0, unitName = "литр", foodName = "оливковое масло")),
        )

        assertEquals("литр", drafts.single().unitName)
    }

    @Test
    fun `food is mapped by name`() {
        val drafts = prepareIngredientDrafts(
            lines = listOf("2 яйца"),
            parsed = listOf(parsed("2 яйца", quantity = 2.0, foodName = "яйцо")),
        )

        assertEquals("яйцо", drafts.single().foodName)
    }

    @Test
    fun `a parser note is kept and a blank one becomes null`() {
        val withNote = prepareIngredientDrafts(
            lines = listOf("соль по вкусу"),
            parsed = listOf(parsed("соль по вкусу", quantity = 1.0, foodName = "соль", note = "по вкусу")),
        ).single()
        assertEquals("по вкусу", withNote.note)

        val blankNote = prepareIngredientDrafts(
            lines = listOf("2 яйца"),
            parsed = listOf(parsed("2 яйца", quantity = 2.0, foodName = "яйцо", note = "   ")),
        ).single()
        assertNull("a blank note must not be written", blankNote.note)
    }

    @Test
    fun `originalText is always the user's line, structured or not`() {
        val structured = prepareIngredientDrafts(
            lines = listOf(" 500 г куриного филе "),
            parsed = listOf(parsed("500 г куриного филе", quantity = 500.0, unitName = "грамм", foodName = "куриное филе")),
        ).single()
        assertEquals(" 500 г куриного филе ", structured.originalText)

        val fallback = prepareIngredientDrafts(listOf("соль по вкусу"), emptyList()).single()
        assertEquals("соль по вкусу", fallback.originalText)
    }

    @Test
    fun `a useless parse falls back to the line as a note`() {
        val drafts = prepareIngredientDrafts(
            lines = listOf("соль по вкусу"),
            // Mealie echoes the whole line back as the food name for free text
            parsed = listOf(parsed("соль по вкусу", quantity = 0.0, foodName = "соль по вкусу")),
        )

        val draft = drafts.single()
        assertFalse(draft.isStructured)
        assertEquals("соль по вкусу", draft.note)
        assertEquals("соль по вкусу", draft.originalText)
        assertNull(draft.quantity)
        assertNull(draft.unitName)
        assertNull(draft.foodName)
    }

    @Test
    fun `no parser result at all falls back every line`() {
        val drafts = prepareIngredientDrafts(
            lines = listOf("500 г куриного филе", "2 яйца", "соль по вкусу"),
            parsed = null,
        )

        assertEquals(3, drafts.size)
        assertEquals(
            listOf("500 г куриного филе", "2 яйца", "соль по вкусу"),
            drafts.map { it.note },
        )
        assertEquals(0, drafts.count { it.isStructured })
    }

    @Test
    fun `mixed result keeps structured lines and falls back the rest`() {
        val lines = listOf("500 г куриного филе", "2 яйца", "соль по вкусу")
        val drafts = prepareIngredientDrafts(
            lines = lines,
            parsed = listOf(
                parsed(lines[0], quantity = 500.0, unitName = "грамм", foodName = "куриное филе"),
                parsed(lines[1], quantity = 2.0, foodName = "яйцо"),
                parsed(lines[2], quantity = 0.0, foodName = "соль по вкусу"),
            ),
        )

        assertEquals(3, drafts.size)
        assertTrue(drafts[0].isStructured)
        assertTrue(drafts[1].isStructured)
        assertFalse(drafts[2].isStructured)
        assertEquals("соль по вкусу", drafts[2].note)
    }

    @Test
    fun `ingredient order follows the line order`() {
        val lines = listOf("a", "b", "c")
        val drafts = prepareIngredientDrafts(
            lines = lines,
            parsed = listOf(parsed("a"), parsed("b", quantity = 2.0, foodName = "bee"), parsed("c")),
        )

        assertEquals(lines, drafts.map { it.originalText })
        assertFalse(drafts[0].isStructured)
        assertTrue(drafts[1].isStructured)
        assertFalse(drafts[2].isStructured)
    }

    @Test
    fun `a mismatched parser response is not mapped positionally`() {
        val drafts = prepareIngredientDrafts(
            lines = listOf("500 г куриного филе", "2 яйца"),
            parsed = listOf(parsed("500 г куриного филе", quantity = 500.0, foodName = "куриное филе")),
        )

        assertEquals(2, drafts.size)
        assertEquals(0, drafts.count { it.isStructured })
        assertEquals(listOf("500 г куриного филе", "2 яйца"), drafts.map { it.note })
    }

    @Test
    fun `no lines means no drafts`() {
        assertTrue(prepareIngredientDrafts(emptyList(), null).isEmpty())
    }
}
