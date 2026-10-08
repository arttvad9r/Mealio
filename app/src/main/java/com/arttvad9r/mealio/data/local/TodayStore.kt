package com.arttvad9r.mealio.data.local

import android.content.Context
import com.arttvad9r.mealio.domain.today.TodaySelection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId

/** Tiny string sink, so the day logic is testable without Android. */
interface StringStorage {
    fun read(): String?
    fun write(value: String)
}

/** SharedPreferences-backed [StringStorage] — the project's existing light store. */
class SharedPrefsStorage(context: Context) : StringStorage {
    private val prefs = context.applicationContext
        .getSharedPreferences("mealio_today", Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY, null)

    override fun write(value: String) {
        prefs.edit().putString(KEY, value).apply()
    }

    private companion object {
        const val KEY = "today_day"
    }
}

/**
 * Stores ONLY the current local day's selection. When the local calendar date
 * changes the stored set is treated as stale and read back as empty — there is
 * no history and no background worker; the date is checked whenever the day is
 * read. Backed by [StringStorage] (SharedPreferences in the app); Room is
 * deliberately not used.
 *
 * @param today supplies the current local date (yyyy-MM-dd) — injectable for tests.
 */
class TodayStore(
    private val storage: StringStorage,
    private val today: () -> String = { LocalDate.now(ZoneId.systemDefault()).toString() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    constructor(context: Context) : this(SharedPrefsStorage(context))

    private val json = Json { ignoreUnknownKeys = true }

    /** Reads the saved selections for today; empty when stale or unreadable. */
    suspend fun read(): List<TodaySelection> = withContext(dispatcher) {
        val raw = storage.read() ?: return@withContext emptyList()
        val stored = runCatching { json.decodeFromString<StoredDay>(raw) }.getOrNull()
            ?: return@withContext emptyList()
        if (stored.date != today()) emptyList() else stored.selections
    }

    suspend fun save(selections: List<TodaySelection>) = withContext(dispatcher) {
        storage.write(json.encodeToString(StoredDay(date = today(), selections = selections)))
    }

    suspend fun clear() = withContext(dispatcher) {
        storage.write(json.encodeToString(StoredDay(date = "", selections = emptyList())))
    }

    @Serializable
    private data class StoredDay(
        val date: String,
        val selections: List<TodaySelection> = emptyList(),
    )
}
