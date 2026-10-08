package com.arttvad9r.mealio.ui.screens.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.data.remote.MealieImageUrl
import com.arttvad9r.mealio.domain.format.DurationFormatter
import com.arttvad9r.mealio.domain.model.RecipeSummary
import com.arttvad9r.mealio.ui.components.EmptyState
import com.arttvad9r.mealio.ui.components.ErrorState
import com.arttvad9r.mealio.ui.components.MealioCard
import com.arttvad9r.mealio.ui.components.CenteredLoading
import com.arttvad9r.mealio.ui.components.CompactSearchField
import com.arttvad9r.mealio.ui.errorMessage
import com.arttvad9r.mealio.ui.theme.IconSize
import com.arttvad9r.mealio.ui.theme.Radius
import com.arttvad9r.mealio.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    state: RecipesUiState,
    serverUrl: String,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onOpenRecipe: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        CompactSearchField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.recipes_search_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.screen, end = Space.screen, top = Space.s, bottom = Space.xs),
        )

        if (state.categories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.screen),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCategorySlug == null,
                        onClick = { onCategorySelected(null) },
                        shape = Radius.field,
                        label = { Text(stringResource(R.string.recipes_category_all)) },
                    )
                }
                items(state.categories, key = { it.second }) { (name, slug) ->
                    FilterChip(
                        selected = state.selectedCategorySlug == slug,
                        onClick = { onCategorySelected(slug) },
                        shape = Radius.field,
                        label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            Spacer(Modifier.height(Space.s))
        }

        when {
            state.isLoading -> CenteredLoading()

            state.error != null && state.recipes.isEmpty() -> ErrorState(
                message = errorMessage(state.error),
                onRetry = onRefresh,
            )

            state.recipes.isEmpty() -> EmptyState(
                title = stringResource(R.string.recipes_empty_title),
                subtitle = stringResource(R.string.recipes_empty_subtitle),
            )

            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Space.screen,
                        end = Space.screen,
                        bottom = Space.l * 2,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    items(state.recipes, key = { it.slug }) { recipe ->
                        RecipeCard(
                            recipe = recipe,
                            serverUrl = serverUrl,
                            onClick = { onOpenRecipe(recipe.slug) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: RecipeSummary,
    serverUrl: String,
    onClick: () -> Unit,
) {
    val imageUrl = MealieImageUrl.recipeImage(serverUrl, recipe.uuid, recipe.imageKey)

    MealioCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(Space.m)) {
            RecipeThumbnail(imageUrl = imageUrl)
            Spacer(Modifier.width(Space.m))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val firstCategory = recipe.categories.firstOrNull()
                if (firstCategory != null || recipe.totalTimeIso != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (firstCategory != null) {
                            Text(
                                text = firstCategory,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        recipe.totalTimeIso?.let { time ->
                            DurationFormatter.format(time, LocalContext.current)?.let { human ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(IconSize.caption),
                                    )
                                    Text(
                                        text = human,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeThumbnail(imageUrl: String?) {
    if (imageUrl == null) {
        Surface(
            shape = Radius.field,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.status),
                )
            }
        }
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(Radius.field),
        )
    }
}
