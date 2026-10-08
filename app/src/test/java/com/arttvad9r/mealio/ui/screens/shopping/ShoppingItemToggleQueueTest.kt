package com.arttvad9r.mealio.ui.screens.shopping

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingItemToggleQueueTest {

    @Test
    fun `rapid repeats of the same item coalesce into a single send`() = runTest {
        val sent = mutableListOf<Pair<String, Boolean>>()
        val gate = CompletableDeferred<Unit>()
        val queue = ShoppingItemToggleQueue(
            scope = this,
            send = { id, checked ->
                gate.await()
                sent += id to checked
            },
            onRollback = { _, _ -> },
        )
        queue.seed("a", false)

        queue.request("a", true)
        runCurrent() // drain starts and blocks inside send()
        queue.request("a", false)
        queue.request("a", true)

        gate.complete(Unit)
        advanceUntilIdle()

        // Only the first send went out; the two later taps collapsed onto the
        // in-flight target, and the trailing value equals the server state.
        assertEquals(listOf("a" to true), sent)
    }

    @Test
    fun `a value equal to the confirmed state is not sent`() = runTest {
        val sent = mutableListOf<Pair<String, Boolean>>()
        val queue = ShoppingItemToggleQueue(
            scope = this,
            send = { id, checked -> sent += id to checked },
            onRollback = { _, _ -> },
        )
        queue.seed("a", true)

        queue.request("a", true)
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, Boolean>>(), sent)
    }

    @Test
    fun `failure rolls back to the last server-confirmed state`() = runTest {
        val rollbacks = mutableListOf<Pair<String, Boolean>>()
        val queue = ShoppingItemToggleQueue(
            scope = this,
            send = { _, _ -> throw RuntimeException("network") },
            onRollback = { id, checked -> rollbacks += id to checked },
        )
        queue.seed("a", false)

        queue.request("a", true)
        advanceUntilIdle()

        // The optimistic tap is reverted to the state the server still holds.
        assertEquals(listOf("a" to false), rollbacks)
    }

    @Test
    fun `items are independent and do not block each other`() = runTest {
        val sent = mutableListOf<Pair<String, Boolean>>()
        val gateA = CompletableDeferred<Unit>()
        val queue = ShoppingItemToggleQueue(
            scope = this,
            send = { id, checked ->
                if (id == "a") gateA.await()
                sent += id to checked
            },
            onRollback = { _, _ -> },
        )
        queue.seed("a", false)
        queue.seed("b", false)

        queue.request("a", true) // blocks on gateA
        runCurrent()
        queue.request("b", true) // must not wait for "a"
        advanceUntilIdle()

        assertEquals(listOf("b" to true), sent)
        gateA.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("b" to true, "a" to true), sent)
    }
}
