package com.arttvad9r.mealio.ui.screens.shopping

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Serialises check/uncheck mutations of a single shopping item.
 *
 * State machine per item:
 * - `confirmed` — the state the server last acknowledged. Only ever set after a
 *   successful send. `seed()` sets the baseline from a server read.
 * - `pending` — the newest user intent not yet sent. A request during an
 *   in-flight send merely overwrites it (coalescing); it is never dropped.
 *
 * `drain` removes the pending target, and sends only when it differs from
 * `confirmed`. On failure it does NOT discard a newer intent: if one arrived
 * while the send was in flight, the loop immediately processes it (re-sending if
 * it still differs, or exiting if it now equals `confirmed`). The UI is only
 * rolled back when no newer intent exists — i.e. when the failed target is still
 * the user's latest wish — so a tap made during a failed request always wins.
 *
 * At most one request per item is in flight; different items are independent and
 * never block each other.
 *
 * @param send performs the network mutation for (itemId, checked).
 * @param onRollback called when a send fails and no newer intent is pending,
 *   with the last state confirmed by the server.
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
                // A tap that arrived during the failed send is a newer intent and
                // must not be lost: process it instead of rolling over it. The
                // optimistic UI already shows that newer intent, so no rollback.
                if (pending[itemId] != null) continue
                onRollback(itemId, confirmed[itemId] ?: !target)
                return
            }
        }
    }
}
