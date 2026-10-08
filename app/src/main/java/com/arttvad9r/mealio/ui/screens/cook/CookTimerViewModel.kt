package com.arttvad9r.mealio.ui.screens.cook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the single Cook Mode countdown timer. */
data class CookTimerUiState(
    val totalSeconds: Long = 0,
    val remainingSeconds: Long = 0,
    val isRunning: Boolean = false,
) {
    val isActive: Boolean get() = totalSeconds > 0
    val isFinished: Boolean get() = isActive && remainingSeconds <= 0
}

/**
 * One countdown timer for Cook Mode. Lives in a ViewModel so it survives
 * recomposition and configuration changes: a rotation while cooking keeps the
 * timer running and its remaining time. The tick runs only while the timer is
 * live and the ViewModel is alive, so nothing keeps ticking off-screen forever.
 */
class CookTimerViewModel : ViewModel() {

    private val _state = MutableStateFlow(CookTimerUiState())
    val state: StateFlow<CookTimerUiState> = _state.asStateFlow()

    private var tickJob: Job? = null

    /** Starts (or restarts) a timer of [seconds]. A non-positive value just clears it. */
    fun start(seconds: Long) {
        tickJob?.cancel()
        tickJob = null
        if (seconds <= 0) {
            _state.value = CookTimerUiState()
            return
        }
        _state.value = CookTimerUiState(
            totalSeconds = seconds,
            remainingSeconds = seconds,
            isRunning = true,
        )
        tickJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val remaining = _state.value.remainingSeconds - 1
                if (remaining <= 0) {
                    _state.update { it.copy(remainingSeconds = 0, isRunning = false) }
                    break
                }
                _state.update { it.copy(remainingSeconds = remaining) }
            }
        }
    }

    /** Stop the running timer but keep the value, so it can be resumed. */
    fun stop() {
        tickJob?.cancel()
        tickJob = null
        _state.update { it.copy(isRunning = false) }
    }

    /** Clears the timer entirely (used when leaving Cook Mode or changing step). */
    fun reset() {
        tickJob?.cancel()
        tickJob = null
        _state.value = CookTimerUiState()
    }

    override fun onCleared() {
        tickJob?.cancel()
        tickJob = null
    }
}
