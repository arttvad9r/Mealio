package com.arttvad9r.mealio.ui.screens.recipecreate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arttvad9r.mealio.data.repository.RecipeWriteSource
import com.arttvad9r.mealio.domain.model.RecipeDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lifecycle of the manual-create flow. SUCCESS carries the final slug in the state. */
enum class RecipeCreatePhase { EDITING, SAVING, SUCCESS, ERROR }

/** One editable ingredient line, identified so rows survive reordering/removal. */
data class IngredientField(val id: Long, val text: String)

/** One editable step line, identified for the same reason. */
data class StepField(val id: Long, val text: String)

data class RecipeCreateUiState(
    val name: String = "",
    val description: String = "",
    val servings: String = "",
    val ingredients: List<IngredientField> = listOf(IngredientField(id = 0L, text = "")),
    val steps: List<StepField> = listOf(StepField(id = 0L, text = "")),
    val phase: RecipeCreatePhase = RecipeCreatePhase.EDITING,
    val nameError: Boolean = false,
    val servingsError: Boolean = false,
    /** Non-null only in [RecipeCreatePhase.ERROR]; mapped to a message by the UI. */
    val error: Throwable? = null,
    /** The slug of the recipe created in this session; non-null after a successful POST. */
    val createdSlug: String? = null,
)

/**
 * Drives manual recipe creation (V1.5). The flow is two requests against Mealie —
 * POST /api/recipes (returns a slug) then PATCH /api/recipes/{slug} with the
 * content — so the ViewModel keeps the slug of a recipe it already created: a
 * retry after a failed PATCH repeats the PATCH of that recipe, it never issues a
 * second POST (which would leave a duplicate behind). The double-Save guard
 * ([saving]) is independent, so a double tap cannot create two recipes either.
 */
class RecipeCreateViewModel(private val repository: RecipeWriteSource) : ViewModel() {

    private val _state = MutableStateFlow(RecipeCreateUiState())
    val state: StateFlow<RecipeCreateUiState> = _state.asStateFlow()

    /** Slug of the recipe created in this session, kept across a failed PATCH. */
    private var createdSlug: String? = null

    /** True while a save is in flight: the one guard against a duplicate POST. */
    private var saving = false

    /** Monotonic row ids; only unique within the current list is required. */
    private var idSeq = 0L

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = false) }

    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }

    fun onServingsChange(value: String) = _state.update { it.copy(servings = value, servingsError = false) }

    fun onIngredientChange(id: Long, text: String) = _state.update { state ->
        state.copy(ingredients = state.ingredients.map { if (it.id == id) it.copy(text = text) else it })
    }

    fun addIngredient() = _state.update {
        it.copy(ingredients = it.ingredients + IngredientField(id = ++idSeq, text = ""))
    }

    fun removeIngredient(id: Long) = _state.update { state ->
        state.copy(ingredients = state.ingredients.filterNot { it.id == id })
    }

    fun onStepChange(id: Long, text: String) = _state.update { state ->
        state.copy(steps = state.steps.map { if (it.id == id) it.copy(text = text) else it })
    }

    fun addStep() = _state.update {
        it.copy(steps = it.steps + StepField(id = ++idSeq, text = ""))
    }

    fun removeStep(id: Long) = _state.update { state ->
        state.copy(steps = state.steps.filterNot { it.id == id })
    }

    /**
     * True when Back should ask for confirmation: any field has content, or a
     * recipe was already created on the server (leaving would strand it).
     */
    fun hasUnsavedInput(): Boolean = with(_state.value) {
        name.isNotBlank() || description.isNotBlank() || servings.isNotBlank() ||
            ingredients.any { it.text.isNotBlank() } || steps.any { it.text.isNotBlank() } ||
            createdSlug != null
    }

    /**
     * Validates the form, then runs the create → PATCH flow once. Re-entrant calls
     * while saving are ignored; a call after a failed PATCH reuses the remembered
     * slug and only repeats the PATCH.
     */
    fun save() {
        if (saving) return
        val current = _state.value
        val name = current.name.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        val rawServings = current.servings.trim().replace(',', '.')
        val servings = if (rawServings.isEmpty()) null else rawServings.toDoubleOrNull()
        if (rawServings.isNotEmpty() && (servings == null || !servings.isFinite() || servings <= 0.0)) {
            _state.update { it.copy(servingsError = true) }
            return
        }

        val draft = RecipeDraft(
            name = name,
            description = current.description.trim().ifEmpty { null },
            servings = servings,
            ingredients = current.ingredients.map { it.text.trim() }.filter { it.isNotEmpty() },
            instructions = current.steps.map { it.text.trim() }.filter { it.isNotEmpty() },
        )

        saving = true
        _state.update {
            it.copy(
                phase = RecipeCreatePhase.SAVING,
                nameError = false,
                servingsError = false,
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                val slug = createdSlug ?: repository.createRecipe(name).also { createdSlug = it }
                val detail = repository.updateRecipe(slug, draft)
                // The PATCH response is authoritative: Mealie may have changed the slug.
                createdSlug = detail.slug
                saving = false
                _state.update { it.copy(phase = RecipeCreatePhase.SUCCESS, createdSlug = detail.slug) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                saving = false
                _state.update {
                    // Keep the created slug visible so the screen can explain that the
                    // recipe exists but the changes did not save, and the retry PATCHes it.
                    it.copy(phase = RecipeCreatePhase.ERROR, error = e, createdSlug = createdSlug)
                }
            }
        }
    }

    /** Clears the form for a fresh create; called when a new create is started. */
    fun reset() {
        createdSlug = null
        saving = false
        idSeq = 0L
        _state.value = RecipeCreateUiState()
    }
}
