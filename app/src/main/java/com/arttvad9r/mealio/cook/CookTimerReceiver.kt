package com.arttvad9r.mealio.cook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService

/**
 * Fires when a Cook Mode timer's alarm goes off — the only piece that runs while
 * Mealio is backgrounded. It raises the completion notification and a vibration
 * pattern. Deliberately thin: it holds no state, so a removed timer can never
 * fire, and a restarted one simply reschedules.
 */
class CookTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TIMER_FINISHED) return
        val timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L)
        if (timerId < 0) return
        val step = intent.getIntExtra(EXTRA_STEP_NUMBER, 0)
        val seconds = intent.getLongExtra(EXTRA_TOTAL_SECONDS, 0L)

        CookTimerNotifications.ensureChannel(context)
        CookTimerNotifications.notifyFinished(context, timerId, step, seconds)
        vibrate(context)
    }

    private fun vibrate(context: Context) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService<Vibrator>()
        }
        if (vibrator?.hasVibrator() != true) return
        vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, -1))
    }

    companion object {
        const val ACTION_TIMER_FINISHED = "com.arttvad9r.mealio.action.COOK_TIMER_FINISHED"
        const val EXTRA_TIMER_ID = "timer_id"
        const val EXTRA_STEP_NUMBER = "step_number"
        const val EXTRA_TOTAL_SECONDS = "total_seconds"

        private val PATTERN = longArrayOf(0L, 400L, 250L, 400L, 250L, 600L)
    }
}
