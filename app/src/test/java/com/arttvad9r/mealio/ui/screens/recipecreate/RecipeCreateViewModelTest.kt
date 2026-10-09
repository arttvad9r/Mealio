package com.arttvad9r.mealio.ui.screens.recipecreate

import com.arttvad9r.mealio.data.remote.ErrorKind
import com.arttvad9r.mealio.data.remote.MealioException
import com.arttvad9r.mealio.data.repository.RecipeWriteSource
import com.arttvad9r.mealio.domain.model.Nutrition
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
 * two-request create → PATCH flow, and the two guards that keep it from creating
 * duplicates — the double-Save guard and the remembered slug that turns a retry
 * after a failed PATCH back into a PATCH rather than a second POST.
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

    /** A [RecipeWriteSource] that records its calls and can fail or block on demand. */
    private inner class FakeWriteSource(
        private val createSlug: String = "slug-a",
        private val patchSlug: String? = null,
        private var failPatchOnce: Boolean = false,
        private val createGate: CompletableDeferred<Unit>? = null,
    ) : RecipeWriteSource {
        var createCalls = 0
        var updateCalls = 0
        val createNames = mutableListOf<String>()
        val updateSlugs = mutableListOf<String>()
        val drafts = mutableListOf<RecipeDraft>()

        override suspend fun createRecipe(name: String): String {
            createCalls++
            createNames += name
            createGate?.await()
            return createSlug
        }

        override suspend fun updateRecipe(slug: String, draft: RecipeDraft): RecipeDetail {
            updateCalls++
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
        assertEquals(listOf("500 г куриного филе", "2 яйца"), draft.ingredients)
        assertEquals(listOf("Нарезать"), draft.instructions)
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
        assertEquals(listOf("a", "b", "c"), draft.ingredients)
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

        vm.save()
        advanceUntilIdle()
        assertEquals(RecipeCreatePhase.ERROR, vm.state.value.phase)
        assertEquals(1, src.createCalls)
        assertEquals(1, src.updateCalls)

        vm.save()
        advanceUntilIdle()
        assertEquals(1, src.createCalls) // no duplicate POST
        assertEquals(2, src.updateCalls)
        assertEquals(listOf("slug-a", "slug-a"), src.updateSlugs)
        assertEquals(RecipeCreatePhase.SUCCESS, vm.state.value.phase)
    }

    @Test
    fun `double save issues a single post`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val src = FakeWriteSource(createGate = gate)
        val vm = vm(src)
        vm.onNameChange("Ужин")

        vm.save() // suspends inside createRecipe, saving = true
        vm.save() // ignored
        gate.complete(Unit)
        advanceUntilIdle()

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
