package com.budgetguard.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.budgetguard.app.notification.BalanceSurfaces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Repaints the ambient surfaces when the calendar moves under them, then re-arms the next alarm.
 *
 * Three triggers land here:
 *  - [DayRolloverScheduler.ACTION_DAY_ROLLOVER], the alarm itself. `setExactAndAllowWhileIdle` is
 *    one-shot, so rescheduling from inside the handler is what makes it recurring.
 *  - `ACTION_BOOT_COMPLETED`, because a reboot wipes every pending alarm.
 *  - `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED`, because an alarm armed for "midnight" is
 *    armed for an absolute instant, and moving the clock or the zone moves midnight away from it.
 */
class DayRolloverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Reading the budget touches Room and DataStore, too slow for a receiver's synchronous
        // window, so hold the broadcast open until the refresh finishes.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                BalanceSurfaces.refresh(appContext)
            } finally {
                // Re-arm even if the refresh threw, otherwise one bad night ends the chain.
                runCatching { DayRolloverScheduler.schedule(appContext) }
                pendingResult.finish()
            }
        }
    }
}
