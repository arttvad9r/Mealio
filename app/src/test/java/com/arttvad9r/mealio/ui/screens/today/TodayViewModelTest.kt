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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private class FakeStorage(vararg initial: String?) : StringStorage {
        private val queue = ArrayDeque(initial.toList().filterNotNull())
        var value: String? = null
        override fun read(): String? = if (queue.isNotEmpty()) queue.removeFirst() else value
        override fun write(value: String) {
            this.value = value
        }
    }

    /**
     * Runs the store's first dispatched task only after [gate] opens, leaving
     * later tasks immediate. Lets a test hold the *first* `store.read()` open so
     * a newer load/refresh can overtake it — the exact window the generation
     * guard after `read()` defends.
     */
    private class PausingDispatcher : CoroutineDispatcher() {
        @Volatile
        var gate: CompletableDeferred<Unit>? = null
        private val scope = CoroutineScope(Dispatchers.Unconfined)

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            val g = gate
            if (g == null) {
                block.run()
            } else {
                gate = null // pause only the first task
                scope.launch {
                    g.await()
                    block.run()
                }
            }
        }
    }

    /**
     * A [RecipeSource] whose per-slug recipe loads can be held open ([blocking])
     * so a slow request can be overtaken by a newer selection. Blocked loads
     * ignore cancellation ([NonCancellable]) — they model a repository that does
     * not cooperate with cancellation, which is exactly what the slug guard in
     * the ViewModel must defend against.
     */
    private class FakeSource(
        private val recipes: List<RecipeSummary>,
        private val missing: Set<String> = emptySet(),
        private val blocking: Set<String> = emptySet(),
        private val failOn: Set<String> = emptySet(),
    ) : RecipeSource {
        private val gates = blocking.associateWith { CompletableDeferred<Unit>() }
        private var recipesCalls = 0
        var recipesGate: CompletableDeferred<Unit>? = null

        fun release(slug: String) {
            gates[slug]?.complete(Unit)
        }

        fun releaseRecipes() {
            recipesGate?.complete(Unit)
        }

        override suspend fun recipes(search: String?, categorySlug: String?): List<RecipeSummary> {
            recipesCalls++
            if (recipesCalls == 1) recipesGate?.await()
            return recipes
        }

        override suspend fun recipe(slug: String): RecipeDetail {
            gates[slug]?.let { withContext(NonCancellable) { it.await() } }
            if (slug in missing || slug in failOn) throw MealioException(ErrorKind.UNKNOWN)
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

    // --- concurrency hardening -------------------------------------------------

    @Test
    fun `slow A then cached B keeps B - stale A result is ignored`() = runTest {
        val src = FakeSource(
            recipes = listOf(summary("a", listOf("Основное")), summary("b", listOf("Основное"))),
            blocking = setOf("a"),
        )
        val vm = TodayViewModel(repository = src, store = store(FakeStorage()))
        advanceUntilIdle()

        vm.pick(TodaySlot.MAIN, "a")            // slow: blocked
        advanceUntilIdle()
        assertEquals("a", vm.state.value.slots.first { it.slot == TodaySlot.MAIN }.selection?.slug)

        vm.pick(TodaySlot.MAIN, "b")            // b is not blocked -> loads at once
        advanceUntilIdle()
        val afterB = vm.state.value.slots.first { it.slot == TodaySlot.MAIN }
        assertEquals("b", afterB.selection?.slug)
        assertEquals("b", afterB.detail?.slug)

        src.release("a")                        // stale A finally completes
        advanceUntilIdle()
        val afterA = vm.state.value.slots.first { it.slot == TodaySlot.MAIN }
        assertEquals("b", afterA.selection?.slug)
        assertEquals("b", afterA.detail?.slug)  // A never overwrote B
        assertEquals(400.0, vm.state.value.totals.calories, 0.001)
    }

    @Test
    fun `slow A then B then A completes - stale result does not touch the new selection`() = runTest {
        val src = FakeSource(
            recipes = listOf(summary("a", listOf("Основное")), summary("b", listOf("Основное"))),
            blocking = setOf("a"),
        )
        val vm = TodayViewModel(repository = src, store = store(FakeStorage()))
        advanceUntilIdle()

        vm.pick(TodaySlot.MAIN, "a")            // blocked
        advanceUntilIdle()
        vm.pick(TodaySlot.MAIN, "b")
        advanceUntilIdle()
        src.release("a")                        // A's response arrives late
        advanceUntilIdle()

        val slot = vm.state.value.slots.first { it.slot == TodaySlot.MAIN }
        assertEquals("b", slot.selection?.slug)
        assertEquals("b", slot.detail?.slug)
        assertNull(slot.error)
        assertEquals(400.0, vm.state.value.totals.calories, 0.001)
    }

    @Test
    fun `stale A failure does not show an error on B`() = runTest {
        val src = FakeSource(
            recipes = listOf(summary("a", listOf("Основное")), summary("b", listOf("Основное"))),
            blocking = setOf("a"),
            failOn = setOf("a"),
        )
        val vm = TodayViewModel(repository = src, store = store(FakeStorage()))
        advanceUntilIdle()

        vm.pick(TodaySlot.MAIN, "a")            // blocked, then will fail
        advanceUntilIdle()
        vm.pick(TodaySlot.MAIN, "b")
        advanceUntilIdle()
        src.release("a")                        // A's error surfaces late
        advanceUntilIdle()

        val slot = vm.state.value.slots.first { it.slot == TodaySlot.MAIN }
        assertEquals("b", slot.selection?.slug)
        assertEquals("b", slot.detail?.slug)
        assertNull(slot.error)                  // B shows no error
        assertFalse(slot.isLoading)
    }

    @Test
    fun `stale load does not restore the old snapshot selections`() = runTest {
        val src = FakeSource(recipes = listOf(summary("a", listOf("Основное"))))
        val pause = PausingDispatcher()
        pause.gate = CompletableDeferred()      // hold the first store.read() open

        // load #1 is paused *before* it pops, so the newer load #2 reads first
        // ("b"); when load #1 finally resumes it reads the stale "a" snapshot.
        val storage = FakeStorage(
            """{"date":"2026-10-08","selections":[{"slot":"MAIN","slug":"b","servings":1.0}]}""",
            """{"date":"2026-10-08","selections":[{"slot":"MAIN","slug":"a","servings":1.0}]}""",
        )
        val vm = TodayViewModel(repository = src, store = TodayStore(storage, { "2026-10-08" }, pause))
        advanceUntilIdle()                      // load #1 is paused inside store.read()

        vm.refresh()                            // load #2 starts (generation 2)
        advanceUntilIdle()

        pause.gate?.complete(Unit)              // load #1's read finally returns "a"
        advanceUntilIdle()

        val slot = vm.state.value.slots.first { it.slot == TodaySlot.MAIN }
        assertEquals("b", slot.selection?.slug) // not restored to the stale "a"
        assertEquals("b", slot.detail?.slug)
    }
}
