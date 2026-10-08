package com.arttvad9r.mealio.ui.screens.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.domain.format.QuantityFormatter
import com.arttvad9r.mealio.domain.model.RecipeSummary
import com.arttvad9r.mealio.domain.today.DailyNutrition
import com.arttvad9r.mealio.domain.today.NutritionCalculator
import com.arttvad9r.mealio.domain.today.TodaySlot
import com.arttvad9r.mealio.domain.today.TodayTargets
import com.arttvad9r.mealio.ui.components.CenteredLoading
import com.arttvad9r.mealio.ui.components.CompactSearchField
import com.arttvad9r.mealio.ui.components.ErrorState
import com.arttvad9r.mealio.ui.components.MealioCard
import com.arttvad9r.mealio.ui.errorMessage
import com.arttvad9r.mealio.ui.theme.IconSize
import com.arttvad9r.mealio.ui.theme.Radius
import com.arttvad9r.mealio.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    candidatesFor: (TodaySlot) -> List<RecipeSummary>,
    onPick: (TodaySlot, String) -> Unit,
    onRemove: (TodaySlot) -> Unit,
    onServingsDecrease: (TodaySlot) -> Unit,
    onServingsIncrease: (TodaySlot) -> Unit,
    onRetryDetail: (TodaySlot) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayEmpty = state.slots.all { it.selection == null }
    if (state.isLoading && state.summaries.isEmpty() && dayEmpty) {
        CenteredLoading(modifier)
        return
    }

    var selectorSlot by remember { mutableStateOf<TodaySlot?>(null) }

    val breakfastHeader = stringResource(R.string.today_section_breakfast)
    val mainHeader = stringResource(R.string.today_section_main)
    val snackHeader = stringResource(R.string.today_section_snack)
    val extraHeader = stringResource(R.string.today_section_extra)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screen,
            end = Space.screen,
            top = Space.s,
            bottom = Space.l * 2,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        if (state.loadError != null) {
            item(key = "load-error") {
                ErrorState(message = errorMessage(state.loadError), onRetry = onRetry)
            }
        }

        item(key = "summary") {
            DailySummaryCard(totals = state.totals)
        }

        slotSection(
            header = breakfastHeader,
            slots = listOf(TodaySlot.BREAKFAST),
            state = state,
            onEmptyClick = { selectorSlot = it },
            onRemove = onRemove,
            onDecrease = onServingsDecrease,
            onIncrease = onServingsIncrease,
            onRetry = onRetryDetail,
        )
        slotSection(
            header = mainHeader,
            slots = listOf(TodaySlot.MAIN, TodaySlot.SIDE, TodaySlot.VEGETABLES),
            state = state,
            onEmptyClick = { selectorSlot = it },
            onRemove = onRemove,
            onDecrease = onServingsDecrease,
            onIncrease = onServingsIncrease,
            onRetry = onRetryDetail,
        )
        slotSection(
            header = snackHeader,
            slots = listOf(TodaySlot.SNACK),
            state = state,
            onEmptyClick = { selectorSlot = it },
            onRemove = onRemove,
            onDecrease = onServingsDecrease,
            onIncrease = onServingsIncrease,
            onRetry = onRetryDetail,
        )
        slotSection(
            header = extraHeader,
            slots = listOf(TodaySlot.EXTRA),
            state = state,
            onEmptyClick = { selectorSlot = it },
            onRemove = onRemove,
            onDecrease = onServingsDecrease,
            onIncrease = onServingsIncrease,
            onRetry = onRetryDetail,
        )
    }

    selectorSlot?.let { slot ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectorSlot = null },
            sheetState = sheetState,
            shape = Radius.sheet,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            RecipeSelectorSheet(
                title = slotLabel(slot),
                candidates = candidatesFor(slot),
                onSelect = { slug ->
                    onPick(slot, slug)
                    selectorSlot = null
                },
            )
        }
    }
}

/** A section header followed by its slot cards, inside the main list scope. */
private fun LazyListScope.slotSection(
    header: String,
    slots: List<TodaySlot>,
    state: TodayUiState,
    onEmptyClick: (TodaySlot) -> Unit,
    onRemove: (TodaySlot) -> Unit,
    onDecrease: (TodaySlot) -> Unit,
    onIncrease: (TodaySlot) -> Unit,
    onRetry: (TodaySlot) -> Unit,
) {
    item(key = "header-$header") {
        Text(
            text = header,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs, start = Space.xs),
        )
    }
    slots.forEach { slot ->
        item(key = slot.name) {
            val ui = state.slots.firstOrNull { it.slot == slot } ?: TodaySlotUi(slot = slot)
            SlotCard(
                ui = ui,
                onEmptyClick = { onEmptyClick(slot) },
                onRemove = { onRemove(slot) },
                onDecrease = { onDecrease(slot) },
                onIncrease = { onIncrease(slot) },
                onRetry = { onRetry(slot) },
            )
        }
    }
}

