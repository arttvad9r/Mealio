package com.arttvad9r.mealio.ui.screens.recipecreate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.ui.errorMessage
import com.arttvad9r.mealio.ui.theme.IconSize
import com.arttvad9r.mealio.ui.theme.Radius
import com.arttvad9r.mealio.ui.theme.Space
import androidx.compose.ui.res.stringResource

/**
 * Wires [RecipeCreateViewModel] to the screen: the discard guard on Back, and the
 * one navigation event (a successful save). Kept apart from [RecipeCreateScreen]
 * so the screen itself stays a pure function of its state.
 */
@Composable
fun RecipeCreateRoute(
    viewModel: RecipeCreateViewModel,
    onExit: () -> Unit,
    onCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDiscard by rememberSaveable { mutableStateOf(false) }

    val attemptExit: () -> Unit = {
        if (viewModel.hasUnsavedInput()) showDiscard = true else onExit()
    }

    LaunchedEffect(state.phase, state.createdSlug) {
        val slug = state.createdSlug
        if (state.phase == RecipeCreatePhase.SUCCESS && slug != null) onCreated(slug)
    }

    BackHandler(enabled = !showDiscard) { attemptExit() }

    RecipeCreateScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onServingsChange = viewModel::onServingsChange,
        onIngredientChange = viewModel::onIngredientChange,
        onAddIngredient = viewModel::addIngredient,
        onRemoveIngredient = viewModel::removeIngredient,
        onStepChange = viewModel::onStepChange,
        onAddStep = viewModel::addStep,
        onRemoveStep = viewModel::removeStep,
        onSave = viewModel::save,
        onBack = attemptExit,
        modifier = modifier,
    )

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.recipe_create_discard_title)) },
            text = { Text(stringResource(R.string.recipe_create_discard_text)) },
            confirmButton = {
                TextButton(onClick = onExit) {
                    Text(stringResource(R.string.recipe_create_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) {
                    Text(stringResource(R.string.recipe_create_discard_keep))
                }
            },
        )
    }
}

/** Manual recipe creation: name, description, servings, ingredient lines, steps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeCreateScreen(
    state: RecipeCreateUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onServingsChange: (String) -> Unit,
    onIngredientChange: (Long, String) -> Unit,
    onAddIngredient: () -> Unit,
    onRemoveIngredient: (Long) -> Unit,
    onStepChange: (Long, String) -> Unit,
    onAddStep: () -> Unit,
    onRemoveStep: (Long) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val saving = state.phase == RecipeCreatePhase.SAVING
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipe_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.recipe_create_name_label)) },
                singleLine = true,
                isError = state.nameError,
                supportingText = if (state.nameError) {
                    { Text(stringResource(R.string.recipe_create_name_required)) }
                } else {
                    null
                },
                shape = Radius.field,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.recipe_create_description_label)) },
                minLines = 3,
                maxLines = 6,
                shape = Radius.field,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.servings,
                onValueChange = onServingsChange,
                label = { Text(stringResource(R.string.recipe_create_servings_label)) },
                singleLine = true,
                isError = state.servingsError,
                supportingText = if (state.servingsError) {
                    { Text(stringResource(R.string.recipe_create_servings_error)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = Radius.field,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(stringResource(R.string.recipe_create_ingredients_title))
            state.ingredients.forEach { field ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = field.text,
                        onValueChange = { onIngredientChange(field.id, it) },
                        placeholder = { Text(stringResource(R.string.recipe_create_ingredient_hint)) },
                        maxLines = 3,
                        shape = Radius.field,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemoveIngredient(field.id) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.recipe_create_remove_ingredient),
                            modifier = Modifier.size(IconSize.action),
                        )
                    }
                }
            }
            AddRowButton(
                text = stringResource(R.string.recipe_create_add_ingredient),
                onClick = onAddIngredient,
            )

            SectionTitle(stringResource(R.string.recipe_create_steps_title))
            state.steps.forEachIndexed { index, field ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(24.dp),
                    )
                    OutlinedTextField(
                        value = field.text,
                        onValueChange = { onStepChange(field.id, it) },
                        placeholder = { Text(stringResource(R.string.recipe_create_step_hint)) },
                        maxLines = 5,
                        shape = Radius.field,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemoveStep(field.id) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.recipe_create_remove_step),
                            modifier = Modifier.size(IconSize.action),
                        )
                    }
                }
            }
            AddRowButton(
                text = stringResource(R.string.recipe_create_add_step),
                onClick = onAddStep,
            )

            val error = state.error
            if (state.phase == RecipeCreatePhase.ERROR && error != null) {
                Text(
                    text = errorMessage(error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.createdSlug != null) {
                    Text(
                        text = stringResource(R.string.recipe_create_partial_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Button(
                onClick = onSave,
                enabled = !saving,
                shape = Radius.field,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(IconSize.action),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(Space.s))
                }
                Text(stringResource(R.string.recipe_create_save))
            }
            Spacer(Modifier.height(Space.l))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AddRowButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(IconSize.action),
        )
        Spacer(Modifier.width(Space.s))
        Text(text)
    }
}
