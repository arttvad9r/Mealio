package com.arttvad9r.mealio.data.repository

import com.arttvad9r.mealio.data.local.SecureTokenStorage
import com.arttvad9r.mealio.data.local.SettingsStore
import com.arttvad9r.mealio.data.remote.ErrorKind
import com.arttvad9r.mealio.data.remote.MealieApi
import com.arttvad9r.mealio.data.remote.MealieApiFactory
import com.arttvad9r.mealio.data.remote.MealioException
import com.arttvad9r.mealio.data.remote.UrlNormalizer
import com.arttvad9r.mealio.data.remote.toMealioException
import com.arttvad9r.mealio.domain.model.ServerAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the active connection: resolves the server URL, holds the token in
 * secure storage and builds an authenticated [MealieApi].
 */
class ConnectionRepository(
    private val tokenStorage: SecureTokenStorage,
    private val settings: SettingsStore,
    private val factory: MealieApiFactory,
) {

    val isConfigured: Boolean
        get() = settings.serverUrl != null && tokenStorage.getToken() != null

    fun currentAccount(): ServerAccount? = settings.readAccount()

    /** Authenticated API for the configured server, or null when not connected. */
    fun api(): MealieApi? {
        val url = settings.serverUrl ?: return null
        return factory.create(url)
    }

    /**
     * Validates the URL, checks connectivity and the token by fetching the server
     * info and the current user. On success the token and account are persisted.
     */
    suspend fun connect(rawUrl: String, token: String): ServerAccount = withContext(Dispatchers.IO) {
        val normalized = when (val r = UrlNormalizer.normalize(rawUrl)) {
            is UrlNormalizer.Result.Ok -> r
            UrlNormalizer.Result.Invalid -> throw MealioException(ErrorKind.URL_INVALID)
        }
        if (token.isBlank()) throw MealioException(ErrorKind.AUTH)

        // Verify with the candidate token before persisting anything.
        val probe = factory.createWithToken(normalized.baseUrl, token)
        val account = try {
            val about = probe.about()
            val user = probe.self()
            ServerAccount(
                serverUrl = normalized.normalized,
                username = user.username,
                fullName = user.fullName,
                household = user.householdSlug,
                mealieVersion = about.version,
            )
        } catch (t: Throwable) {
            throw t.toMealioException()
        }

        tokenStorage.saveToken(token)
        settings.saveAccount(account)
        account
    }

    fun disconnect() {
        tokenStorage.clear()
        settings.clearAccount()
    }
}
