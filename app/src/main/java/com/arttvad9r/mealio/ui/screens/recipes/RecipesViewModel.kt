package com.arttvad9r.mealio.ui.screens.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arttvad9r.mealio.data.repository.RecipeRepository
import com.arttvad9r.mealio.domain.model.RecipeSummary
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

    init {
        loadCategories()
        load()
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            load()
        }
    }

    fun onCategorySelected(slug: String?) {
        if (_state.value.selectedCategorySlug == slug) {
            _state.update { it.copy(selectedCategorySlug = null) }
        } else {
            _state.update { it.copy(selectedCategorySlug = slug) }
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

    private fun load() {
        val current = _state.value
        _state.update { it.copy(isLoading = current.recipes.isEmpty(), error = null) }
        viewModelScope.launch {
            runCatching {
                repository.recipes(
                    search = current.query,
                    categorySlug = current.selectedCategorySlug,
                )
            }.onSuccess { list ->
                _state.update {
                    it.copy(recipes = list, isLoading = false, isRefreshing = false, error = null)
                }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = e) }
            }
        }
    }
}
