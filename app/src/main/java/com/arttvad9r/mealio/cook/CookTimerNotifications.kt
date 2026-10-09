package com.arttvad9r.mealio.cook

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.arttvad9r.mealio.MainActivity
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.domain.cook.alarmRequestCode
import com.arttvad9r.mealio.domain.format.DurationFormatter

/**
 * The cooking-timer notification: its own channel, an alarm-like importance and
 * the system alarm sound with a vibration pattern. Kept out of Compose — the
 * screen only asks the ViewModel to schedule/cancel; no notification details live
 * in the UI layer.
 *
 * The notification is only posted when the alarm receiver fires. If the user
 * denied POST_NOTIFICATIONS (Android 13+) it is silently skipped; the timer still
 * runs, Cook Mode still shows "time is up", and the vibration the receiver
 * performs is the fallback.
 */
object CookTimerNotifications {

    private const val CHANNEL_ID = "cook-timers"

    /**
     * Creates the channel. Importance is [NotificationManager.IMPORTANCE_HIGH]
     * so a finished timer heads-up: this is an alarm-like event, not a chat ping.
     */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.cook_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.cook_channel_description)
            enableVibration(true)
            enableLights(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Shows "Timer finished" for [timerId]. Tapping it opens the app. No-op when
     * notifications are not permitted, so the caller never has to branch.
     */
    fun notifyFinished(context: Context, timerId: Long, stepNumber: Int, totalSeconds: Long) {
        if (!areEnabled(context)) return
        ensureChannel(context)

        val body = context.getString(
            R.string.cook_notification_body,
            stepNumber,
            DurationFormatter.formatTimer(totalSeconds, context),
        )
        val openApp = PendingIntent.getActivity(
            context,
            alarmRequestCode(timerId),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(R.string.cook_notification_title))
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(FINISH_PATTERN)
            .build()

        // Lint would prefer a runtime permission check here; we already gated on
        // areEnabled(), which covers POST_NOTIFICATIONS.
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(alarmRequestCode(timerId), notification)
    }

    /** Clears the finished notification when its timer is removed or restarted. */
    fun cancel(context: Context, timerId: Long) {
        NotificationManagerCompat.from(context).cancel(alarmRequestCode(timerId))
    }

    private fun areEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** A short triple buzz, felt on a countertop, not a single tap. */
    private val FINISH_PATTERN = longArrayOf(0L, 400L, 250L, 400L, 250L, 600L)
}
