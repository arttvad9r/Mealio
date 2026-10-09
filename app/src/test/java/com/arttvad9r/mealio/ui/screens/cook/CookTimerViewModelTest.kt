package com.arttvad9r.mealio.ui.screens.cook

import androidx.lifecycle.SavedStateHandle
import com.arttvad9r.mealio.domain.cook.ActiveTimer
import com.arttvad9r.mealio.domain.cook.CookTimerScheduler
import com.arttvad9r.mealio.domain.cook.CookStep
import com.arttvad9r.mealio.domain.cook.Duration
import com.arttvad9r.mealio.domain.cook.TimerSuggestion
import com.arttvad9r.mealio.domain.cook.isFinished
import com.arttvad9r.mealio.domain.cook.remainingSeconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Records what the ViewModel asks the platform to schedule, with no Android. */
private class RecordingScheduler(var exactPermitted: Boolean = true) : CookTimerScheduler {
    val scheduled = mutableListOf<ActiveTimer>()
    val cancelled = mutableListOf<Long>()

    override fun canScheduleExactAlarms(): Boolean = exactPermitted

    override fun schedule(timer: ActiveTimer) {
        scheduled += timer
    }

    override fun cancel(timerId: Long) {
        cancelled += timerId
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CookTimerViewModelTest {

    private lateinit var scheduler: RecordingScheduler

    @Before
    fun setUp() {
        // viewModelScope dispatches on Main, which does not exist off-device.
        Dispatchers.setMain(StandardTestDispatcher())
        scheduler = RecordingScheduler()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(clock: () -> Long = { 0L }) =
        CookTimerViewModel(scheduler, SavedStateHandle(), clock)

    // --- Editor opens at the right starting value -------------------------------

    @Test
    fun `editor opens at the suggested single duration`() {
        val vm = viewModel()
        vm.openEditor(suggestionSeconds(step(TimerSuggestion.Single(Duration(15 * 60L)))))

        assertTrue(vm.editor.value.visible)
        assertEquals(15 * 60L, vm.editor.value.initialSeconds)
    }

    @Test
    fun `editor opens at the lower bound of a range`() {
        val vm = viewModel()
        vm.openEditor(
            suggestionSeconds(
                step(TimerSuggestion.Range(Duration(4 * 60L), Duration(5 * 60L))),
            ),
        )

        assertEquals(4 * 60L, vm.editor.value.initialSeconds)
    }

    @Test
    fun `editor opens at five minutes when the step names no time`() {
        val vm = viewModel()
        vm.openEditor(suggestionSeconds(step(null)))

        assertEquals(5 * 60L, vm.editor.value.initialSeconds)
    }

    // --- Cancel and start -------------------------------------------------------

    @Test
    fun `cancel closes the editor and creates no timer`() {
        val vm = viewModel()
        vm.openEditor(5 * 60L)
        vm.cancelEditor()

        assertFalse(vm.editor.value.visible)
        assertTrue(vm.timers.value.items.isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `start creates exactly one timer and closes the editor`() {
        val vm = viewModel(clock = { 1_000L })
        vm.openEditor(5 * 60L)
        vm.startFromEditor(5 * 60L, stepNumber = 1)

        assertEquals(1, vm.timers.value.items.size)
        assertFalse(vm.editor.value.visible)
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(5 * 60L, scheduler.scheduled.single().totalSeconds)
    }

    @Test
    fun `reopening the editor gets a fresh session and value`() {
        val vm = viewModel()
        vm.openEditor(15 * 60L)
        val firstSession = vm.editor.value.session
        assertEquals(15 * 60L, vm.editor.value.initialSeconds)
        vm.cancelEditor()
        vm.openEditor(5 * 60L)

        assertEquals(5 * 60L, vm.editor.value.initialSeconds)
        assertTrue(vm.editor.value.session > firstSession)
    }

    // --- Several timers are independent ----------------------------------------

    @Test
    fun `several timers get distinct ids and independent alarms`() {
        var now = 0L
        val vm = viewModel(clock = { now })
        vm.startFromEditor(5 * 60L, 1)
        now += 1_000L
        vm.startFromEditor(9 * 60L, 2)

        val ids = vm.timers.value.items.map { it.id }
        assertEquals(2, ids.toSet().size)
        assertEquals(ids, scheduler.scheduled.map { it.id })
    }

    @Test
    fun `removing one timer cancels only its own alarm`() {
        var now = 0L
        val vm = viewModel(clock = { now })
        vm.startFromEditor(5 * 60L, 1)
        now += 1_000L
        vm.startFromEditor(9 * 60L, 2)
        val (first, second) = vm.timers.value.items

        vm.remove(first.id)

        assertEquals(listOf(second), vm.timers.value.items)
        assertEquals(listOf(first.id), scheduler.cancelled)
    }

    @Test
    fun `restart cancels the old alarm and schedules a fresh one`() {
        var now = 0L
        val vm = viewModel(clock = { now })
        vm.startFromEditor(5 * 60L, 1)
        val old = vm.timers.value.items.single()

        vm.restart(old.id)

        val fresh = vm.timers.value.items.single()
        assertTrue(fresh.id != old.id)
        assertEquals(old.totalSeconds, fresh.totalSeconds)
        assertEquals(old.stepNumber, fresh.stepNumber)
        assertEquals(listOf(old.id), scheduler.cancelled)
        assertEquals(listOf(old.id, fresh.id), scheduler.scheduled.map { it.id })
    }

    // --- Deadline behaviour did not regress ------------------------------------

    @Test
    fun `remaining time is derived from the deadline after backgrounding`() {
        var now = 1_000_000L
        val vm = viewModel(clock = { now })
        vm.startFromEditor(5 * 60L, 1)
        val timer = vm.timers.value.items.single()

        // The app was away for a while; only the clock moved.
        now += 4 * 60_000L
        vm.refresh()
        assertEquals(60L, timer.remainingSeconds(vm.now.value))

        now += 2 * 60_000L
        vm.refresh()
        assertTrue(timer.isFinished(vm.now.value))
        assertEquals(0L, timer.remainingSeconds(vm.now.value))
    }

    // --- Exact-alarm requirement -------------------------------------------------

    @Test
    fun `without exact-alarm access no timer starts and the requirement is raised`() {
        scheduler.exactPermitted = false
        val vm = viewModel()
        vm.openEditor(5 * 60L)

        vm.startFromEditor(5 * 60L, stepNumber = 1)

        assertTrue(vm.timers.value.items.isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
        assertTrue("the screen must be told to explain the access", vm.exactAlarmRequired.value)
        // The editor stays open so the user can grant the access and confirm again.
        assertTrue(vm.editor.value.visible)
    }

    @Test
    fun `after the access is granted the same editor start succeeds`() {
        scheduler.exactPermitted = false
        val vm = viewModel()
        vm.openEditor(5 * 60L)
        vm.startFromEditor(5 * 60L, 1)
        assertTrue(vm.exactAlarmRequired.value)

        // The user granted the access in the system screen; we re-check on return.
        scheduler.exactPermitted = true
        vm.onExactAlarmAccessChecked()
        assertFalse(vm.exactAlarmRequired.value)

        vm.startFromEditor(5 * 60L, 1)
        assertEquals(1, vm.timers.value.items.size)
        assertEquals(1, scheduler.scheduled.size)
    }

    @Test
    fun `with exact access a timer starts normally and raises nothing`() {
        val vm = viewModel()
        vm.openEditor(5 * 60L)

        vm.startFromEditor(5 * 60L, 1)

        assertEquals(1, vm.timers.value.items.size)
        assertFalse(vm.exactAlarmRequired.value)
    }

    private fun step(suggestion: TimerSuggestion?): CookStep =
        CookStep(title = null, text = "text", suggestions = listOfNotNull(suggestion))
}
