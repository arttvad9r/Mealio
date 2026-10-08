package com.arttvad9r.mealio.ui.screens.recipedetail

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.data.remote.MealieImageUrl
import com.arttvad9r.mealio.domain.format.DurationFormatter
import com.arttvad9r.mealio.domain.format.NutritionFormatter
import com.arttvad9r.mealio.domain.format.QuantityFormatter
import com.arttvad9r.mealio.domain.model.Nutrition
import com.arttvad9r.mealio.domain.model.ShoppingListSummary
import com.arttvad9r.mealio.ui.components.CenteredLoading
import com.arttvad9r.mealio.ui.components.ErrorState
import com.arttvad9r.mealio.ui.components.MealioCard
import com.arttvad9r.mealio.ui.components.NutritionMetric
import com.arttvad9r.mealio.ui.components.ServingStepper
import com.arttvad9r.mealio.ui.errorMessage
import com.arttvad9r.mealio.ui.theme.IconSize
import com.arttvad9r.mealio.ui.theme.Radius
import com.arttvad9r.mealio.ui.theme.Space

@Composable
fun RecipeDetailScreen(
    state: RecipeDetailUiState,
    serverUrl: String,
    shoppingLists: List<ShoppingListSummary>,
    canAddToList: Boolean,
    onServingsDecrease: () -> Unit,
    onServingsIncrease: () -> Unit,
    onTabSelected: (RecipeTab) -> Unit,
    onRetry: () -> Unit,
    onAddToList: (String) -> Unit,
    addToListMessage: String?,
    addToListIsError: Boolean,
    onDismissAddToListMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> CenteredLoading(modifier)
        state.error != null -> ErrorState(message = errorMessage(state.error), onRetry = onRetry, modifier = modifier)
        state.recipe != null -> {
            val recipe = state.recipe
            val scaled = remember(state.recipe, state.servings, state.baseServings) {
                val factor = state.baseServings?.takeIf { it > 0.0 }?.let { state.servings / it } ?: 1.0
                recipe.ingredients.map { it.scale(factor) }
            }
            val imageUrl = MealieImageUrl.recipeImage(serverUrl, recipe.uuid, recipe.imageKey)

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Space.l * 2),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                if (imageUrl != null) {
                    item {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(220.dp).padding(horizontal = Space.screen).clip(Radius.card),
                        )
                    }
                }
                item {
                    Column(modifier = Modifier.padding(horizontal = Space.screen)) {
                        Text(recipe.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                        if (recipe.categories.isNotEmpty() || recipe.tags.isNotEmpty()) {
                            Spacer(Modifier.height(Space.xs))
                            Text((recipe.categories + recipe.tags).joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        recipe.totalTimeIso?.let { DurationFormatter.format(it, LocalContext.current) }?.let { human ->
                            Spacer(Modifier.height(Space.xs))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.caption))
                                Text(stringResource(R.string.recipe_time_label, human), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (recipe.servings != null || recipe.nutrition.hasAny) {
                    item {
                        MealioCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screen)) {
                            Column(modifier = Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.l)) {
                                if (state.baseServings != null) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.recipe_servings_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                        ServingStepper(state.servings, onServingsDecrease, onServingsIncrease, state.servings > 1.0)
                                    }
                                }
                                if (recipe.nutrition.hasAny) NutritionSection(recipe.nutrition)
                            }
                        }
                    }
                }
                item {
                    FilledTonalButton(
                        onClick = { if (shoppingLists.isNotEmpty()) onAddToList(shoppingLists.first().id) },
                        enabled = recipe.uuid != null && shoppingLists.isNotEmpty() && canAddToList,
                        shape = Radius.field,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screen),
                    ) {
                        Icon(Icons.Filled.PlaylistAdd, null, modifier = Modifier.size(IconSize.action))
                        Spacer(Modifier.width(Space.s))
                        Text(stringResource(R.string.recipe_add_to_list))
                    }
                }
                item {
                    TabRow(
                        selectedTabIndex = state.tab.ordinal,
                        containerColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.padding(horizontal = Space.screen),
                    ) {
                        Tab(selected = state.tab == RecipeTab.INGREDIENTS, onClick = { onTabSelected(RecipeTab.INGREDIENTS) }, text = { Text(stringResource(R.string.recipe_tab_ingredients)) })
                        Tab(selected = state.tab == RecipeTab.INSTRUCTIONS, onClick = { onTabSelected(RecipeTab.INSTRUCTIONS) }, text = { Text(stringResource(R.string.recipe_tab_instructions)) })
                    }
                }
                if (state.tab == RecipeTab.INGREDIENTS) {
                    if (scaled.isEmpty()) item { Text(stringResource(R.string.recipe_no_ingredients), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.screen)) }
                    else itemsIndexed(scaled) { _, ing -> IngredientRow(ing, Modifier.padding(horizontal = Space.screen)) }
                } else {
                    if (recipe.instructions.isEmpty()) item { Text(stringResource(R.string.recipe_no_instructions), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.screen)) }
                    else itemsIndexed(recipe.instructions) { index, step -> InstructionRow(index + 1, step, Modifier.padding(horizontal = Space.screen)) }
                }
            }
        }
    }

    if (addToListMessage != null) {
        var showDialog by remember { mutableStateOf(true) }
        if (showDialog) AlertDialog(
            onDismissRequest = { showDialog = false; onDismissAddToListMessage() },
            shape = Radius.card,
            title = { Text(stringResource(if (addToListIsError) R.string.recipe_add_to_list_error_title else R.string.recipe_add_to_list_done_title)) },
            text = { Text(addToListMessage) },
            confirmButton = { TextButton(onClick = { showDialog = false; onDismissAddToListMessage() }) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}

@Composable
private fun NutritionSection(nutrition: Nutrition) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(stringResource(R.string.recipe_nutrition_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            NutritionMetric(stringResource(R.string.nutrition_calories), NutritionFormatter.format(nutrition.calories, NutritionFormatter.Metric.CALORIES, LocalContext.current))
            NutritionMetric(stringResource(R.string.nutrition_protein), NutritionFormatter.format(nutrition.protein, NutritionFormatter.Metric.PROTEIN, LocalContext.current))
            NutritionMetric(stringResource(R.string.nutrition_fat), NutritionFormatter.format(nutrition.fat, NutritionFormatter.Metric.FAT, LocalContext.current))
            NutritionMetric(stringResource(R.string.nutrition_carbs), NutritionFormatter.format(nutrition.carbs, NutritionFormatter.Metric.CARBS, LocalContext.current))
        }
    }
}

@Composable
private fun IngredientRow(ingredient: ScaledIngredient, modifier: Modifier = Modifier) {
    val amount = QuantityFormatter.amount(ingredient.quantity, ingredient.unit, LocalContext.current)
    Row(modifier = modifier.fillMaxWidth().padding(vertical = Space.s), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(ingredient.name.ifBlank { ingredient.note ?: stringResource(R.string.recipe_ingredient_unnamed) }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        if (amount != null) { Spacer(Modifier.width(Space.m)); Text(amount, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun InstructionRow(index: Int, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Surface(shape = Radius.field, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(index.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}
