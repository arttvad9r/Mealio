package com.arttvad9r.mealio.data

import android.content.Context
import com.arttvad9r.mealio.data.local.SecureTokenStorage
import com.arttvad9r.mealio.data.local.SettingsStore
import com.arttvad9r.mealio.data.remote.MealieApiFactory
import com.arttvad9r.mealio.data.repository.ConnectionRepository
import com.arttvad9r.mealio.data.repository.RecipeRepository
import com.arttvad9r.mealio.data.repository.ShoppingRepository

/**
 * Minimal manual dependency container. Small enough that a DI framework would
 * be unjustified overhead.
 */
class AppContainer(context: Context) {

    val tokenStorage = SecureTokenStorage(context)
    val settingsStore = SettingsStore(context)

    private val apiFactory = MealieApiFactory(
        okHttpClient = MealieApiFactory.defaultOkHttp(),
        tokenProvider = { tokenStorage.getToken() },
    )

    val connectionRepository = ConnectionRepository(
        tokenStorage = tokenStorage,
        settings = settingsStore,
        factory = apiFactory,
    )

    val recipeRepository = RecipeRepository(connectionRepository)
    val shoppingRepository = ShoppingRepository(connectionRepository)
}
