package com.arttvad9r.mealio.ui.screens.recipedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arttvad9r.mealio.data.repository.RecipeRepository
import com.arttvad9r.mealio.domain.model.IngredientLine
import com.arttvad9r.mealio.domain.model.RecipeDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RecipeTab { INGREDIENTS, INSTRUCTIONS }

data class RecipeDetailUiState(
    val recipe: RecipeDetail? = null,
    val isLoading: Boolean = true,
    val error: Throwable? = null,
    /** Currently selected serving count; kept as Double because Mealie stores servings as a float. */
    val servings: Double = 1.0,
    /** The recipe's own serving count, used as the scaling base; null when the recipe has none. */
    val baseServings: Double? = null,
    val tab: RecipeTab = RecipeTab.INGREDIENTS,
)

/** An ingredient with its quantity already scaled to the chosen serving count. */
data class ScaledIngredient(
    val name: String,
    val quantity: Double?,
    val unit: com.arttvad9r.mealio.domain.model.IngredientUnit?,
    val note: String?,
)

class RecipeDetailViewModel(
    private val repository: RecipeRepository,
    private val slug: String,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeDetailUiState())
    val state: StateFlow<RecipeDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.recipe(slug) }
                .onSuccess { recipe ->
                    // Mealie serves servings as a float; keep the original value
                    // so a fractional yield (e.g. 2.5) is not silently rounded.
                    val base = recipe.servings?.takeIf { it > 0.0 }
                    _state.update {
                        it.copy(
                            recipe = recipe,
                            isLoading = false,
                            error = null,
                            baseServings = base,
                            servings = base ?: 1.0,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e) }
                }
        }
    }

    fun setTab(tab: RecipeTab) = _state.update { it.copy(tab = tab) }

    fun decreaseServings() {
        if (_state.value.baseServings == null) return
        _state.update { it.copy(servings = (it.servings - 1.0).coerceAtLeast(1.0)) }
    }

    fun increaseServings() {
        if (_state.value.baseServings == null) return
        _state.update { it.copy(servings = it.servings + 1.0) }
    }
}

/** Scales one ingredient line by the serving factor; never mutates the server copy. */
fun IngredientLine.scale(factor: Double): ScaledIngredient = ScaledIngredient(
    name = foodName ?: originalText ?: title ?: "",
    quantity = quantity?.let { if (factor == 1.0) it else it * factor },
    unit = unit,
    note = note,
)
