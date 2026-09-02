package com.budgetguard.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.budgetguard.app.data.BudgetRepository
import com.budgetguard.app.util.BudgetPeriod
import kotlinx.coroutines.flow.first

/**
 * Keeps the ambient surfaces honest across midnight.
 *
 * Both supporting figures -- "残り N日" and "1日 ¥X ペース" -- change when the date changes even
 * if the user spent nothing, because both are derived from the days left in the period. Every
 * other refresh path is driven by a detected purchase, so without this the widget shows
 * yesterday's pace all through a quiet morning, which is precisely the failure mode the whole
 * "always-correct ambient balance" idea exists to prevent.
 *
 * Why an alarm and not the widget's own update cycle: [android.appwidget.AppWidgetProviderInfo]
 * `updatePeriodMillis` has a 30-minute floor *and* is silently skipped while the device is in
 * Doze, so it cannot be relied on to fire near midnight on an idle phone. WorkManager has the
 * same Doze caveat for exact timing. `setExactAndAllowWhileIdle` is the only API that both names
 * an instant and is allowed to run during Doze.
 */
object DayRolloverScheduler {

    const val ACTION_DAY_ROLLOVER = "com.budgetguard.app.action.DAY_ROLLOVER"

    private const val TAG = "DayRolloverScheduler"
    private const val REQUEST_CODE = 2001

    /**
     * Fire a few seconds *after* the boundary. Alarms can be delivered a hair early, and at
     * exactly 00:00:00.000 `LocalDate.now()` may still read as yesterday, which would recompute
     * the identical numbers and leave the widget stale for a whole day.
     */
    private const val SAFETY_MARGIN_MILLIS = 5_000L

    /** Arms the next rollover. Safe to call repeatedly; re-arming replaces the pending alarm. */
    suspend fun schedule(context: Context) {
        val resetDay = BudgetRepository.get(context).preferences.budgetResetDay.first()
        val triggerAt = BudgetPeriod.nextRolloverMillis(resetDay) + SAFETY_MARGIN_MILLIS
        scheduleAt(context.applicationContext, triggerAt)
    }

    private fun scheduleAt(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DayRolloverReceiver::class.java).setAction(ACTION_DAY_ROLLOVER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // setExactAndAllowWhileIdle needs SCHEDULE_EXACT_ALARM from API 31, and from API 33 that
        // is not granted by default for apps targeting 33+. Falling back to the inexact variant
        // still wakes the device out of Doze; it just lands in a window after midnight instead of
        // on it, which for a once-a-day number refresh is an acceptable degradation.
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        try {
            if (canBeExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
                )
            } else {
                Log.i(TAG, "Exact alarms not permitted; falling back to inexact rollover refresh")
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
                )
            }
        } catch (e: SecurityException) {
            // Some OEM builds revoke exact-alarm access without canScheduleExactAlarms() saying so.
            Log.w(TAG, "Exact alarm refused, using inexact", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
            )
        }
    }
}
