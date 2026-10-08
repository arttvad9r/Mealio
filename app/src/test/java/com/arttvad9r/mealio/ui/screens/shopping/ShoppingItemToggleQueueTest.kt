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

    // --- regression: a tap that arrives during a failed send must not be lost ---

    @Test
    fun `intent tapped during a failed send is honoured, not dropped`() = runTest {
        val attempts = mutableListOf<Pair<String, Boolean>>()
        val rollbacks = mutableListOf<Pair<String, Boolean>>()
        val gate = CompletableDeferred<Unit>()
        var call = 0
        val queue = ShoppingItemToggleQueue(
            scope = this,
            send = { id, checked ->
                call++
                attempts += id to checked
                if (call == 1) {
                    gate.await()
                    throw RuntimeException("first PUT fails")
                }
            },
            onRollback = { id, checked -> rollbacks += id to checked },
        )
        queue.seed("a", false)

        queue.request("a", true) // first PUT, blocked then failing
        runCurrent()
        queue.request("a", false)
        queue.request("a", true) // last user intent = true

        gate.complete(Unit)
        advanceUntilIdle()

        // confirmed is still false after the failure; the newest intent (true)
        // must be re-sent rather than discarded, and no rollback may clobber it.
        assertEquals(listOf("a" to true, "a" to true), attempts)
        assertEquals(emptyList<Pair<String, Boolean>>(), rollbacks)
    }

    @Test
    fun `tap matching confirmed during a failed send causes no extra PUT or rollback`() = runTest {
        val attempts = mutableListOf<Pair<String, Boolean>>()
        val rollbacks = mutableListOf<Pair<String, Boolean>>()
        val gate = CompletableDeferred<Unit>()
        var call = 0
        val queue = ShoppingItemToggleQueue(
            scope = this,
            send = { id, checked ->
                call++
                attempts += id to checked
                if (call == 1) {
                    gate.await()
                    throw RuntimeException("first PUT fails")
                }
            },
            onRollback = { id, checked -> rollbacks += id to checked },
        )
        queue.seed("a", false)

        queue.request("a", true) // first PUT, blocked then failing
        runCurrent()
        queue.request("a", false) // newer intent == confirmed state

        gate.complete(Unit)
        advanceUntilIdle()

        // Net effect of the newer tap is already the server state, so no PUT is
        // needed and the UI must not be rolled back over a newer intent.
        assertEquals(listOf("a" to true), attempts)
        assertEquals(emptyList<Pair<String, Boolean>>(), rollbacks)
    }

    @Test
    fun `false-true-false with a successful first PUT reaches the server`() = runTest {
        val sent = mutableListOf<Pair<String, Boolean>>()
        val gate = CompletableDeferred<Unit>()
        val queue = ShoppingItemToggleQueue(
            scope = this,
            send = { id, checked ->
                if (sent.isEmpty()) gate.await()
                sent += id to checked
            },
            onRollback = { _, _ -> },
        )
        queue.seed("a", false)

        queue.request("a", true)
        runCurrent()
        queue.request("a", false)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("a" to true, "a" to false), sent)
    }

    @Test
    fun `false-true-false-true with a successful first PUT coalesces onto confirmed`() = runTest {
        val sent = mutableListOf<Pair<String, Boolean>>()
        val gate = CompletableDeferred<Unit>()
        val queue = ShoppingItemToggleQueue(
            scope = this,
            send = { id, checked ->
                if (sent.isEmpty()) gate.await()
                sent += id to checked
            },
            onRollback = { _, _ -> },
        )
        queue.seed("a", false)

        queue.request("a", true)
        runCurrent()
        queue.request("a", false)
        queue.request("a", true)
        gate.complete(Unit)
        advanceUntilIdle()

        // true is already confirmed by the first PUT, so the trailing intents net
        // out and no redundant second PUT is sent.
        assertEquals(listOf("a" to true), sent)
    }
}