@Composable
private fun DailySummaryCard(totals: DailyNutrition) {
    val target = TodayTargets.CALORIES
    val progress = (totals.calories / target).coerceIn(0.0, 1.0).toFloat()

    MealioCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text(
                text = stringResource(
                    R.string.today_calories_value,
                    QuantityFormatter.formatNumber(totals.calories),
                    QuantityFormatter.formatNumber(target),
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                // Material3 draws a primary-coloured stop dot at the end of the
                // track by default (visible as a stray green dot at 0/partial
                // progress). Suppress it: a plain determinate bar only.
                drawStopIndicator = {},
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.l),
            ) {
                MacroText(stringResource(R.string.today_macro_short_b, QuantityFormatter.formatNumber(totals.protein)))
                MacroText(stringResource(R.string.today_macro_short_zh, QuantityFormatter.formatNumber(totals.fat)))
                MacroText(stringResource(R.string.today_macro_short_u, QuantityFormatter.formatNumber(totals.carbs)))
            }
        }
    }
}

@Composable
private fun MacroText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SlotCard(
    ui: TodaySlotUi,
    onEmptyClick: () -> Unit,
    onRemove: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onRetry: () -> Unit,
) {
    val selection = ui.selection
    if (selection == null) {
        EmptySlotCard(label = slotLabel(ui.slot), onClick = onEmptyClick)
        return
    }

    val detail = ui.detail
    MealioCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail?.name ?: selection.slug,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val kcal = detail?.let {
                        NutritionCalculator.perServing(it.nutrition.calories, it.servings)
                            ?.times(selection.servings)
                    }
                    if (kcal != null) {
                        Text(
                            text = stringResource(
                                R.string.today_kcal_value,
                                QuantityFormatter.formatNumber(kcal),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (detail != null) {
                        Text(
                            text = stringResource(R.string.today_slot_no_nutrition),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                when {
                    detail != null -> CompactServingStepper(
                        servings = selection.servings,
                        onDecrease = onDecrease,
                        onIncrease = onIncrease,
                        decreaseEnabled = selection.servings > 1.0,
                    )

                    ui.error == null -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.today_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.action),
                    )
                }
            }

            // Error is the only case that needs a second line: it cannot share
            // the title row without squeezing the message.
            if (detail == null && ui.error != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    Text(
                        text = errorMessage(ui.error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.today_slot_retry))
                    }
                }
            }
        }
    }
}

/**
 * Dense "−  N  +" control for the Today slot card. Plain [IconButton]s: they
 * keep the 48dp touch target but draw no outlined square, so the filled card
 * stays far shorter than with the shared outlined stepper.
 */
@Composable
private fun CompactServingStepper(
    servings: Double,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseEnabled: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDecrease, enabled = decreaseEnabled) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = stringResource(R.string.recipe_servings_decrease),
                modifier = Modifier.size(IconSize.action),
            )
        }
        Text(
            text = QuantityFormatter.formatNumber(servings),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(34.dp),
        )
        IconButton(onClick = onIncrease) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.recipe_servings_increase),
                modifier = Modifier.size(IconSize.action),
            )
        }
    }
}

@Composable
private fun EmptySlotCard(label: String, onClick: () -> Unit) {
    MealioCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.today_pick),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.action),
            )
        }
    }
}

@Composable
private fun RecipeSelectorSheet(
    title: String,
    candidates: List<RecipeSummary>,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(candidates, query) {
        if (query.isBlank()) candidates
        else candidates.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Space.screen),
        )
        Spacer(Modifier.height(Space.s))
        CompactSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.today_selector_search_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screen),
        )
        Spacer(Modifier.height(Space.s))

        if (filtered.isEmpty()) {
            Text(
                text = stringResource(R.string.today_selector_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Space.l),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(
                    start = Space.screen,
                    end = Space.screen,
                    bottom = Space.l,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                items(filtered, key = { it.slug }) { recipe ->
                    SelectorRow(recipe = recipe, onClick = { onSelect(recipe.slug) })
                }
            }
        }
    }
}

@Composable
private fun SelectorRow(recipe: RecipeSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Surface(
            shape = Radius.field,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.action),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val kcal = NutritionCalculator.perServing(recipe.calories, recipe.servings)
            val subtitle = kcal?.let {
                stringResource(R.string.today_kcal_value, QuantityFormatter.formatNumber(it))
            } ?: recipe.categories.firstOrNull()
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun slotLabel(slot: TodaySlot): String = stringResource(
    when (slot) {
        TodaySlot.BREAKFAST -> R.string.today_slot_breakfast
        TodaySlot.MAIN -> R.string.today_slot_main
        TodaySlot.SIDE -> R.string.today_slot_side
        TodaySlot.VEGETABLES -> R.string.today_slot_vegetables
        TodaySlot.SNACK -> R.string.today_slot_snack
        TodaySlot.EXTRA -> R.string.today_slot_extra
    },
)
