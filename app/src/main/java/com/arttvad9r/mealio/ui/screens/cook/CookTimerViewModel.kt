package com.arttvad9r.mealio.ui.screens.cook

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arttvad9r.mealio.domain.cook.ActiveTimer
import com.arttvad9r.mealio.domain.cook.ActiveTimers
import com.arttvad9r.mealio.domain.cook.CookTimerScheduler
import com.arttvad9r.mealio.domain.cook.TimerSetup
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Whether the single inline timer editor is open, the duration it opened with and
 * the edit session it belongs to. Kept in the ViewModel — not in the composable —
 * so the cancel/start rules are unit-tested directly. [session] changes on every
 * open, which lets the screen reset the editable value while still surviving a
 * configuration change.
 */
data class TimerEditorState(
    val visible: Boolean = false,
    val initialSeconds: Long = TimerSetup.MANUAL.totalSeconds,
    val session: Int = 0,
)

/**
 * Holds the Cook Mode timers. Each timer stores an absolute deadline on the
 * monotonic clock and the UI re-derives the remaining time from it on every tick,
 * so nothing drifts when Compose recomposes, the app pauses, or the user walks
 * between steps. Any number of timers run independently.
 *
 * Two mechanisms cooperate and never race: this ViewModel drives the in-app
 * countdown from the deadline, while every running timer is also handed to a
 * [CookTimerScheduler], which raises the system alarm and notification that fire
 * even when Mealio is backgrounded and the screen is off. The scheduler is an
 * injected interface, so these rules are unit-tested against a fake and no Android
 * class is needed.
 *
 * A timer is only ever created by [start] / [startFromEditor]; the editor never
 * starts anything on its own, and [cancelEditor] starts nothing at all.
 */
class CookTimerViewModel(
    private val scheduler: CookTimerScheduler,
    private val savedState: SavedStateHandle,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    private val _timers = MutableStateFlow(restore())
    val timers: StateFlow<ActiveTimers> = _timers.asStateFlow()

    /** The clock reading the countdown is rendered against. */
    private val _now = MutableStateFlow(clock())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _editor = MutableStateFlow(TimerEditorState())
    val editor: StateFlow<TimerEditorState> = _editor.asStateFlow()

    private var tickJob: Job? = null

    /** Opens the editor at [initialSeconds]. Starting nothing until the user confirms. */
    fun openEditor(initialSeconds: Long) {
        _editor.update { TimerEditorState(visible = true, initialSeconds = initialSeconds, session = it.session + 1) }
    }

    /** The user abandoned the editor: it closes and no timer is created. */
    fun cancelEditor() {
        _editor.value = TimerEditorState(session = _editor.value.session)
    }

    /** Confirms the editor: creates exactly one timer and closes the editor. */
    fun startFromEditor(totalSeconds: Long, stepNumber: Int) {
        start(totalSeconds, stepNumber)
        _editor.value = TimerEditorState(session = _editor.value.session)
    }

    /** Starts a new timer for [seconds], tied to [stepNumber] for its label. */
    fun start(seconds: Long, stepNumber: Int) {
        if (seconds <= 0) return
        val before = _timers.value.items.map { it.id }.toSet()
        _timers.update { it.start(seconds, stepNumber, clock()) }
        _timers.value.items.firstOrNull { it.id !in before }?.let { started ->
            scheduler.schedule(started)
        }
        resumeTicking()
    }

    fun remove(id: Long) {
        scheduler.cancel(id)
        _timers.update { it.remove(id) }
    }

    /** Runs the same length again as a fresh timer, replacing the finished one. */
    fun restart(id: Long) {
        val timer = _timers.value.items.firstOrNull { it.id == id } ?: return
        // Drop the stale schedule and any shown notification, then replace the
        // finished timer so its row does not linger next to the new one. The new
        // timer gets a fresh id and therefore a fresh, independent alarm.
        scheduler.cancel(id)
        _timers.update { it.remove(id) }
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
