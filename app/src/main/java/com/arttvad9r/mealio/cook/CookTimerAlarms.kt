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
import com.arttvad9r.mealio.domain.cook.alarmRequestCode
import com.arttvad9r.mealio.domain.cook.wallTriggerAt

/**
 * Owns the background half of a Cook Mode timer: one alarm per timer, keyed by a
 * stable request code, so timers complete even when Mealio is backgrounded and
 * the screen is off. A plain Compose coroutine cannot do that — it stops when the
 * app is backgrounded — so the completion alert is driven by AlarmManager.
 *
 * Exact alarms need a user-granted special access from Android 12/14. Cook Mode
 * degrades gracefully: without it the alarm is still set with the inexact,
 * doze-friendly API, which may fire a little late but never silently drops the
 * timer. No special permission is requested at launch.
 */
object CookTimerAlarms {

    /** Schedules (or reschedules) the completion alarm for [timer]. */
    fun schedule(context: Context, timer: ActiveTimer) {
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
    fun cancel(context: Context, timerId: Long) {
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
            totalSeconds = timer.totalSeconds,
        )!!

    private fun pendingIntent(
        context: Context,
        timerId: Long,
        extraFlags: Int,
        stepNumber: Int = 0,
        totalSeconds: Long = 0L,
    ): PendingIntent? {
        val intent = Intent(context, CookTimerReceiver::class.java).apply {
            action = CookTimerReceiver.ACTION_TIMER_FINISHED
            // The extras distinguish alarms when the PendingIntents are compared.
            data = "mealio://cook-timer/$timerId".toUri()
            putExtra(CookTimerReceiver.EXTRA_TIMER_ID, timerId)
            putExtra(CookTimerReceiver.EXTRA_STEP_NUMBER, stepNumber)
            putExtra(CookTimerReceiver.EXTRA_TOTAL_SECONDS, totalSeconds)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmRequestCode(timerId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or extraFlags,
        )
    }
}
