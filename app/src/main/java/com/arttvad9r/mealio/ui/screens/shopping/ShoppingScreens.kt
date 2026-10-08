package com.arttvad9r.mealio.ui.screens.shopping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.domain.format.QuantityFormatter
import com.arttvad9r.mealio.domain.model.ShoppingItem
import com.arttvad9r.mealio.domain.model.ShoppingListDetail
import com.arttvad9r.mealio.ui.components.CenteredLoading
import com.arttvad9r.mealio.ui.components.EmptyState
import com.arttvad9r.mealio.ui.components.ErrorState
import com.arttvad9r.mealio.ui.components.MealioCard
import com.arttvad9r.mealio.ui.errorMessage
import com.arttvad9r.mealio.ui.theme.IconSize
import com.arttvad9r.mealio.ui.theme.Radius
import com.arttvad9r.mealio.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListsScreen(
    state: ShoppingUiState,
    onOpenList: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> CenteredLoading(modifier)

        state.error != null && state.lists.isEmpty() -> ErrorState(
            message = errorMessage(state.error),
            onRetry = onRefresh,
            modifier = modifier,
        )

        state.lists.isEmpty() -> EmptyState(
            title = stringResource(R.string.shopping_empty_title),
            subtitle = stringResource(R.string.shopping_empty_subtitle),
            modifier = modifier,
        )

        else -> PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.screen,
                    end = Space.screen,
                    top = Space.m,
                    bottom = Space.l * 2,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                items(state.lists, key = { it.id }) { list ->
                    MealioCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenList(list.id) },
                    ) {
                        Row(
                            modifier = Modifier.padding(Space.l),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.m),
                        ) {
                            Surface(
                                shape = Radius.field,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Icon(
                                    Icons.Filled.ShoppingCart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(Space.s).size(IconSize.action),
                                )
                            }
                            Text(
                                text = list.name.ifBlank { stringResource(R.string.shopping_untitled) },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShoppingListDetailScreen(
    state: ShoppingListDetailUiState,
    onToggle: (ShoppingItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> CenteredLoading(modifier)

        state.error != null && state.list == null -> ErrorState(
            message = errorMessage(state.error),
            onRetry = onRetry,
            modifier = modifier,
        )

        state.list != null -> {
            val list = state.list
            val remaining = list.items.count { !it.checked }
            if (list.items.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.shopping_list_empty_title),
                    subtitle = stringResource(R.string.shopping_list_empty_subtitle),
                    modifier = modifier,
                )
            } else {
                Column(modifier = modifier.fillMaxSize()) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.shopping_remaining,
                            remaining,
                            remaining,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.s),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Space.screen,
                            end = Space.screen,
                            bottom = Space.l * 2,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        items(list.items, key = { it.id }) { item ->
                            ShoppingItemRow(item = item, onToggle = { onToggle(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingItem,
    onToggle: () -> Unit,
) {
    MealioCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(Space.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            CheckCircle(checked = item.checked)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.foodName
                        ?: item.display?.takeIf { it.isNotBlank() }
                        ?: item.note
                        ?: stringResource(R.string.recipe_ingredient_unnamed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (item.checked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.note?.takeIf { it.isNotBlank() && item.foodName != null }?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val amount = QuantityFormatter.amount(item.quantity, item.unit)
            if (amount != null) {
                Spacer(Modifier.width(Space.xs))
                Text(
                    text = amount,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CheckCircle(checked: Boolean) {
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (checked) {
            null
        } else {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
        },
        modifier = Modifier.size(26.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (checked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.shopping_item_checked),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
