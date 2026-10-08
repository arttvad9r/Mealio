package com.arttvad9r.mealio.data.local

import com.arttvad9r.mealio.domain.today.TodaySelection
import com.arttvad9r.mealio.domain.today.TodaySlot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayStoreTest {

    /** In-memory [StringStorage] so the store is exercised without Android. */
    private class FakeStorage : StringStorage {
        var value: String? = null
        override fun read(): String? = value
        override fun write(value: String) {
            this.value = value
        }
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val selection = TodaySelection(slot = TodaySlot.MAIN, slug = "kurica", servings = 2.5)

    private fun store(storage: StringStorage, date: String = "2026-10-08") =
        TodayStore(storage, { date }, dispatcher)

    @Test
    fun `save then read returns the same day`() = runTest {
        val store = store(FakeStorage())
        store.save(listOf(selection))
        assertEquals(listOf(selection), store.read())
    }

    @Test
    fun `servings survive as Double`() = runTest {
        val store = store(FakeStorage())
        store.save(listOf(selection.copy(servings = 1.75)))
        assertEquals(1.75, store.read().single().servings, 0.0001)
    }

    @Test
    fun `a new day clears the previous state`() = runTest {
        val storage = FakeStorage()
        store(storage, "2026-10-08").save(listOf(selection))
        // Same payload, read on the next calendar day -> empty.
        assertTrue(store(storage, "2026-10-09").read().isEmpty())
    }

    @Test
    fun `unreadable payload reads as empty`() = runTest {
        val storage = FakeStorage().apply { value = "{not json" }
        assertTrue(store(storage).read().isEmpty())
    }

    @Test
    fun `empty storage reads as empty`() = runTest {
        assertTrue(store(FakeStorage()).read().isEmpty())
    }

    @Test
    fun `clear empties the day`() = runTest {
        val store = store(FakeStorage())
        store.save(listOf(selection))
        store.clear()
        assertTrue(store.read().isEmpty())
    }
}
