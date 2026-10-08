package com.arttvad9r.mealio.ui.screens.today

import com.arttvad9r.mealio.data.local.StringStorage
import com.arttvad9r.mealio.data.local.TodayStore
import com.arttvad9r.mealio.data.remote.ErrorKind
import com.arttvad9r.mealio.data.remote.MealioException
import com.arttvad9r.mealio.data.repository.RecipeSource
import com.arttvad9r.mealio.domain.model.Nutrition
import com.arttvad9r.mealio.domain.model.RecipeDetail
import com.arttvad9r.mealio.domain.model.RecipeSummary
import com.arttvad9r.mealio.domain.today.TodaySelection
import com.arttvad9r.mealio.domain.today.TodaySlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The "restore a saved day" path must never crash when a recipe can no longer be
 * loaded (deleted on the server, offline). [FakeSource] throws for the "gone"
 * slug; the slot keeps its selection and exposes an error instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun store(storage: StringStorage, date: String = "2026-10-08") =
        TodayStore(storage, { date }, dispatcher)

    private class FakeStorage : StringStorage {
        var value: String? = null
        override fun read(): String? = value
        override fun write(value: String) {
            this.value = value
        }
    }

    private fun summary(slug: String, categories: List<String>) = RecipeSummary(
        slug = slug,
        uuid = "uuid-$slug",
        name = slug,
        imageKey = null,
        categories = categories,
        tags = emptyList(),
        calories = null,
        protein = null,
        fat = null,
        carbs = null,
        totalTimeIso = null,
        servings = null,
    )

    private class FakeSource(
        private val recipes: List<RecipeSummary>,
        private val missing: Set<String>,
    ) : RecipeSource {
        override suspend fun recipes(search: String?, categorySlug: String?): List<RecipeSummary> = recipes
        override suspend fun recipe(slug: String): RecipeDetail {
            if (slug in missing) throw MealioException(ErrorKind.UNKNOWN)
            return RecipeDetail(
                slug = slug,
                uuid = "uuid-$slug",
                name = slug,
                imageKey = null,
                categories = emptyList(),
                tags = emptyList(),
                servings = 2.0,
                nutrition = Nutrition(calories = "800", protein = "40", fat = "20", carbs = "90"),
                ingredients = emptyList(),
                instructions = emptyList(),
                totalTimeIso = null,
                prepTimeIso = null,
                description = null,
            )
        }
    }

    @Test
    fun `state loads summaries and filters candidates by category`() = runTest {
        val vm = TodayViewModel(
            repository = FakeSource(
                recipes = listOf(summary("chicken", listOf("Основное")), summary("rice", listOf("Гарнир"))),
                missing = emptySet(),
            ),
            store = store(FakeStorage()),
        )
        advanceUntilIdle()
        assertEquals(2, vm.state.value.summaries.size)
        assertEquals(listOf("chicken"), vm.candidates(TodaySlot.MAIN).map { it.slug })
        assertEquals(listOf("rice"), vm.candidates(TodaySlot.SIDE).map { it.slug })
        assertEquals(2, vm.candidates(TodaySlot.EXTRA).size)
    }

    @Test
    fun `choosing a dish loads detail and sums calories`() = runTest {
        val vm = TodayViewModel(
            repository = FakeSource(
                recipes = listOf(summary("chicken", listOf("Основное"))),
                missing = emptySet(),
            ),
            store = store(FakeStorage()),
        )
        advanceUntilIdle()
        vm.pick(TodaySlot.MAIN, "chicken")
        advanceUntilIdle()
        val slot = vm.state.value.slots.first { it.slot == TodaySlot.MAIN }
        assertNotNull(slot.detail)
        // 800 kcal over 2 servings, 1 selected -> 400.
        assertEquals(400.0, vm.state.value.totals.calories, 0.001)
    }

    @Test
    fun `missing recipe does not crash and keeps the selection`() = runTest {
        val storage = FakeStorage()
        store(storage).save(listOf(TodaySelection(TodaySlot.MAIN, "gone", 1.0)))
        val vm = TodayViewModel(
            repository = FakeSource(recipes = emptyList(), missing = setOf("gone")),
            store = store(storage),
        )
        advanceUntilIdle()
        val slot = vm.state.value.slots.first { it.slot == TodaySlot.MAIN }
        assertNotNull(slot.selection)
        assertEquals("gone", slot.selection?.slug)
        assertNull(slot.detail)
        assertNotNull(slot.error)
    }
}
