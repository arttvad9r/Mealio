package com.arttvad9r.mealio.ui.screens.recipecreate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arttvad9r.mealio.data.repository.RecipeWriteSource
import com.arttvad9r.mealio.domain.model.IngredientRef
import com.arttvad9r.mealio.domain.model.RecipeDraft
import com.arttvad9r.mealio.domain.model.RecipeIngredientDraft
import com.arttvad9r.mealio.domain.recipe.prepareIngredientDrafts
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
 *
 * Before the first POST the ingredients are sent to Mealie's server-side parser
 * (best effort, strictly non-fatal) and turned into structured write ingredients,
 * with a note-only fallback per line. The prepared draft is remembered for the
 * current save session so a retry never re-parses and never re-POSTs.
 */
class RecipeCreateViewModel(private val repository: RecipeWriteSource) : ViewModel() {

    private val _state = MutableStateFlow(RecipeCreateUiState())
    val state: StateFlow<RecipeCreateUiState> = _state.asStateFlow()

    /** Slug of the recipe created in this session, kept across a failed PATCH. */
    private var createdSlug: String? = null

    /**
     * The write-ready draft of the current save session, built after parsing. Kept so
     * a retry after a failed PATCH reuses the parsed ingredients; dropped by any edit.
     */
    private var preparedDraft: RecipeDraft? = null

    /** True while a save is in flight: the one guard against a duplicate POST. */
    private var saving = false

    /** Monotonic row ids; only unique within the current list is required. */
    private var idSeq = 0L

    fun onNameChange(value: String) {
        _state.update { it.copy(name = value, nameError = false) }
        invalidatePrepared()
    }

    fun onDescriptionChange(value: String) {
        _state.update { it.copy(description = value) }
        invalidatePrepared()
    }

    fun onServingsChange(value: String) {
        _state.update { it.copy(servings = value, servingsError = false) }
        invalidatePrepared()
    }

    fun onIngredientChange(id: Long, text: String) {
        _state.update { state ->
            state.copy(ingredients = state.ingredients.map { if (it.id == id) it.copy(text = text) else it })
        }
        invalidatePrepared()
    }

    fun addIngredient() {
        _state.update { it.copy(ingredients = it.ingredients + IngredientField(id = ++idSeq, text = "")) }
        invalidatePrepared()
    }

    fun removeIngredient(id: Long) {
        _state.update { state -> state.copy(ingredients = state.ingredients.filterNot { it.id == id }) }
        invalidatePrepared()
    }

    fun onStepChange(id: Long, text: String) {
        _state.update { state ->
            state.copy(steps = state.steps.map { if (it.id == id) it.copy(text = text) else it })
        }
        invalidatePrepared()
    }

    fun addStep() {
        _state.update { it.copy(steps = it.steps + StepField(id = ++idSeq, text = "")) }
        invalidatePrepared()
    }

    fun removeStep(id: Long) {
        _state.update { state -> state.copy(steps = state.steps.filterNot { it.id == id }) }
        invalidatePrepared()
    }

    /**
     * Any edit to the form drops the prepared draft, so the next save re-parses the
     * current lines. This keeps "what is on screen" and "what gets written" in sync:
     * a retry without edits reuses the prepared draft (and skips the POST).
     */
    private fun invalidatePrepared() {
        preparedDraft = null
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

        val lines = current.ingredients.map { it.text.trim() }.filter { it.isNotEmpty() }
        val instructions = current.steps.map { it.text.trim() }.filter { it.isNotEmpty() }

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
                // Parse before the first POST so the PATCH can carry structured
                // ingredients. Reuses the prepared draft on a retry (no re-parse, no
                // second POST).
                val draft = preparedDraft
                    ?: prepareDraft(name, current.description, servings, lines, instructions)
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

    /**
     * Builds the write-ready draft: parses the ingredient lines server-side (best
     * effort — any failure means "no structure", never an error the user sees), resolves
     * each parser suggestion against the entities that already exist on the server and
     * converts each line into a structured ingredient or a note-only fallback. The
     * result is remembered for the rest of the save session.
     *
     * The parser is skipped entirely when there are no ingredient lines, matching the
     * "no ingredients, no parser call" rule.
     */
    private suspend fun prepareDraft(
        name: String,
        description: String,
        servings: Double?,
        lines: List<String>,
        instructions: List<String>,
    ): RecipeDraft {
        val parsed = if (lines.isEmpty()) {
            emptyList()
        } else {
            try {
                repository.parseIngredients(lines)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Strictly best effort: the create flow proceeds note-only.
                null
            }
        }
        val draft = RecipeDraft(
            name = name,
            description = description.trim().ifEmpty { null },
            servings = servings,
            ingredients = resolveIngredientRefs(prepareIngredientDrafts(lines, parsed)),
            instructions = instructions,
        )
        preparedDraft = draft
        return draft
    }

    /**
     * Exchanges the parser's unit/food names for ids of entities that already exist on
     * the server, because Mealie's PATCH accepts those relations by id only (a name-only
     * reference is answered with HTTP 500 — the first physical smoke test). Also best
     * effort: a line whose suggestions cannot be resolved to existing entities is kept
     * whole as the user's original line, so a save never fails on a missing reference.
     */
    private suspend fun resolveIngredientRefs(
        drafts: List<RecipeIngredientDraft>,
    ): List<RecipeIngredientDraft> = drafts.map { draft ->
        if (!draft.needsRefResolution) {
            draft
        } else {
            draft.withResolvedRefs(
                unitRef = draft.unitName?.let { resolveRef(repository::findUnitRef, it) },
                foodRef = draft.foodName?.let { resolveRef(repository::findFoodRef, it) },
            )
        }
    }

    /**
     * Runs one reference lookup, turning any failure into "not found". A lookup that
     * fails (or a name that matches nothing) must degrade the line to its note-only
     * fallback, never fail the save.
     */
    private suspend fun resolveRef(
        lookup: suspend (String) -> IngredientRef?,
        name: String,
    ): IngredientRef? = try {
        lookup(name)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    /** Clears the form for a fresh create; called when a new create is started. */
    fun reset() {
        createdSlug = null
        preparedDraft = null
        saving = false
        idSeq = 0L
        _state.value = RecipeCreateUiState()
    }
}
