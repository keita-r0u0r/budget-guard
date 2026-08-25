package com.budgetguard.app.notification

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Helpers around the one manual setup step the user has to do: granting Notification Access.
 * There is no runtime-permission dialog for this (unlike POST_NOTIFICATIONS) -- the user has to
 * flip it on in system Settings, which is why the dashboard needs to check this and link them
 * straight there.
 */
object NotificationAccess {

    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun openSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}
