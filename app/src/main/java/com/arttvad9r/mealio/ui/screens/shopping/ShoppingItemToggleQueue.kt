package com.arttvad9r.mealio.ui.screens.shopping

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Serialises check/uncheck mutations of a single shopping item.
 *
 * A burst of rapid taps would otherwise send several concurrent PUTs for the
 * same item and could end with an order/rollback race. Here, while a send is in
 * flight a newer request merely replaces the pending target, so there is at most
 * one in-flight request per item and the final state always matches the last
 * requested one. Items are independent — one item's send never blocks another.
 *
 * @param send performs the network mutation for (itemId, checked).
 * @param onRollback called when a send fails, with the last state confirmed by
 *   the server, so the caller can drop its optimistic update.
 */
class ShoppingItemToggleQueue(
    private val scope: CoroutineScope,
    private val send: suspend (itemId: String, checked: Boolean) -> Unit,
    private val onRollback: (itemId: String, checked: Boolean) -> Unit,
) {

    private val jobs = mutableMapOf<String, Job>()
    private val pending = mutableMapOf<String, Boolean>()
    private val confirmed = mutableMapOf<String, Boolean>()

    /** Records the state the server currently holds, e.g. after a list load. */
    fun seed(itemId: String, checked: Boolean) {
        confirmed[itemId] = checked
    }

    /** Requests [checked] for [itemId]; coalesces rapid repeated calls. */
    fun request(itemId: String, checked: Boolean) {
        pending[itemId] = checked
        if (jobs[itemId]?.isActive == true) return
        jobs[itemId] = scope.launch { drain(itemId) }
    }

    private suspend fun drain(itemId: String) {
        while (true) {
            val target = pending.remove(itemId) ?: return
            // Already what the server holds — nothing to send.
            if (target == confirmed[itemId]) return
            try {
                send(itemId, target)
                confirmed[itemId] = target
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                pending.remove(itemId)
                onRollback(itemId, confirmed[itemId] ?: !target)
                // Keep draining: a tap during the failed send is still pending and
                // must win, otherwise the UI would show a state the server rejected.
                continue
            }
        }
    }
}
