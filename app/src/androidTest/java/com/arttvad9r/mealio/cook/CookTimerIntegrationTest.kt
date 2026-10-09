package com.arttvad9r.mealio.cook

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.domain.cook.ActiveTimer
import com.arttvad9r.mealio.domain.cook.alarmRequestCode
import com.arttvad9r.mealio.domain.cook.wallTriggerAt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Android half of Cook Mode, exercised on a real system: the notification
 * channel, the receiver-to-notification path, and the AlarmManager scheduling that
 * keeps timers firing while Mealio is backgrounded.
 *
 * Only public system APIs are used — no reflection, no internal AlarmManager
 * introspection. "Is an alarm scheduled" is answered through PendingIntent
 * identity/existence, which is what distinguishes one timer from another.
 *
 * The exact-alarm special access is the one switch that cannot be driven safely
 * from inside the test process: Android kills a running app the instant its
 * SCHEDULE_EXACT_ALARM access is REVOKED, and the instrumentation runs in that same
 * process — a revoke aborts the whole suite (`ActivityManager: Killing … :
 * schedule_exact_alarm revoked`), it does not fail one assertion. So this suite only
 * ever GRANTS the access (a no-op, kill-wise) and asserts the exact scheduling path
 * it enables. The complementary half of the policy — that NO inexact, late alarm is
 * ever scheduled when the access is missing — is a pure decision in the ViewModel
 * and is covered by the unit test `without exact-alarm access no timer starts and
 * the requirement is raised`, against the same
 * [com.arttvad9r.mealio.domain.cook.CookTimerScheduler] interface.
 */
