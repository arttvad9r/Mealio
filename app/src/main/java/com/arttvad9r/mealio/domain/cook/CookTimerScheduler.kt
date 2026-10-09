package com.arttvad9r.mealio.domain.cook

/**
 * The system half of a Cook Mode timer, behind a tiny interface: the ViewModel
 * talks only to this, so its rules are unit-tested with a fake and never pull
 * Android onto the test classpath. The real implementation uses AlarmManager
 * plus a notification.
 *
 * A kitchen timer must fire on time, so [canScheduleExactAlarms] is part of the
 * contract: a caller starts a timer only when the platform will actually schedule
 * it exactly. There is deliberately no inexact scheduling path — a timer that may
 * fire a minute late is not an acceptable kitchen timer.
 *
 * [schedule] is idempotent per timer id — rescheduling replaces the previous
 * alarm — and [cancel] must be safe for an id that has no alarm.
 */
interface CookTimerScheduler {
    /** Whether the platform will grant an exact alarm right now. */
    fun canScheduleExactAlarms(): Boolean

    fun schedule(timer: ActiveTimer)
    fun cancel(timerId: Long)
}
