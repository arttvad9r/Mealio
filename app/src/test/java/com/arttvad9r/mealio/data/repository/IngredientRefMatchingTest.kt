package com.arttvad9r.mealio.data.repository

import com.arttvad9r.mealio.domain.model.IngredientRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rule that turns a parser-suggested name into the id of an entity that already
 * exists on the server. Mealie's ingredient search is loose (it also answers near
 * matches), so an id may only be taken from an *exact* name match — anything else makes
 * the caller write the line note-only instead of referencing the wrong food.
 */
class IngredientRefMatchingTest {

    @Test
    fun `an exact name match answers the entity`() {
        val refs = listOf(NamedRef("id-flour", "мука"), NamedRef("id-egg", "яйцо"))

        // The server's own name travels with the id: a write must send both.
        assertEquals(IngredientRef("id-egg", "яйцо"), exactRef("яйцо", refs))
    }

    @Test
    fun `case and repeated whitespace do not matter`() {
        val refs = listOf(NamedRef("id-1", "  Куриное   ФИЛЕ "))

        assertEquals(IngredientRef("id-1", "  Куриное   ФИЛЕ "), exactRef(" куриное филе", refs))
    }

    @Test
    fun `a yofolded name matches its plain spelling`() {
        val refs = listOf(NamedRef("id-1", "свекла"))

        assertEquals("id-1", exactRef("свёкла", refs)!!.id)
    }

    @Test
    fun `a near match is not accepted`() {
        // What the live search really answers for "куриного филе": a different food.
        val refs = listOf(NamedRef("id-pork", "свиное филе"), NamedRef("id-chicken", "куриное филе"))

        assertNull(exactRef("куриного филе", refs))
    }

    @Test
    fun `an empty answer and an empty name both miss`() {
        assertNull(exactRef("грамм", emptyList()))
        assertNull(exactRef("   ", listOf(NamedRef("id-1", ""))))
    }

    @Test
    fun `a nameless or blank-id entry is never used`() {
        val refs = listOf(NamedRef("id-null-name", null), NamedRef("  ", "яйцо"))

        assertNull(exactRef("яйцо", refs))
    }

    @Test
    fun `the first exact match wins`() {
        val refs = listOf(NamedRef("id-first", "мука"), NamedRef("id-second", "мука"))

        assertEquals("id-first", exactRef("мука", refs)!!.id)
    }
}
