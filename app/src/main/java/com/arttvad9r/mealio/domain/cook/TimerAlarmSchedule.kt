package com.arttvad9r.mealio.domain.cook

/**
 * Pure arithmetic behind the background timer alarms, kept free of Android so it
 * is unit-tested directly. The Android side (AlarmManager) only consumes these.
 */

/**
 * The wall-clock instant an alarm should fire for a timer whose deadline is a
 * monotonic reading: AlarmManager works on `RTC`, the timer on `elapsedRealtime`.
 * A deadline already in the past maps to "now", so it fires immediately rather
 * than never.
 */
fun wallTriggerAt(deadlineMillis: Long, nowElapsed: Long, nowWall: Long): Long =
    nowWall + (deadlineMillis - nowElapsed).coerceAtLeast(0L)

/**
 * A stable, distinct request/notification id for a timer. Timers get monotonic
 * ids, so their alarms and notifications never collide.
 */
fun alarmRequestCode(timerId: Long): Int = (timerId % Int.MAX_VALUE).toInt()
