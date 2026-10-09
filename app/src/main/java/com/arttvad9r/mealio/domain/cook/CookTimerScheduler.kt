package com.arttvad9r.mealio.domain.cook

/**
 * The system half of a Cook Mode timer, behind a tiny interface: the ViewModel
 * talks only to this, so its rules are unit-tested with a fake and never pull
 * Android onto the test classpath. The real implementation uses AlarmManager
 * plus a notification.
 *
 * [schedule] is idempotent per timer id — rescheduling replaces the previous
 * alarm — and [cancel] must be safe for an id that has no alarm.
 */
interface CookTimerScheduler {
    fun schedule(timer: ActiveTimer)
    fun cancel(timerId: Long)
}
