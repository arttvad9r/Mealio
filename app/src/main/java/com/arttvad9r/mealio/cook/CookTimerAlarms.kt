package com.arttvad9r.mealio.cook

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.arttvad9r.mealio.MainActivity
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
 * A kitchen timer must fire on time, so the alarm is set as an *alarm clock*
 * ([AlarmManager.setAlarmClock]) — the strongest delivery contract AlarmManager
 * offers: the system exits Doze shortly before such an alarm and does not treat it
 * as a deferrable background alarm. A plain exact alarm
 * ([AlarmManager.setExactAndAllowWhileIdle]) looked correct on the emulator yet was
 * delivered with an OEM-imposed window on a real device (see ADR 0009), which a
 * timer cannot tolerate. The caller still checks [canScheduleExactAlarms] before
 * scheduling; [schedule] refuses silently without that access.
 */
class CookTimerAlarms(private val context: Context) : CookTimerScheduler {

    init {
        // The channel must exist before the first notification can be posted.
        CookTimerNotifications.ensureChannel(context)
    }

    /** True on Android 12+ only once the user granted the "alarms & reminders" access. */
    override fun canScheduleExactAlarms(): Boolean {
        val manager = context.getSystemService<AlarmManager>() ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    }

    /**
     * Schedules (or reschedules) the completion alarm for [timer] as an alarm
     * clock whose wall-clock trigger is the timer's deadline. No-op when exact
     * alarms are not permitted — the caller must not have produced the timer in
     * that case, and a silently-inexact alarm is exactly the bug this class
     * refuses to reintroduce.
     */
    override fun schedule(timer: ActiveTimer) {
        val manager = context.getSystemService<AlarmManager>() ?: return
        if (!canScheduleExactAlarms()) return
        val triggerAt = wallTriggerAt(timer.deadlineMillis, SystemClock.elapsedRealtime(), System.currentTimeMillis())
        val pending = pendingIntent(context, timer, PendingIntent.FLAG_UPDATE_CURRENT)
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent(context)), pending)
    }

    /**
     * The activity intent the system may launch when the user taps the alarm-clock
     * entry this timer produces (status bar / lock screen). Deliberately minimal:
     * it just opens Mealio. One shared instance, independent of any timer — it is a
     * launcher, not a per-timer handle.
     */
    private fun showIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            SHOW_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Cancels the alarm (and any shown notification) for a removed timer. */
    override fun cancel(timerId: Long) {
        val manager = context.getSystemService<AlarmManager>() ?: return
        val pending = pendingIntent(context, timerId, PendingIntent.FLAG_NO_CREATE) ?: return
        manager.cancel(pending)
        pending.cancel()
        CookTimerNotifications.cancel(context, timerId)
    }

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
        /** Request code of the shared "open Mealio" intent used as the alarm-clock show intent. */
        private const val SHOW_REQUEST_CODE = 0

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
