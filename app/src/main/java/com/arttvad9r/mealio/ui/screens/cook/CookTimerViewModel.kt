package com.arttvad9r.mealio.ui.screens.cook

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arttvad9r.mealio.cook.CookTimerAlarms
import com.arttvad9r.mealio.cook.CookTimerNotifications
import com.arttvad9r.mealio.domain.cook.ActiveTimer
import com.arttvad9r.mealio.domain.cook.ActiveTimers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the Cook Mode timers. Each timer stores an absolute deadline on the
 * monotonic clock and the UI re-derives the remaining time from it on every tick,
 * so nothing drifts when Compose recomposes, the app pauses, or the user walks
 * between steps. Any number of timers run independently.
 *
 * Two mechanisms cooperate: this ViewModel keeps the in-app countdown and mirrors
 * deadlines into [SavedStateHandle], while each running timer also gets a
 * background alarm (see [CookTimerAlarms]) so it finishes even when Mealio is
 * backgrounded. Removal and restart cancel the corresponding alarm.
 */
class CookTimerViewModel(
    private val appContext: Context,
    private val savedState: SavedStateHandle,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    private val _timers = MutableStateFlow(restore())
    val timers: StateFlow<ActiveTimers> = _timers.asStateFlow()

    /** The clock reading the countdown is rendered against. */
    private val _now = MutableStateFlow(clock())
    val now: StateFlow<Long> = _now.asStateFlow()

    private var tickJob: Job? = null

    /** Starts a new timer for [seconds], tied to [stepNumber] for its label. */
    fun start(seconds: Long, stepNumber: Int) {
        if (seconds <= 0) return
        val before = _timers.value.items.map { it.id }.toSet()
        _timers.update { it.start(seconds, stepNumber, clock()) }
        _timers.value.items.firstOrNull { it.id !in before }?.let { started ->
            CookTimerAlarms.schedule(appContext, started)
        }
        resumeTicking()
    }

    fun remove(id: Long) {
        CookTimerAlarms.cancel(appContext, id)
        _timers.update { it.remove(id) }
    }

    /** Runs the same length again as a fresh timer, replacing the finished one. */
    fun restart(id: Long) {
        val timer = _timers.value.items.firstOrNull { it.id == id } ?: return
        // Drop the stale schedule and any shown notification, then replace the
        // finished timer so its row does not linger next to the new one.
        CookTimerAlarms.cancel(appContext, id)
        _timers.update { it.remove(id) }
        start(timer.totalSeconds, timer.stepNumber)
    }

    /** Re-reads the clock, e.g. after returning to the foreground. */
    fun refresh() {
        _now.value = clock()
        // Anything that already finished while we were away has now fired; drop
        // its scheduled alarm so it cannot fire twice.
        _timers.value.items.filter { it.deadlineMillis <= _now.value }
            .forEach { CookTimerAlarms.cancel(appContext, it.id) }
        resumeTicking()
    }

    private fun resumeTicking() {
        val now = clock()
        _now.value = now
        if (tickJob?.isActive == true || !_timers.value.hasRunning(now)) {
            persist()
            return
        }
        persist()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                val tick = clock()
                _now.value = tick
                if (!_timers.value.hasRunning(tick)) break
            }
            _now.value = clock()
            persist()
            tickJob = null
        }
    }

    override fun onCleared() {
        tickJob?.cancel()
        tickJob = null
    }

    // Deadlines are monotonic clock readings, so they survive a process restart
    // (no reboot) and can be stored as plain longs.
    private fun persist() {
        savedState[KEY] = _timers.value.items
            .flatMap { listOf(it.id, it.totalSeconds, it.deadlineMillis, it.stepNumber.toLong()) }
            .toLongArray()
    }

    private fun restore(): ActiveTimers {
        val flat = savedState.get<LongArray>(KEY) ?: return ActiveTimers()
        if (flat.size % 4 != 0) return ActiveTimers()
        val timers = flat.toList().chunked(4).mapNotNull { chunk ->
            val (id, total, deadline, step) = chunk
            if (total <= 0) null else ActiveTimer(id, total, deadline, step.toInt())
        }
        return ActiveTimers(timers)
    }

    init {
        CookTimerNotifications.ensureChannel(appContext)
    }

    private companion object {
        const val KEY = "cook-timers"
        const val TICK_MS = 250L
    }
}
