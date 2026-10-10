package com.arttvad9r.mealio.ui.screens.recipecreate

import com.arttvad9r.mealio.data.remote.ErrorKind
import com.arttvad9r.mealio.data.remote.MealioException
import com.arttvad9r.mealio.data.repository.RecipeWriteSource
import com.arttvad9r.mealio.domain.model.Nutrition
import com.arttvad9r.mealio.domain.model.ParsedIngredient
import com.arttvad9r.mealio.domain.model.RecipeDetail
import com.arttvad9r.mealio.domain.model.RecipeDraft
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The manual-create state machine: validation and trimming before any request, the
 * best-effort ingredient parsing that feeds the write, the two-request
 * create → PATCH flow, and the guards that keep it from creating duplicates — the
 * double-Save guard and the remembered slug that turns a retry after a failed PATCH
 * back into a PATCH rather than a second POST.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeCreateViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun detail(slug: String) = RecipeDetail(
        slug = slug,
        uuid = "uuid-$slug",
        name = slug,
        imageKey = null,
        categories = emptyList(),
        tags = emptyList(),
        servings = null,
        nutrition = Nutrition(calories = null, protein = null, fat = null, carbs = null),
        ingredients = emptyList(),
        instructions = emptyList(),
        totalTimeIso = null,
        prepTimeIso = null,
        description = null,
    )

    private fun parsed(
        input: String,
        quantity: Double? = null,
        unitName: String? = null,
        foodName: String? = null,
        note: String? = null,
    ) = ParsedIngredient(
        input = input,
        quantity = quantity,
        unitName = unitName,
        foodName = foodName,
        note = note,
        display = null,
    )

    /**
     * A [RecipeWriteSource] that records its calls (in order) and can fail, block or
     * parse on demand. [parser] defaults to "no structure", which is the same
     * note-only behaviour the manual baseline had.
     */
    private inner class FakeWriteSource(
        private val createSlug: String = "slug-a",
        private val patchSlug: String? = null,
        private var failPatchOnce: Boolean = false,
        private val createGate: CompletableDeferred<Unit>? = null,
        private val parser: (suspend (List<String>) -> List<ParsedIngredient>)? = null,
    ) : RecipeWriteSource {
        var createCalls = 0
        var updateCalls = 0
        var parseCalls = 0
        val calls = mutableListOf<String>()
        val createNames = mutableListOf<String>()
        val updateSlugs = mutableListOf<String>()
        val drafts = mutableListOf<RecipeDraft>()
        val parsedLines = mutableListOf<List<String>>()

        override suspend fun parseIngredients(lines: List<String>): List<ParsedIngredient> {
            parseCalls++
            calls += "parse"
            parsedLines += lines
            return parser?.invoke(lines) ?: emptyList()
        }

        override suspend fun createRecipe(name: String): String {
            createCalls++
            calls += "create"
            createNames += name
            createGate?.await()
            return createSlug
        }

        override suspend fun updateRecipe(slug: String, draft: RecipeDraft): RecipeDetail {
            updateCalls++
            calls += "update"
            updateSlugs += slug
            drafts += draft
            if (failPatchOnce) {
                failPatchOnce = false
                throw MealioException(ErrorKind.UNREACHABLE, "io")
            }
            return detail(patchSlug ?: slug)
        }
    }

    private fun vm(source: FakeWriteSource) = RecipeCreateViewModel(source)

    /** Fills three ingredient rows with the lines the smoke test uses. */
    private fun RecipeCreateViewModel.fillSmokeIngredients() {
        addIngredient()
        addIngredient()
        val ids = state.value.ingredients.map { it.id }
        onIngredientChange(ids[0], "500 г куриного филе")
        onIngredientChange(ids[1], "2 яйца")
        onIngredientChange(ids[2], "соль по вкусу")
    }

    // --- validation -------------------------------------------------------------

    @Test
    fun `blank name does not save and flags the field`() = runTest {
        val src = FakeWriteSource()
        val vm = vm(src)
        vm.onNameChange("   ")
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.nameError)
        assertEquals(RecipeCreatePhase.EDITING, vm.state.value.phase)
        assertEquals(0, src.createCalls)
        assertEquals(0, src.updateCalls)
    }

    @Test
    fun `invalid servings does not call the network`() = runTest {
        val src = FakeWriteSource()
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.onServingsChange("abc")
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.servingsError)
        assertEquals(RecipeCreatePhase.EDITING, vm.state.value.phase)
        assertEquals(0, src.createCalls)
        assertEquals(0, src.updateCalls)
    }

    @Test
    fun `name is trimmed before the request`() = runTest {
        val src = FakeWriteSource()
        val vm = vm(src)
        vm.onNameChange("  Ужин  ")
        vm.save()
        advanceUntilIdle()

        assertEquals(listOf("Ужин"), src.createNames)
        assertEquals("Ужин", src.drafts.single().name)
    }

    // --- draft shape ------------------------------------------------------------

    @Test
    fun `form produces the expected draft with blank lines dropped`() = runTest {
        val src = FakeWriteSource()
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.onDescriptionChange("   ")
        vm.onServingsChange("4")
        // three ingredient rows, the last one blank
        vm.addIngredient()
        vm.addIngredient()
        val ingredientIds = vm.state.value.ingredients.map { it.id }
        vm.onIngredientChange(ingredientIds[0], " 500 г куриного филе ")
        vm.onIngredientChange(ingredientIds[1], "2 яйца")
        vm.onIngredientChange(ingredientIds[2], "   ")
        // two step rows, the last one blank
        vm.addStep()
        val stepIds = vm.state.value.steps.map { it.id }
        vm.onStepChange(stepIds[0], " Нарезать ")
        vm.onStepChange(stepIds[1], "   ")
        vm.save()
        advanceUntilIdle()

        val draft = src.drafts.single()
        assertEquals("Ужин", draft.name)
        assertNull("blank description must stay null", draft.description)
        assertEquals(4.0, draft.servings!!, 0.0)
        assertEquals(
            listOf("500 г куриного филе", "2 яйца"),
            draft.ingredients.map { it.originalText },
        )
        // the fake parser returns nothing usable, so both lines stay note-only
        assertEquals(
            listOf("500 г куриного филе", "2 яйца"),
            draft.ingredients.map { it.note },
        )
        assertEquals(0, draft.ingredients.count { it.isStructured })
        assertEquals(listOf("Нарезать"), draft.instructions)
        // blank lines never reach the parser
        assertEquals(listOf(listOf("500 г куриного филе", "2 яйца")), src.parsedLines)
    }

    @Test
    fun `ingredient and instruction order is preserved`() = runTest {
        val src = FakeWriteSource()
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.addIngredient()
        vm.addIngredient()
        val ingredientIds = vm.state.value.ingredients.map { it.id }
        vm.onIngredientChange(ingredientIds[0], "a")
        vm.onIngredientChange(ingredientIds[1], "b")
        vm.onIngredientChange(ingredientIds[2], "c")
        vm.addStep()
        val stepIds = vm.state.value.steps.map { it.id }
        vm.onStepChange(stepIds[0], "s1")
        vm.onStepChange(stepIds[1], "s2")
        vm.save()
        advanceUntilIdle()

        val draft = src.drafts.single()
        assertEquals(listOf("a", "b", "c"), draft.ingredients.map { it.originalText })
        assertEquals(listOf("s1", "s2"), draft.instructions)
    }

    @Test
    fun `blank servings is accepted as no servings`() = runTest {
        val src = FakeWriteSource()
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.save()
        advanceUntilIdle()

        assertNull(src.drafts.single().servings)
        assertEquals(RecipeCreatePhase.SUCCESS, vm.state.value.phase)
    }

    @Test
    fun `removing a row keeps the rest in order`() = runTest {
        val vm = vm(FakeWriteSource())
        vm.addIngredient()
        vm.addIngredient()
        val ids = vm.state.value.ingredients.map { it.id }
        vm.removeIngredient(ids[1])
        assertEquals(listOf(ids[0], ids[2]), vm.state.value.ingredients.map { it.id })

        vm.addStep()
        val stepIds = vm.state.value.steps.map { it.id }
        vm.removeStep(stepIds[1])
        assertEquals(listOf(stepIds[0]), vm.state.value.steps.map { it.id })
    }

    // --- ingredient parsing -----------------------------------------------------

    @Test
    fun `parser runs before the first post`() = runTest {
        val src = FakeWriteSource()
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.fillSmokeIngredients()
        vm.save()
        advanceUntilIdle()

        assertEquals(listOf("parse", "create", "update"), src.calls)
        assertEquals(1, src.parseCalls)
    }

    @Test
    fun `structured parse result reaches the patch draft`() = runTest {
        val src = FakeWriteSource(parser = { lines ->
            lines.mapIndexed { index, line ->
                if (index == 0) parsed(line, quantity = 500.0, unitName = "грамм", foodName = "куриное филе")
                else parsed(line)
            }
        })
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.fillSmokeIngredients()
        vm.save()
        advanceUntilIdle()

        val ingredients = src.drafts.single().ingredients
        assertEquals(3, ingredients.size)
        assertEquals(
            listOf("500 г куриного филе", "2 яйца", "соль по вкусу"),
            ingredients.map { it.originalText },
        )
        assertEquals(500.0, ingredients[0].quantity!!, 0.0)
        assertEquals("грамм", ingredients[0].unitName)
        assertEquals("куриное филе", ingredients[0].foodName)
        assertTrue(ingredients[0].isStructured)
        // the other two lines had no usable structure: safe note-only fallback
        assertEquals("2 яйца", ingredients[1].note)
        assertEquals("соль по вкусу", ingredients[2].note)
        assertFalse(ingredients[1].isStructured)
        assertFalse(ingredients[2].isStructured)
    }

    @Test
    fun `parser failure still creates and patches note-only`() = runTest {
        val src = FakeWriteSource(parser = { throw MealioException(ErrorKind.UNREACHABLE, "parser down") })
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.fillSmokeIngredients()
        vm.save()
        advanceUntilIdle()

        assertEquals(RecipeCreatePhase.SUCCESS, vm.state.value.phase)
        assertEquals(1, src.createCalls)
        assertEquals(1, src.updateCalls)
        val ingredients = src.drafts.single().ingredients
        assertEquals(
            listOf("500 г куриного филе", "2 яйца", "соль по вкусу"),
            ingredients.map { it.note },
        )
        assertEquals(0, ingredients.count { it.isStructured })
    }

    @Test
    fun `a parser exception is never a fatal create error`() = runTest {
        val src = FakeWriteSource(parser = { throw IllegalStateException("malformed response") })
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.fillSmokeIngredients()
        vm.save()
        advanceUntilIdle()

        assertEquals(RecipeCreatePhase.SUCCESS, vm.state.value.phase)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `mixed parser result keeps structured lines and falls back the rest`() = runTest {
        val src = FakeWriteSource(parser = { lines ->
            listOf(
                parsed(lines[0], quantity = 500.0, unitName = "грамм", foodName = "куриное филе"),
                parsed(lines[1], quantity = 2.0, foodName = "яйцо"),
                // "соль по вкусу": the parser echoes the whole line as the food name
                parsed(lines[2], quantity = 0.0, foodName = "соль по вкусу"),
            )
        })
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.fillSmokeIngredients()
        vm.save()
        advanceUntilIdle()

        val ingredients = src.drafts.single().ingredients
        assertTrue(ingredients[0].isStructured)
        assertTrue(ingredients[1].isStructured)
        assertFalse("a food equal to the whole line is not structure", ingredients[2].isStructured)
        assertEquals("соль по вкусу", ingredients[2].note)
    }

    @Test
    fun `no ingredient lines skips the parser`() = runTest {
        val src = FakeWriteSource()
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.save()
        advanceUntilIdle()

        assertEquals(0, src.parseCalls)
        assertEquals(listOf("create", "update"), src.calls)
    }

    // --- the create → PATCH flow -------------------------------------------------

    @Test
    fun `create then patch success`() = runTest {
        val src = FakeWriteSource()
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.save()
        advanceUntilIdle()

        assertEquals(1, src.createCalls)
        assertEquals(1, src.updateCalls)
        assertEquals(listOf("slug-a"), src.updateSlugs)
        assertEquals(RecipeCreatePhase.SUCCESS, vm.state.value.phase)
        assertEquals("slug-a", vm.state.value.createdSlug)
    }

    @Test
    fun `success uses the slug returned by the patch`() = runTest {
        val src = FakeWriteSource(patchSlug = "slug-b")
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.save()
        advanceUntilIdle()

        assertEquals("slug-b", vm.state.value.createdSlug)
    }

    @Test
    fun `retry after a failed patch repeats the patch, not a second post`() = runTest {
        val src = FakeWriteSource(failPatchOnce = true)
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.fillSmokeIngredients()

        vm.save()
        advanceUntilIdle()
        assertEquals(RecipeCreatePhase.ERROR, vm.state.value.phase)
        assertEquals(1, src.createCalls)
        assertEquals(1, src.updateCalls)

        vm.save()
        advanceUntilIdle()
        assertEquals(1, src.createCalls) // no duplicate POST
        assertEquals(1, src.parseCalls) // prepared draft reused, no re-parse
        assertEquals(2, src.updateCalls)
        assertEquals(listOf("slug-a", "slug-a"), src.updateSlugs)
        assertEquals(RecipeCreatePhase.SUCCESS, vm.state.value.phase)
    }

    @Test
    fun `retry after a failed patch reuses the prepared draft`() = runTest {
        val src = FakeWriteSource(
            failPatchOnce = true,
            parser = { lines ->
                listOf(parsed(lines[0], quantity = 500.0, unitName = "грамм", foodName = "куриное филе"))
            },
        )
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.onIngredientChange(vm.state.value.ingredients[0].id, "500 г куриного филе")

        vm.save()
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        assertEquals(2, src.drafts.size)
        assertEquals(src.drafts[0].ingredients, src.drafts[1].ingredients)
        assertTrue(src.drafts[1].ingredients[0].isStructured)
    }

    @Test
    fun `editing the form after a failed patch re-parses but never re-posts`() = runTest {
        val src = FakeWriteSource(failPatchOnce = true)
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.onIngredientChange(vm.state.value.ingredients[0].id, "2 яйца")

        vm.save()
        advanceUntilIdle()
        assertEquals(RecipeCreatePhase.ERROR, vm.state.value.phase)

        // the user edits the lines before retrying: the prepared draft is stale
        vm.onIngredientChange(vm.state.value.ingredients[0].id, "3 яйца")
        vm.save()
        advanceUntilIdle()

        assertEquals(2, src.parseCalls) // re-parsed after the edit
        assertEquals(1, src.createCalls) // but the recipe already exists
        assertEquals(2, src.updateCalls)
        assertEquals("3 яйца", src.drafts[1].ingredients.single().originalText)
        assertEquals(RecipeCreatePhase.SUCCESS, vm.state.value.phase)
    }

    @Test
    fun `double save issues a single post`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val src = FakeWriteSource(createGate = gate)
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.onIngredientChange(vm.state.value.ingredients[0].id, "2 яйца")

        vm.save() // suspends inside createRecipe, saving = true
        vm.save() // ignored
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, src.parseCalls)
        assertEquals(1, src.createCalls)
        assertEquals(1, src.updateCalls)
        assertEquals(RecipeCreatePhase.SUCCESS, vm.state.value.phase)
    }

    @Test
    fun `a failed patch keeps the created slug for the retry`() = runTest {
        val src = FakeWriteSource(failPatchOnce = true)
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.save()
        advanceUntilIdle()

        assertEquals("slug-a", vm.state.value.createdSlug)
        assertTrue(vm.hasUnsavedInput()) // leaving would strand the created recipe
    }

    @Test
    fun `reset clears the form and the remembered slug`() = runTest {
        val src = FakeWriteSource(failPatchOnce = true)
        val vm = vm(src)
        vm.onNameChange("Ужин")
        vm.save()
        advanceUntilIdle()

        vm.reset()
        assertEquals("", vm.state.value.name)
        assertEquals(RecipeCreatePhase.EDITING, vm.state.value.phase)
        assertNull(vm.state.value.createdSlug)
        assertFalse(vm.hasUnsavedInput())

        vm.onNameChange("Другое")
        vm.save()
        advanceUntilIdle()
        assertEquals(2, src.createCalls) // a fresh session starts a fresh create
    }
}