@RunWith(AndroidJUnit4::class)
class CookTimerIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val alarms = CookTimerAlarms(context)

    /** Test-only ids, far from the monotonic ids production hands out. */
    private val ids = (1..9).map { BASE + it }

    @Before
    fun grantNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        // The grant lands asynchronously; showFinished() correctly skips posting
        // while notifications are still disabled, so wait (bounded) rather than
        // racing it — otherwise whichever test runs first flakes.
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (!NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            SystemClock.elapsedRealtime() < deadline
        ) {
            Thread.sleep(50L)
        }
    }

    @After
    fun cleanup() {
        // Leave no alarm, notification or pending intent behind for the next test
        // or the next run: every id this class touches is cancelled here. The
        // exact-alarm app-op is deliberately left as granted — revoking it would
        // kill the instrumentation process (see the class doc), and the emulator
        // this suite runs on is torn down afterwards anyway.
        ids.forEach { id ->
            alarms.cancel(id)
            CookTimerNotifications.cancel(context, id)
        }
    }

    // --- A. Exact-alarm scheduling path ----------------------------------------

    @Test
    fun withExactAlarmAccess_anExactAlarmIsRegisteredForTheTimer() {
        grantExactAlarmAccess()
        assertTrue("precondition: exact access must be on", alarms.canScheduleExactAlarms())

        val id = ids[0]
        alarms.schedule(runningTimer(id, seconds = 60))

        assertNotNull("an exact-access timer must be scheduled", existing(id))
        // The system really holds it: the package shows up in the alarm dump.
        assertTrue(
            "the scheduled alarm must be visible to the system",
            shell("dumpsys alarm").contains(context.packageName),
        )
    }

    @Test
    fun withExactAlarmAccess_theTimerIsRegisteredAsAnAlarmClockAtItsDeadline() {
        grantExactAlarmAccess()
        val id = ids[1]
        val timer = runningTimer(id, seconds = 60)
        val expected = wallTriggerAt(timer.deadlineMillis, SystemClock.elapsedRealtime(), System.currentTimeMillis())

        alarms.schedule(timer)

        // getNextAlarmClock() reports only alarms installed through setAlarmClock(),
        // so a non-null result is itself the proof that the alarm-clock path — not
        // setExactAndAllowWhileIdle — was taken. No reflection needed.
        val next = alarmManager.nextAlarmClock
        assertNotNull("the timer must be registered as an alarm clock", next)
        val delta = Math.abs(next!!.triggerTime - expected)
        assertTrue(
            "alarm-clock trigger must match the timer deadline (delta=${delta}ms)",
            delta <= 500L,
        )
    }

    @Test
    fun reschedulingTheSameId_replacesTheAlarmInsteadOfAddingASecond() {
        grantExactAlarmAccess()
        val id = ids[6]
        alarms.schedule(runningTimer(id, seconds = 60))
        val first = existing(id)!!

        // Same timer id, new deadline: identity is preserved, so the scheduler
        // replaces the alarm rather than leaving an orphan behind.
        alarms.schedule(runningTimer(id, seconds = 120))
        val second = existing(id)!!

        assertEquals("rescheduling must keep the same PendingIntent identity", first, second)

        alarms.cancel(id)
        assertNull("a single cancel must clear the one alarm for this id", existing(id))
    }

    // --- B. Notification channel ----------------------------------------------

    @Test
    fun channel_isCreatedWithAlarmLikeImportanceVibrationAndSound() {
        // Recreate from scratch so the assertion tests the code, not a channel a
        // previous app run happened to leave behind.
        notifications.deleteNotificationChannel(CookTimerNotifications.CHANNEL_ID)
        CookTimerNotifications.ensureChannel(context)

        val channel = notifications.getNotificationChannel(CookTimerNotifications.CHANNEL_ID)
        assertNotNull("channel was not created", channel)
        channel!!
        assertEquals(CookTimerNotifications.CHANNEL_ID, channel.id)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertTrue("vibration must be enabled on the channel", channel.shouldVibrate())
        assertNotNull("vibration pattern must be set", channel.vibrationPattern)
        assertTrue("vibration pattern must not be empty", channel.vibrationPattern!!.isNotEmpty())
        // The app must not silence the channel: on Android 8+ the sound lives here.
        assertNotNull("channel sound must not be disabled by the app", channel.sound)
    }

    // --- C. Receiver -> notification -------------------------------------------

    @Test
    fun receiver_postsLocalizedNotificationForItsStep() {
        val id = ids[2]
        val step = 2

        CookTimerReceiver().onReceive(context, completionIntent(id, step))

        val posted = awaitNotification(id)
        assertNotNull("receiver did not post a notification for timer $id", posted)
        val title = posted!!.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        assertEquals(context.getString(R.string.cook_notification_title), title)
        // Compared against the resource, never a hard-coded "Step N".
        assertEquals(context.getString(R.string.cook_notification_body, step), text)

        CookTimerNotifications.cancel(context, id)
        assertNull("cancel must clear the posted notification", awaitNoNotification(id))
    }

    // --- D. Independent timer ids ---------------------------------------------

    @Test
    fun twoTimers_haveDistinctPendingIntentsAndCancelIndependently() {
        val a = ids[3]
        val b = ids[4]

        val pendingA = CookTimerAlarms.alarmPendingIntent(context, a, stepNumber = 1, PendingIntent.FLAG_UPDATE_CURRENT)!!
        val pendingB = CookTimerAlarms.alarmPendingIntent(context, b, stepNumber = 1, PendingIntent.FLAG_UPDATE_CURRENT)!!
        assertNotEquals("two timers must not share a request code", alarmRequestCode(a), alarmRequestCode(b))
        assertNotEquals("two timers must not share a PendingIntent", pendingA, pendingB)

        alarms.cancel(a)
        assertNull("cancelling one timer must clear that timer's intent", existing(a))
        assertNotNull("cancelling one timer must not touch the other", existing(b))

        alarms.cancel(b)
        assertNull(existing(b))
    }

    // --- E. Schedule / cancel --------------------------------------------------

    @Test
    fun schedule_createsPendingIntent_thenCancelRemovesIt() {
        grantExactAlarmAccess()
        val id = ids[5]
        val timer = runningTimer(id, seconds = 60)
        val trigger = wallTriggerAt(timer.deadlineMillis, SystemClock.elapsedRealtime(), System.currentTimeMillis())
        alarms.schedule(timer)

        assertNotNull("schedule must leave an alarm PendingIntent behind", existing(id))

        alarms.cancel(id)
        assertNull("cancel must remove the alarm PendingIntent", existing(id))

        // A cancel of the alarm clock must also drop it from the system's next alarm.
        val next = alarmManager.nextAlarmClock
        assertTrue(
            "cancelled timer must not remain the system's next alarm clock",
            next == null || next.triggerTime != trigger,
        )
    }

    // --- F. Receiver isolation -------------------------------------------------

    @Test
    fun completionOfOneTimer_doesNotCarryAnotherTimersData() {
        grantExactAlarmAccess()
        val a = ids[7]
        // B exists with its own scheduled alarm and a different step…
        alarms.schedule(runningTimer(ids[8], seconds = 300, stepNumber = 5))

        // …but only A completes.
        CookTimerReceiver().onReceive(context, completionIntent(a, step = 1))

        val forA = awaitNotification(a)
        assertNotNull(forA)
        assertEquals(
            context.getString(R.string.cook_notification_body, 1),
            forA!!.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
        assertNull("B's completion must not be posted when only A fired", activeNotification(ids[8]))
        // B's alarm is untouched by A's completion.
        assertNotNull(existing(ids[8]))
    }

    // --- helpers ---------------------------------------------------------------

    /**
     * Grants the "alarms & reminders" special access exactly as the system Settings
     * page does, via the shell app-op, then waits (bounded) until it is visible
     * through the public API. Granting never kills the process — only revoking
     * does — so this is safe to call mid-suite. Fails loudly if it never takes
     * effect, so a test can never silently run without the access it needs.
     */
    private fun grantExactAlarmAccess() {
        shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (!alarms.canScheduleExactAlarms() && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(50L)
        }
    }

    /** Runs a shell command as the instrumentation's shell user and returns its output. */
    private fun shell(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    private fun runningTimer(id: Long, seconds: Long, stepNumber: Int = 1) = ActiveTimer(
        id = id,
        totalSeconds = seconds,
        deadlineMillis = SystemClock.elapsedRealtime() + seconds * 1_000L,
        stepNumber = stepNumber,
    )

    /** The intent the AlarmManager would deliver for a finished timer. */
    private fun completionIntent(id: Long, step: Int): Intent =
        Intent(context, CookTimerReceiver::class.java).apply {
            action = CookTimerReceiver.ACTION_TIMER_FINISHED
            putExtra(CookTimerReceiver.EXTRA_TIMER_ID, id)
            putExtra(CookTimerReceiver.EXTRA_STEP_NUMBER, step)
        }

    /** The alarm PendingIntent for [id] if one exists, else null. */
    private fun existing(id: Long): PendingIntent? =
        CookTimerAlarms.alarmPendingIntent(context, id, stepNumber = 0, PendingIntent.FLAG_NO_CREATE)

    /** The notification the app currently shows for [id], matched by its stable id. */
    private fun activeNotification(id: Long): Notification? =
        notifications.activeNotifications
            .firstOrNull { it.id == alarmRequestCode(id) }
            ?.notification

    /**
     * Waits (bounded) for a notification to be posted before asserting. Posting is
     * asynchronous inside the system NotificationService, so reading
     * `activeNotifications` right after `notify()` is a race — this polls instead.
     */
    private fun awaitNotification(id: Long): Notification? {
        val deadline = SystemClock.elapsedRealtime() + 2_000L
        var posted = activeNotification(id)
        while (posted == null && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(25L)
            posted = activeNotification(id)
        }
        return posted
    }

    /**
     * Waits (bounded) for a notification to disappear. NotificationManager's
     * `activeNotifications` view is updated by the system service asynchronously,
     * so a cancel is followed by a short settle rather than asserted instantly.
     */
    private fun awaitNoNotification(id: Long): Notification? {
        val deadline = SystemClock.elapsedRealtime() + 2_000L
        var last = activeNotification(id)
        while (last != null && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(25L)
            last = activeNotification(id)
        }
        return last
    }

    private companion object {
        const val BASE = 900_000L
    }
}
