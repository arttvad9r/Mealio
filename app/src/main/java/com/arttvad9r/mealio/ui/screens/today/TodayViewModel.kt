package com.arttvad9r.mealio.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arttvad9r.mealio.data.local.TodayStore
import com.arttvad9r.mealio.data.repository.RecipeSource
import com.arttvad9r.mealio.domain.model.RecipeDetail
import com.arttvad9r.mealio.domain.model.RecipeSummary
import com.arttvad9r.mealio.domain.today.DailyNutrition
import com.arttvad9r.mealio.domain.today.NutritionCalculator
import com.arttvad9r.mealio.domain.today.TodaySelection
import com.arttvad9r.mealio.domain.today.TodaySlot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One slot card on the "Today" screen. */
data class TodaySlotUi(
    val slot: TodaySlot,
    val selection: TodaySelection? = null,
    /** Loaded recipe detail; null until loaded, or when it could not be restored. */
    val detail: RecipeDetail? = null,
    val isLoading: Boolean = false,
    /** Non-null when the saved recipe could not be restored (deleted, offline, …). */
    val error: Throwable? = null,
)

data class TodayUiState(
    val isLoading: Boolean = true,
    val loadError: Throwable? = null,
    val summaries: List<RecipeSummary> = emptyList(),
    val slots: List<TodaySlotUi> = TodaySlot.entries.map { TodaySlotUi(slot = it) },
    val totals: DailyNutrition = DailyNutrition(),
)

/**
 * "Today" screen state. Owns the day's selections (persisted in [TodayStore]),
 * the recipe summaries used by the selector, and an in-memory detail cache so a
 * recipe is never fetched twice in a live ViewModel. It never loads every
 * recipe's detail up front — details are fetched only for chosen dishes.
 */
class TodayViewModel(
    private val repository: RecipeSource,
    private val store: TodayStore,
) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    private val detailCache = mutableMapOf<String, RecipeDetail>()
    private val slotJobs = mutableMapOf<TodaySlot, Job>()
    private var loadGeneration = 0

    init {
        load()
    }

    /** Loads the saved day and the recipe list once; restores saved details. */
    fun load() {
        val generation = ++loadGeneration
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val saved = store.read()
            // A newer load/refresh supersedes this one: never apply (or restore)
            // an older snapshot's selections, even if this coroutine resumes late.
            if (generation != loadGeneration) return@launch
            // Merge saved selections into the slot list before the network call
            // so a failed/absent server still shows what was chosen.
            applySelections(saved)
            try {
                val summaries = repository.recipes().sortedBy { it.name.lowercase() }
                if (generation != loadGeneration) return@launch
                _state.update { it.copy(summaries = summaries, isLoading = false, loadError = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (generation != loadGeneration) return@launch
                _state.update { it.copy(isLoading = false, loadError = e) }
            }
            if (generation != loadGeneration) return@launch
            // Restore details for whatever is selected (cache hit = no request).
            saved.forEach { ensureDetail(it.slot, it.slug) }
        }
    }

    fun refresh() = load()

    /** Candidates for the slot selector; EXTRA accepts any recipe. */
    fun candidates(slot: TodaySlot): List<RecipeSummary> =
        _state.value.summaries.filter { slot.matches(it.categories) }

    fun pick(slot: TodaySlot, slug: String) {
        applySelections(_state.value.slots.mapNotNull { it.selection }.filterNot { it.slot == slot }
            + TodaySelection(slot = slot, slug = slug, servings = 1.0))
        persist()
        ensureDetail(slot, slug)
    }

    fun increaseServings(slot: TodaySlot) = changeServings(slot, 1.0)

    fun decreaseServings(slot: TodaySlot) = changeServings(slot, -1.0)

    fun remove(slot: TodaySlot) {
        slotJobs.remove(slot)?.cancel()
        applySelections(_state.value.slots.mapNotNull { it.selection }.filterNot { it.slot == slot })
        persist()
    }

    /** Retry loading a single slot whose detail failed. */
    fun retryDetail(slot: TodaySlot) {
        val slug = _state.value.slots.firstOrNull { it.slot == slot }?.selection?.slug ?: return
        ensureDetail(slot, slug, force = true)
    }

    private fun changeServings(slot: TodaySlot, delta: Double) {
        val current = _state.value.slots.firstOrNull { it.slot == slot }?.selection ?: return
        if (delta < 0 && current.servings <= 1.0) return
        val next = current.servings + delta
        if (next < 1.0) return
        val selections = _state.value.slots.mapNotNull { it.selection }
            .map { if (it.slot == slot) it.copy(servings = next) else it }
        applySelections(selections)
        persist()
    }

    private fun applySelections(selections: List<TodaySelection>) {
        _state.update { state ->
            val updated = state.slots.map { ui ->
                val selection = selections.firstOrNull { it.slot == ui.slot }
                if (selection == null) {
                    TodaySlotUi(slot = ui.slot)
                } else {
                    ui.copy(selection = selection, error = null)
                }
            }
            state.copy(slots = updated, totals = totalsOf(updated))
        }
    }

    private fun persist() {
        val selections = _state.value.slots.mapNotNull { it.selection }
        viewModelScope.launch { store.save(selections) }
    }

    /**
     * Loads a slot's detail (cache first). On failure the slot keeps its
     * selection and shows a compact error — it never crashes the screen.
     */
    private fun ensureDetail(slot: TodaySlot, slug: String, force: Boolean = false) {
        // Any new load for this slot supersedes the previous one — cache hit
        // included: a still-running request for the old slug must never write
        // its result over the new selection.
        slotJobs.remove(slot)?.cancel()
        if (!force) {
            detailCache[slug]?.let { cached ->
                updateSlot(slot) { it.copy(detail = cached, isLoading = false, error = null) }
                return
            }
        }
        updateSlot(slot) { it.copy(isLoading = true, error = null) }
        slotJobs[slot] = viewModelScope.launch {
            try {
                val detail = repository.recipe(slug)
                detailCache[slug] = detail
                applyIfSelected(slot, slug) { it.copy(detail = detail, isLoading = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                detailCache.remove(slug)
                applyIfSelected(slot, slug) { it.copy(detail = null, isLoading = false, error = e) }
            }
        }
    }

    /**
     * Applies [transform] to [slot] only while its selection still points at
     * [slug]. A stale response (the user already switched the slot to another
     * recipe) never touches detail/error/loading of the new selection.
     */
    private fun applyIfSelected(slot: TodaySlot, slug: String, transform: (TodaySlotUi) -> TodaySlotUi) {
        val selected = _state.value.slots.firstOrNull { it.slot == slot }?.selection?.slug
        if (selected != slug) return
        updateSlot(slot, transform)
    }

    /** Applies [transform] to one slot and recomputes the daily totals. */
    private fun updateSlot(slot: TodaySlot, transform: (TodaySlotUi) -> TodaySlotUi) {
        _state.update { state ->
            val updated = state.slots.map { if (it.slot == slot) transform(it) else it }
            state.copy(slots = updated, totals = totalsOf(updated))
        }
    }

    private fun totalsOf(slots: List<TodaySlotUi>): DailyNutrition =
        NutritionCalculator.sum(
            slots.mapNotNull { ui ->
                val detail = ui.detail ?: return@mapNotNull null
                NutritionCalculator.contribution(
                    nutrition = detail.nutrition,
                    recipeServings = detail.servings,
                    selectedServings = ui.selection?.servings ?: 1.0,
                )
            },
        )
}
