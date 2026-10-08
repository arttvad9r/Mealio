package com.arttvad9r.mealio.ui.screens.cook

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * The deadlines are mirrored into [SavedStateHandle], so a brief backgrounding —
 * even process death and restore — keeps them; a device reboot is out of scope.
 */
class CookTimerViewModel(
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
        _timers.update { it.start(seconds, stepNumber, clock()) }
        resumeTicking()
    }

    fun remove(id: Long) {
        _timers.update { it.remove(id) }
    }

    /** Runs the same length again, as a fresh timer, after it finished. */
    fun restart(id: Long) {
        val timer = _timers.value.items.firstOrNull { it.id == id } ?: return
        start(timer.totalSeconds, timer.stepNumber)
    }

    /** Re-reads the clock, e.g. after returning to the foreground. */
    fun refresh() {
        _now.value = clock()
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

    private companion object {
        const val KEY = "cook-timers"
        const val TICK_MS = 250L
    }
}
