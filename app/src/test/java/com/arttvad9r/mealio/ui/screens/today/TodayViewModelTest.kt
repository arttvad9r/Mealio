package com.arttvad9r.mealio.ui.screens.today

import com.arttvad9r.mealio.data.local.StringStorage
import com.arttvad9r.mealio.data.local.TodayStore
import com.arttvad9r.mealio.data.remote.ErrorKind
import com.arttvad9r.mealio.data.remote.MealioException
import com.arttvad9r.mealio.data.repository.RecipeSource
import com.arttvad9r.mealio.domain.model.Nutrition
import com.arttvad9r.mealio.domain.model.RecipeDetail
import com.arttvad9r.mealio.domain.model.RecipeSummary
import com.arttvad9r.mealio.domain.today.DailyTarget
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

    // --- configurable daily calorie target --------------------------------------

    @Test
    fun `initial target falls back to the default`() = runTest {
        val vm = TodayViewModel(repository = FakeSource(recipes = emptyList()), store = store(FakeStorage()))
        assertEquals(DailyTarget.DEFAULT_CALORIES, vm.state.value.calorieTarget)
    }

    @Test
    fun `constructor target is applied`() = runTest {
        val vm = TodayViewModel(
            repository = FakeSource(recipes = emptyList()),
            store = store(FakeStorage()),
            calorieTarget = 1800,
        )
        assertEquals(1800, vm.state.value.calorieTarget)
    }

    @Test
    fun `updateTarget reflects the new value`() = runTest {
        val vm = TodayViewModel(repository = FakeSource(recipes = emptyList()), store = store(FakeStorage()))
        vm.updateTarget(2500)
        assertEquals(2500, vm.state.value.calorieTarget)
    }

    @Test
    fun `out-of-range target is clamped to the allowed bounds`() = runTest {
        val vm = TodayViewModel(repository = FakeSource(recipes = emptyList()), store = store(FakeStorage()))
        vm.updateTarget(DailyTarget.MAX_CALORIES + 5_000)
        assertEquals(DailyTarget.MAX_CALORIES, vm.state.value.calorieTarget)
        vm.updateTarget(0)
        assertEquals(DailyTarget.MIN_CALORIES, vm.state.value.calorieTarget)
    }

    @Test
    fun `changing the target keeps the chosen dishes and totals`() = runTest {
        val vm = TodayViewModel(
            repository = FakeSource(recipes = listOf(summary("chicken", listOf("Основное")))),
            store = store(FakeStorage()),
        )
        advanceUntilIdle()
        vm.pick(TodaySlot.MAIN, "chicken")
        advanceUntilIdle()
        assertEquals(400.0, vm.state.value.totals.calories, 0.001)

        vm.updateTarget(2000)
        advanceUntilIdle()
        val slot = vm.state.value.slots.first { it.slot == TodaySlot.MAIN }
        assertEquals("chicken", slot.selection?.slug)
        assertEquals(400.0, vm.state.value.totals.calories, 0.001) // totals untouched
        assertEquals(2000, vm.state.value.calorieTarget)
    }

    @Test
    fun `local date rollover clears selections but keeps the target`() = runTest {
        var date = "2026-10-08"
        val storage = FakeStorage()
        val dayStore = TodayStore(storage, { date }, dispatcher)
        dayStore.save(listOf(TodaySelection(TodaySlot.MAIN, "chicken", 1.0)))

        // A fresh ViewModel for the next day reads the same storage: selections
        // are stale (old date) -> empty, while the target is a user setting.
        date = "2026-10-09"
        val vm = TodayViewModel(
            repository = FakeSource(recipes = listOf(summary("chicken", listOf("Основное")))),
            store = TodayStore(storage, { date }, dispatcher),
            calorieTarget = 2000,
        )
        advanceUntilIdle()
        assertEquals(2000, vm.state.value.calorieTarget)
        assertNull(vm.state.value.slots.first { it.slot == TodaySlot.MAIN }.selection)
        assertEquals(0.0, vm.state.value.totals.calories, 0.001)
    }

    // --- servings stepper (V1.4) -------------------------------------------------

    @Test
    fun `picking a dish starts at one serving`() = runTest {
        val vm = TodayViewModel(
            repository = FakeSource(recipes = listOf(summary("chicken", listOf("Основное")))),
            store = store(FakeStorage()),
        )
        advanceUntilIdle()
        vm.pick(TodaySlot.MAIN, "chicken")
        advanceUntilIdle()
        val slot = vm.state.value.slots.first { it.slot == TodaySlot.MAIN }
        assertEquals(1.0, slot.selection?.servings ?: 0.0, 0.0001)
        assertEquals(400.0, vm.state.value.totals.calories, 0.001)
    }

    @Test
    fun `increasing servings multiplies the dish nutrition`() = runTest {
        val vm = TodayViewModel(
            repository = FakeSource(recipes = listOf(summary("chicken", listOf("Основное")))),
            store = store(FakeStorage()),
        )
        advanceUntilIdle()
        vm.pick(TodaySlot.MAIN, "chicken")
        advanceUntilIdle()
        vm.increaseServings(TodaySlot.MAIN)
        advanceUntilIdle()
        assertEquals(2.0, vm.state.value.slots.first { it.slot == TodaySlot.MAIN }.selection?.servings ?: 0.0, 0.0001)
        // 800 kcal over 2 recipe servings at 2 selected -> 800.
        assertEquals(800.0, vm.state.value.totals.calories, 0.001)
        assertEquals(40.0, vm.state.value.totals.protein, 0.001)
    }

    @Test
    fun `servings never drop below one`() = runTest {
        val vm = TodayViewModel(
            repository = FakeSource(recipes = listOf(summary("chicken", listOf("Основное")))),
            store = store(FakeStorage()),
        )
        advanceUntilIdle()
        vm.pick(TodaySlot.MAIN, "chicken")
        advanceUntilIdle()
        repeat(3) { vm.decreaseServings(TodaySlot.MAIN) }
        advanceUntilIdle()
        assertEquals(1.0, vm.state.value.slots.first { it.slot == TodaySlot.MAIN }.selection?.servings ?: 0.0, 0.0001)
        assertEquals(400.0, vm.state.value.totals.calories, 0.001)
    }

    @Test
    fun `several dishes sum their scaled nutrition`() = runTest {
        val vm = TodayViewModel(
            repository = FakeSource(
                recipes = listOf(summary("chicken", listOf("Основное")), summary("rice", listOf("Гарнир"))),
            ),
            store = store(FakeStorage()),
        )
        advanceUntilIdle()
        vm.pick(TodaySlot.MAIN, "chicken")   // 800/2 = 400 per serving
        vm.pick(TodaySlot.SIDE, "rice")      // 800/2 = 400 per serving
        advanceUntilIdle()
        assertEquals(800.0, vm.state.value.totals.calories, 0.001)

        vm.increaseServings(TodaySlot.MAIN)  // chicken x2
        advanceUntilIdle()
        assertEquals(1200.0, vm.state.value.totals.calories, 0.001)
    }

    @Test
    fun `servings survive a reload in the same day`() = runTest {
        val storage = FakeStorage()
        val src = FakeSource(recipes = listOf(summary("chicken", listOf("Основное"))))
        val vm = TodayViewModel(repository = src, store = store(storage))
        advanceUntilIdle()
        vm.pick(TodaySlot.MAIN, "chicken")
        advanceUntilIdle()
        vm.increaseServings(TodaySlot.MAIN)
        advanceUntilIdle()

        // A fresh ViewModel (relaunch) reads the same day back.
        val reloaded = TodayViewModel(repository = src, store = store(storage))
        advanceUntilIdle()
        val slot = reloaded.state.value.slots.first { it.slot == TodaySlot.MAIN }
        assertEquals("chicken", slot.selection?.slug)
        assertEquals(2.0, slot.selection?.servings ?: 0.0, 0.0001)
    }
}
