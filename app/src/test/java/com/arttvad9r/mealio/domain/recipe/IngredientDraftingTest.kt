package com.arttvad9r.mealio.domain.recipe

import com.arttvad9r.mealio.domain.model.IngredientRef
import com.arttvad9r.mealio.domain.model.ParsedIngredient
import kotlinx.coroutines.test.runTest
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
 *
 * The drafts produced here are *unresolved*: they carry the parser's unit/food names,
 * not ids. [applyResolvedRefs] turns them into a writable structured ingredient — but
 * only when *every* relation the parser reported resolves; a partial reading degrades
 * the whole line to the note-only fallback.
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
    fun `structured parser result is kept as names for the reference lookup`() {
        val drafts = prepareIngredientDrafts(
            lines = listOf("500 г куриного филе"),
            parsed = listOf(
                parsed("500 г куриного филе", quantity = 500.0, unitName = "грамм", foodName = "куриное филе"),
            ),
        )

        val draft = drafts.single()
        assertTrue(draft.needsRefResolution)
        assertFalse("nothing is structured before the ids are looked up", draft.isStructured)
        assertEquals(500.0, draft.quantity!!, 0.0)
        assertEquals("грамм", draft.unitName)
        assertEquals("куриное филе", draft.foodName)
        assertNull(draft.note)
    }

    @Test
    fun `resolved ids make the line structured and drop the parser names`() {
        val draft = prepareIngredientDrafts(
            lines = listOf("500 г куриного филе"),
            parsed = listOf(
                parsed("500 г куриного филе", quantity = 500.0, unitName = "грамм", foodName = "куриное филе"),
            ),
        ).single().withResolvedRefs(
            unitRef = IngredientRef("unit-1", "грамм"),
            foodRef = IngredientRef("food-1", "куриное филе"),
        )

        assertTrue(draft.isStructured)
        assertEquals("unit-1", draft.unitRef!!.id)
        assertEquals("грамм", draft.unitRef!!.name)
        assertEquals("food-1", draft.foodRef!!.id)
        assertNull(draft.unitName)
        assertNull(draft.foodName)
        assertEquals("500 г куриного филе", draft.originalText)
    }

    @Test
    fun `a relation the parser never reported does not have to resolve`() = runTest {
        // The real shape of "2 яйца": quantity + food, no unit at all.
        val draft = draftOf(
            line = "2 яйца",
            result = parsed("2 яйца", quantity = 2.0, foodName = "яйцо"),
            foods = mapOf("яйцо" to "food-egg"),
        )

        assertTrue(draft.isStructured)
        assertEquals(2.0, draft.quantity!!, 0.0)
        assertNull(draft.unitRef)
        assertEquals("food-egg", draft.foodRef!!.id)
        assertNull(draft.unitName)
        assertNull(draft.foodName)
    }

    @Test
    fun `an unresolvable relation keeps the whole line as a note`() {
        val draft = prepareIngredientDrafts(
            lines = listOf("500 г куриного филе"),
            parsed = listOf(
                parsed("500 г куриного филе", quantity = 500.0, unitName = "грамм", foodName = "куриное филе"),
            ),
        ).single().withResolvedRefs(unitRef = null, foodRef = null)

        assertFalse(draft.isStructured)
        assertEquals("500 г куриного филе", draft.note)
        assertEquals("500 г куриного филе", draft.originalText)
        assertNull(draft.quantity)
        assertNull(draft.unitName)
        assertNull(draft.foodName)
        assertNull(draft.unitRef)
        assertNull(draft.foodRef)
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
            lines = listOf("200 мл молока"),
            parsed = listOf(parsed("200 мл молока", quantity = 200.0, unitName = "миллилитр", foodName = "молоко")),
        )

        assertEquals("миллилитр", drafts.single().unitName)
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
        assertEquals(0, drafts.count { it.needsRefResolution })
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
        assertTrue(drafts[0].needsRefResolution)
        assertTrue(drafts[1].needsRefResolution)
        assertFalse(drafts[2].needsRefResolution)
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
        assertFalse(drafts[0].needsRefResolution)
        assertTrue(drafts[1].needsRefResolution)
        assertFalse(drafts[2].needsRefResolution)
    }

    @Test
    fun `a mismatched parser response is not mapped positionally`() {
        val drafts = prepareIngredientDrafts(
            lines = listOf("500 г куриного филе", "2 яйца"),
            parsed = listOf(parsed("500 г куриного филе", quantity = 500.0, foodName = "куриное филе")),
        )

        assertEquals(2, drafts.size)
        assertEquals(0, drafts.count { it.needsRefResolution })
        assertEquals(listOf("500 г куриного филе", "2 яйца"), drafts.map { it.note })
    }

    @Test
    fun `no lines means no drafts`() {
        assertTrue(prepareIngredientDrafts(emptyList(), null).isEmpty())
    }

    // --- conservative structured policy: physical smoke 2 regressions ---------

    /** Resolves a name to the id the map holds; anything absent is "not on the server". */
    private fun resolver(existing: Map<String, String>): suspend (String) -> IngredientRef? =
        { name -> existing[name]?.let { IngredientRef(it, name) } }

    private suspend fun draftOf(
        line: String,
        result: ParsedIngredient,
        units: Map<String, String> = emptyMap(),
        foods: Map<String, String> = emptyMap(),
    ) = applyResolvedRefs(
        drafts = prepareIngredientDrafts(listOf(line), listOf(result)),
        unitLookup = resolver(units),
        foodLookup = resolver(foods),
    ).single()

    @Test
    fun `A an unresolved food sends the whole parsed line to the note fallback`() = runTest {
        // Exactly what smoke 2 wrote: parser read the food, the dictionary did not have
        // it, and the old code still wrote quantity=500 + unit=грамм.
        val draft = draftOf(
            line = "500 г куриного филе",
            result = parsed("500 г куриного филе", quantity = 500.0, unitName = "грамм", foodName = "куриного филе"),
            units = mapOf("грамм" to "unit-gram"),
        )

        assertFalse("a partial reading must not survive", draft.isStructured)
        assertEquals("500 г куриного филе", draft.note)
        assertNull(draft.quantity)
        assertNull(draft.unitRef)
        assertNull(draft.foodRef)
    }

    @Test
    fun `B an unresolved unit sends the whole parsed line to the note fallback`() = runTest {
        val draft = draftOf(
            line = "2 яйца",
            result = parsed("2 яйца", quantity = 2.0, unitName = "штука", foodName = "яйцо"),
            foods = mapOf("яйцо" to "food-egg"),
        )

        assertFalse(draft.isStructured)
        assertEquals("2 яйца", draft.note)
        assertNull(draft.foodRef)
    }

    @Test
    fun `C a food the parser reported without a unit stays structured`() = runTest {
        val draft = draftOf(
            line = "2 яйца",
            result = parsed("2 яйца", quantity = 2.0, foodName = "яйцо"),
            foods = mapOf("яйцо" to "food-egg"),
        )

        assertTrue(draft.isStructured)
        assertEquals(2.0, draft.quantity!!, 0.0)
        assertEquals("food-egg", draft.foodRef!!.id)
    }

    @Test
    fun `D a tablespoon is never written as the litre the parser reports`() = runTest {
        val draft = draftOf(
            line = "1 ст. л. оливкового масла",
            result = parsed("1 ст. л. оливкового масла", quantity = 1.0, unitName = "литр", foodName = "ст. оливкового масла"),
            units = mapOf("литр" to "unit-litre"),
            foods = mapOf("ст. оливкового масла" to "food-junk"),
        )

        assertFalse("an existing unit is not an excuse for a wrong reading", draft.isStructured)
        assertEquals("1 ст. л. оливкового масла", draft.note)
        assertEquals("1 ст. л. оливкового масла", draft.originalText)
    }

    @Test
    fun `E a tablespoon without spaces is never written as a litre either`() = runTest {
        val draft = draftOf(
            line = "1 ст.л. оливкового масла",
            result = parsed("1 ст.л. оливкового масла", quantity = 1.0, unitName = "литр", foodName = "ст.л. оливкового масла"),
            units = mapOf("литр" to "unit-litre"),
        )

        assertFalse(draft.isStructured)
        assertEquals("1 ст.л. оливкового масла", draft.note)
    }

    @Test
    fun `F a spelled out tablespoon is never written as a litre`() = runTest {
        val draft = draftOf(
            line = "1 столовая ложка оливкового масла",
            result = parsed("1 столовая ложка оливкового масла", quantity = 1.0, unitName = "литр", foodName = "столовые ложки оливкового масла"),
            units = mapOf("литр" to "unit-litre"),
        )

        assertFalse(draft.isStructured)
        assertEquals("1 столовая ложка оливкового масла", draft.note)
    }

    @Test
    fun `G a teaspoon is never written as the litre the parser reports`() = runTest {
        val draft = draftOf(
            line = "1 ч. л. соли",
            result = parsed("1 ч. л. соли", quantity = 1.0, unitName = "литр", foodName = "соли"),
            units = mapOf("литр" to "unit-litre"),
        )

        assertFalse(draft.isStructured)
        assertEquals("1 ч. л. соли", draft.note)
    }

    @Test
    fun `H a line the parser read correctly is written structured`() = runTest {
        val draft = draftOf(
            line = "500 г муки",
            result = parsed("500 г муки", quantity = 500.0, unitName = "грамм", foodName = "муки"),
            units = mapOf("грамм" to "unit-gram"),
            foods = mapOf("муки" to "food-flour"),
        )

        assertTrue(draft.isStructured)
        assertEquals("unit-gram", draft.unitRef!!.id)
        assertEquals("food-flour", draft.foodRef!!.id)
    }

    @Test
    fun `I no structured draft keeps an unresolved relation, all-or-nothing`() = runTest {
        val lines = listOf("500 г куриного филе", "2 яйца", "1 ст. л. оливкового масла", "соль по вкусу", "200 мл молока")
        val results = listOf(
            parsed(lines[0], quantity = 500.0, unitName = "грамм", foodName = "куриного филе"),
            parsed(lines[1], quantity = 2.0, foodName = "яйцо"),
            parsed(lines[2], quantity = 1.0, unitName = "литр", foodName = "ст. оливкового масла"),
            parsed(lines[3], quantity = 0.0, foodName = "соль по вкусу"),
            parsed(lines[4], quantity = 200.0, unitName = "миллилитр", foodName = "молоко"),
        )
        val drafts = prepareIngredientDrafts(lines, results)

        val everything = applyResolvedRefs(drafts, { IngredientRef("u", it) }, { IngredientRef("f", it) })
        assertEquals(lines, everything.map { it.originalText })
        assertTrue(everything.all { !it.needsRefResolution })
        assertTrue(everything.all { d -> d.unitRef == null || d.unitName == null })

        val nothing = applyResolvedRefs(drafts, { null }, { null })
        assertTrue("no reference must mean no structure at all", nothing.none { it.isStructured })
        assertEquals(lines, nothing.map { it.note })
        assertTrue(nothing.none { it.unitRef != null || it.foodRef != null || it.quantity != null })
    }

    @Test
    fun `J the fallback keeps the user text verbatim`() = runTest {
        val raw = "  1 ст . л .  оливкового   масла "
        val draft = prepareIngredientDrafts(listOf(raw), null).single()

        assertEquals(raw, draft.note)
        assertEquals(raw, draft.originalText)

        val unusableParse = prepareIngredientDrafts(listOf(raw), emptyList()).single()
        assertEquals(raw, unusableParse.note)
    }

    @Test
    fun `the unit sanity rule rejects a non-spoon reading of a spoon marker`() {
        assertTrue(unitContradictsLine("1 ст. л. оливкового масла", "литр"))
        assertTrue(unitContradictsLine("1 ст.л. оливкового масла", "литр"))
        assertTrue(unitContradictsLine("1 ст л оливкового масла", "миллилитр"))
        assertTrue(unitContradictsLine("1 столовая ложка масла", "литр"))
        assertTrue(unitContradictsLine("1 столовую ложку масла", "литр"))
        assertTrue(unitContradictsLine("1 ч. л. соли", "литр"))
        assertTrue(unitContradictsLine("1 чайную ложку соли", "литр"))

        assertFalse(unitContradictsLine("1 ст. л. масла", "столовая ложка"))
        assertFalse(unitContradictsLine("1 ч. л. соли", "чайная ложка"))
        assertFalse("grams are none of this rule's business", unitContradictsLine("500 г куриного филе", "грамм"))
        assertFalse(unitContradictsLine("2 стакана молока", "литр"))
        assertFalse(unitContradictsLine("200 мл молока", "миллилитр"))
        assertFalse(unitContradictsLine("1 ст. л. масла", null))
        assertFalse(unitContradictsLine("", "литр"))
    }
}

