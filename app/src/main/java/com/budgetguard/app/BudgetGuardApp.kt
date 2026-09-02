package com.budgetguard.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.budgetguard.app.notification.BalanceNotifier
import com.budgetguard.app.notification.BalanceStatusNotifier
import com.budgetguard.app.notification.BalanceSurfaces
import com.budgetguard.app.schedule.DayRolloverScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BudgetGuardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Repaint the shade notification and widget on process start so they aren't left showing
        // yesterday's pace after the app was killed overnight, then make sure the midnight alarm
        // is armed -- this is the catch-all for the cases the receiver can miss (force stop,
        // "clear all" from recents, an OEM task killer).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            BalanceSurfaces.refresh(applicationContext)
            DayRolloverScheduler.schedule(applicationContext)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                BalanceNotifier.CHANNEL_ID,
                getString(R.string.notification_channel_balance_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.notification_channel_balance_desc)
            }
        )

        // Low importance: the persistent readout should never make a sound or interrupt -- it is
        // there to be glanced at, not to demand attention.
        manager.createNotificationChannel(
            NotificationChannel(
                BalanceStatusNotifier.CHANNEL_ID,
                getString(R.string.notification_channel_status_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_status_desc)
                setShowBadge(false)
            }
        )
    }
}
