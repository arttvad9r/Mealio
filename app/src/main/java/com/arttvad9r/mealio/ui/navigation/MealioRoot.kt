package com.arttvad9r.mealio.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arttvad9r.mealio.BuildConfig
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.data.AppContainer
import com.arttvad9r.mealio.domain.model.ShoppingListSummary
import com.arttvad9r.mealio.ui.errorMessage
import com.arttvad9r.mealio.ui.screens.ConnectScreen
import com.arttvad9r.mealio.ui.screens.SettingsScreen
import com.arttvad9r.mealio.ui.screens.recipes.RecipesScreen
import com.arttvad9r.mealio.ui.screens.recipes.RecipesViewModel
import com.arttvad9r.mealio.ui.screens.recipedetail.RecipeDetailScreen
import com.arttvad9r.mealio.ui.screens.recipedetail.RecipeDetailViewModel
import com.arttvad9r.mealio.ui.screens.shopping.ShoppingListDetailScreen
import com.arttvad9r.mealio.ui.screens.shopping.ShoppingListDetailViewModel
import com.arttvad9r.mealio.ui.screens.shopping.ShoppingListsScreen
import com.arttvad9r.mealio.ui.screens.shopping.ShoppingViewModel
import com.arttvad9r.mealio.data.remote.MealioException
import com.arttvad9r.mealio.data.remote.ErrorKind
import kotlinx.coroutines.launch

private enum class Tab(val labelRes: Int, val icon: ImageVector) {
    RECIPES(R.string.nav_recipes, Icons.Filled.Restaurant),
    SHOPPING(R.string.nav_shopping, Icons.Filled.ShoppingCart),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun MealioRoot(container: AppContainer) {
    val account by container.settingsStore.accountFlow.collectAsStateWithLifecycle(
        initialValue = container.settingsStore.readAccount(),
    )

    if (account == null) {
        ConnectRoute(container)
    } else {
        AuthedRoot(container)
    }
}

@Composable
private fun ConnectRoute(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ConnectScreen(
        url = url,
        token = token,
        isChecking = isChecking,
        errorMessage = error,
        onUrlChange = { url = it; error = null },
        onTokenChange = { token = it; error = null },
        onConnect = connect@{
            if (isChecking) return@connect
            isChecking = true
            error = null
            scope.launch {
                runCatching { container.connectionRepository.connect(url, token) }
                    .onFailure { e -> error = errorMessage(context, e) }
                isChecking = false
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthedRoot(container: AppContainer) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.RECIPES.name) }
    var selectedRecipeSlug by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedListId by rememberSaveable { mutableStateOf<String?>(null) }

    val currentTab = Tab.valueOf(tab)
    val account = container.settingsStore.readAccount()
    val serverUrl = account?.serverUrl.orEmpty()

    val recipesViewModel: RecipesViewModel = viewModel(
        key = "recipes",
        factory = viewModelFactory {
            initializer { RecipesViewModel(container.recipeRepository) }
        },
    )
    val recipesState by recipesViewModel.state.collectAsStateWithLifecycle()

    val shoppingViewModel: ShoppingViewModel = viewModel(
        key = "shopping",
        factory = viewModelFactory {
            initializer { ShoppingViewModel(container.shoppingRepository) }
        },
    )
    val shoppingState by shoppingViewModel.state.collectAsStateWithLifecycle()

    val onBack: () -> Unit = {
        when {
            selectedRecipeSlug != null -> selectedRecipeSlug = null
            selectedListId != null -> selectedListId = null
        }
    }
    BackHandler(enabled = selectedRecipeSlug != null || selectedListId != null, onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            selectedRecipeSlug != null -> stringResource(R.string.recipe_title)
                            selectedListId != null -> stringResource(R.string.shopping_list_title)
                            else -> stringResource(R.string.app_name)
                        },
                    )
                },
                navigationIcon = {
                    if (selectedRecipeSlug != null || selectedListId != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = currentTab == entry,
                        onClick = {
                            selectedRecipeSlug = null
                            selectedListId = null
                            tab = entry.name
                        },
                        icon = { Icon(imageVector = entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)

        when {
            selectedRecipeSlug != null -> RecipeDetailRoute(
                container = container,
                slug = selectedRecipeSlug!!,
                serverUrl = serverUrl,
                shoppingLists = shoppingState.lists,
                modifier = contentModifier,
            )

            selectedListId != null -> ShoppingDetailRoute(
                container = container,
                listId = selectedListId!!,
                modifier = contentModifier,
            )

            else -> when (currentTab) {
                Tab.RECIPES -> RecipesScreen(
                    state = recipesState,
                    serverUrl = serverUrl,
                    onQueryChange = recipesViewModel::onQueryChange,
                    onCategorySelected = recipesViewModel::onCategorySelected,
                    onOpenRecipe = { selectedRecipeSlug = it },
                    onRefresh = recipesViewModel::refresh,
                    modifier = contentModifier,
                )

                Tab.SHOPPING -> ShoppingListsScreen(
                    state = shoppingState,
                    onOpenList = { selectedListId = it },
                    onRefresh = shoppingViewModel::refresh,
                    modifier = contentModifier,
                )

                Tab.SETTINGS -> SettingsRoute(
                    container = container,
                    modifier = contentModifier,
                )
            }
        }
    }
}

@Composable
private fun RecipeDetailRoute(
    container: AppContainer,
    slug: String,
    serverUrl: String,
    shoppingLists: List<ShoppingListSummary>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: RecipeDetailViewModel = viewModel(
        key = "recipe-$slug",
        factory = viewModelFactory {
            initializer { RecipeDetailViewModel(container.recipeRepository, slug) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addMessage by remember { mutableStateOf<String?>(null) }
    var addIsError by remember { mutableStateOf(false) }

    RecipeDetailScreen(
        state = state,
        serverUrl = serverUrl,
        shoppingLists = shoppingLists,
        canAddToList = true,
        onServingsDecrease = viewModel::decreaseServings,
        onServingsIncrease = viewModel::increaseServings,
        onTabSelected = viewModel::setTab,
        onRetry = viewModel::load,
        onAddToList = { listId ->
            val uuid = state.recipe?.uuid
            if (uuid != null) {
                scope.launch {
                    runCatching { container.shoppingRepository.addRecipeToList(listId, uuid) }
                        .onSuccess {
                            addIsError = false
                            addMessage = context.getString(R.string.recipe_add_to_list_done_text)
                        }
                        .onFailure {
                            addIsError = true
                            addMessage = errorMessage(context, it)
                        }
                }
            }
        },
        addToListMessage = addMessage,
        addToListIsError = addIsError,
        onDismissAddToListMessage = { addMessage = null },
        modifier = modifier,
    )
}

@Composable
private fun ShoppingDetailRoute(
    container: AppContainer,
    listId: String,
    modifier: Modifier = Modifier,
) {
    val viewModel: ShoppingListDetailViewModel = viewModel(
        key = "list-$listId",
        factory = viewModelFactory {
            initializer { ShoppingListDetailViewModel(container.shoppingRepository, listId) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShoppingListDetailScreen(
        state = state,
        onToggle = viewModel::toggle,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun SettingsRoute(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val themeMode by container.settingsStore.themeMode.collectAsStateWithLifecycle()
    SettingsScreen(
        account = container.settingsStore.readAccount(),
        themeMode = themeMode,
        appVersion = BuildConfig.VERSION_NAME,
        onThemeModeChange = { mode -> container.settingsStore.setThemeMode(mode) },
        onDisconnect = { container.connectionRepository.disconnect() },
        modifier = modifier,
    )
}
