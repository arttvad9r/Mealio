package com.arttvad9r.mealio.cook

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.arttvad9r.mealio.domain.cook.ActiveTimer
import com.arttvad9r.mealio.domain.cook.CookTimerScheduler
import com.arttvad9r.mealio.domain.cook.alarmRequestCode
import com.arttvad9r.mealio.domain.cook.wallTriggerAt

/**
 * The Android implementation of [CookTimerScheduler]: one AlarmManager alarm per
 * timer, keyed by a stable request code, so several timers run and complete
 * independently even when Mealio is backgrounded and the screen is off. A plain
 * coroutine cannot do that — it stops with the process — so the finish alert is
 * driven by the system alarm, and this class owns nothing else.
 *
 * Exact alarms need a user-granted special access from Android 12. Cook Mode asks
 * for it contextually (see CookModeScreen) and degrades gracefully: without it the
 * alarm is still set with the inexact, doze-friendly API, which may fire late but
 * is never silently dropped.
 */
class CookTimerAlarms(private val context: Context) : CookTimerScheduler {

    init {
        // The channel must exist before the first notification can be posted.
        CookTimerNotifications.ensureChannel(context)
    }

    /** Schedules (or reschedules) the completion alarm for [timer]. */
    override fun schedule(timer: ActiveTimer) {
        val manager = context.getSystemService<AlarmManager>() ?: return
        val triggerAt = wallTriggerAt(timer.deadlineMillis, SystemClock.elapsedRealtime(), System.currentTimeMillis())
        val pending = pendingIntent(context, timer, PendingIntent.FLAG_UPDATE_CURRENT)
        if (canScheduleExact(manager)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    /** Cancels the alarm (and any shown notification) for a removed timer. */
    override fun cancel(timerId: Long) {
        val manager = context.getSystemService<AlarmManager>() ?: return
        val pending = pendingIntent(context, timerId, PendingIntent.FLAG_NO_CREATE) ?: return
        manager.cancel(pending)
        pending.cancel()
        CookTimerNotifications.cancel(context, timerId)
    }

    private fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun pendingIntent(context: Context, timer: ActiveTimer, extraFlags: Int): PendingIntent =
        pendingIntent(
            context,
            timer.id,
            extraFlags,
            stepNumber = timer.stepNumber,
        )!!

    private fun pendingIntent(
        context: Context,
        timerId: Long,
        extraFlags: Int,
        stepNumber: Int = 0,
    ): PendingIntent? =
        alarmPendingIntent(context, timerId, stepNumber, extraFlags)

    companion object {
        /**
         * The broadcast intent that fires one timer's completion alarm. Its identity
         * is the (request code, data URI) pair — the request code from the timer id
         * and the URI carrying that same id — which is what keeps two timers' alarms
         * apart and lets a cancel target exactly one of them. Public to the module so
         * the instrumentation suite can assert that identity directly.
         */
        internal fun alarmPendingIntent(
            context: Context,
            timerId: Long,
            stepNumber: Int,
            extraFlags: Int,
        ): PendingIntent? {
            val intent = Intent(context, CookTimerReceiver::class.java).apply {
                action = CookTimerReceiver.ACTION_TIMER_FINISHED
                // The data URI is what tells two timers' PendingIntents apart.
                data = "mealio://cook-timer/$timerId".toUri()
                putExtra(CookTimerReceiver.EXTRA_TIMER_ID, timerId)
                putExtra(CookTimerReceiver.EXTRA_STEP_NUMBER, stepNumber)
            }
            return PendingIntent.getBroadcast(
                context,
                alarmRequestCode(timerId),
                intent,
                PendingIntent.FLAG_IMMUTABLE or extraFlags,
            )
        }
    }
}
