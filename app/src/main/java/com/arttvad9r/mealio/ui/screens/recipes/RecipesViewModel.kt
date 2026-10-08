package com.arttvad9r.mealio.ui.screens.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arttvad9r.mealio.data.repository.RecipeRepository
import com.arttvad9r.mealio.domain.model.RecipeSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipesUiState(
    val recipes: List<RecipeSummary> = emptyList(),
    val categories: List<Pair<String, String>> = emptyList(),
    val selectedCategorySlug: String? = null,
    val query: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: Throwable? = null,
)

class RecipesViewModel(private val repository: RecipeRepository) : ViewModel() {

    private val _state = MutableStateFlow(RecipesUiState())
    val state: StateFlow<RecipesUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var loadJob: Job? = null

    /** Monotonic id; only the newest load may write the UI, so a slow older
     *  response can never overwrite fresher data. */
    private var loadGeneration = 0

    init {
        loadCategories()
        load()
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            load()
        }
    }

    fun onCategorySelected(slug: String?) {
        // Drop a pending debounce so it cannot fire with a stale category.
        searchJob?.cancel()
        _state.update {
            it.copy(selectedCategorySlug = if (it.selectedCategorySlug == slug) null else slug)
        }
        load()
    }

    fun retry() = load()

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        load()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            runCatching { repository.categories() }
                .onSuccess { cats -> _state.update { it.copy(categories = cats) } }
        }
    }

    /**
     * Cancels any in-flight load and starts a new one for the current query and
     * category. Combined with the generation check this guarantees that only the
     * most recent request ever touches the UI state.
     */
    private fun load() {
        val current = _state.value
        val generation = ++loadGeneration
        loadJob?.cancel()
        _state.update { it.copy(isLoading = current.recipes.isEmpty(), error = null) }
        loadJob = viewModelScope.launch {
            try {
                val list = repository.recipes(
                    search = current.query,
                    categorySlug = current.selectedCategorySlug,
                )
                if (generation != loadGeneration) return@launch
                _state.update {
                    it.copy(recipes = list, isLoading = false, isRefreshing = false, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (generation != loadGeneration) return@launch
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = e) }
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
