package com.arttvad9r.mealio.data.local

import android.content.Context
import com.arttvad9r.mealio.domain.model.ServerAccount
import com.arttvad9r.mealio.domain.today.DailyTarget
import com.arttvad9r.mealio.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Non-secret UI and connection preferences. The API token itself lives only in
 * [SecureTokenStorage]. The app language is not stored here — it is owned by the
 * standard per-app locale API (see [com.arttvad9r.mealio.ui.AppLanguage]).
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readTheme())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dailyCalorieTarget = MutableStateFlow(readCalorieTarget())
    /** The user's daily calorie goal, used for the Today screen progress bar. */
    val dailyCalorieTarget: StateFlow<Int> = _dailyCalorieTarget.asStateFlow()

    private val _accountFlow = MutableStateFlow(readAccount())
    /** Emits the current account (or null when disconnected) so the UI can react. */
    val accountFlow: StateFlow<ServerAccount?> = _accountFlow.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    private fun readTheme(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)

    /** Clamps out-of-range input so bad stored data can never reach the UI. */
    fun setDailyCalorieTarget(calories: Int) {
        val clamped = DailyTarget.coerce(calories)
        prefs.edit().putInt(KEY_TARGET, clamped).apply()
        _dailyCalorieTarget.value = clamped
    }

    private fun readCalorieTarget(): Int =
        DailyTarget.coerce(prefs.getInt(KEY_TARGET, DailyTarget.DEFAULT_CALORIES))

    var serverUrl: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    var username: String?
        get() = prefs.getString(KEY_USER, null)
        set(value) = prefs.edit().putString(KEY_USER, value).apply()

    var fullName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var household: String?
        get() = prefs.getString(KEY_HOUSEHOLD, null)
        set(value) = prefs.edit().putString(KEY_HOUSEHOLD, value).apply()

    var mealieVersion: String?
        get() = prefs.getString(KEY_VERSION, null)
        set(value) = prefs.edit().putString(KEY_VERSION, value).apply()

    fun saveAccount(account: ServerAccount) {
        serverUrl = account.serverUrl
        username = account.username
        fullName = account.fullName
        household = account.household
        mealieVersion = account.mealieVersion
        _accountFlow.value = account
    }

    fun clearAccount() {
        prefs.edit()
            .remove(KEY_URL)
            .remove(KEY_USER)
            .remove(KEY_NAME)
            .remove(KEY_HOUSEHOLD)
            .remove(KEY_VERSION)
            .apply()
        _accountFlow.value = null
    }

    fun readAccount(): ServerAccount? {
        val url = serverUrl ?: return null
        return ServerAccount(
            serverUrl = url,
            username = username,
            fullName = fullName,
            household = household,
            mealieVersion = mealieVersion,
        )
    }

    private companion object {
        const val PREFS = "mealio_settings"
        const val KEY_THEME = "theme_mode"
        const val KEY_TARGET = "daily_calorie_target"
        const val KEY_URL = "server_url"
        const val KEY_USER = "username"
        const val KEY_NAME = "full_name"
        const val KEY_HOUSEHOLD = "household"
        const val KEY_VERSION = "mealie_version"
    }
}
