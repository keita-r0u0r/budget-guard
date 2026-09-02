package com.budgetguard.app.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/**
 * Helpers around the one manual setup step the user has to do: granting Notification Access.
 * There is no runtime-permission dialog for this (unlike POST_NOTIFICATIONS) -- the user has to
 * flip it on in system Settings, which is why the dashboard needs to check this and link them
 * straight there.
 */
object NotificationAccess {

    /** Whether the user has granted notification access in system Settings. */
    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /**
     * Whether the listener is actually *bound and receiving* right now. This is deliberately
     * separate from [isEnabled]: the common failure mode after reinstalling the app is that the
     * permission still reads as granted while the service was never rebound, so notifications
     * silently never arrive. Distinguishing the two is the difference between "go grant the
     * permission" and "the permission is fine, the service just needs a kick".
     */
    fun isListenerConnected(): Boolean = SpendingNotificationListenerService.isConnected

    fun openSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /**
     * Asks the system to bind the listener again, which is what normally only happens by toggling
     * notification access off and on in Settings by hand.
     */
    fun requestRebind(context: Context) {
        NotificationListenerService.requestRebind(
            ComponentName(context, SpendingNotificationListenerService::class.java)
        )
    }
}
