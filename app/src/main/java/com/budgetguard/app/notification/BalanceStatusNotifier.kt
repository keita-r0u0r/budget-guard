package com.budgetguard.app.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.budgetguard.app.data.BudgetRepository
import com.budgetguard.app.ui.MainActivity

/**
 * The always-there balance readout in the notification shade.
 *
 * Distinct from [BalanceNotifier], which fires once per detected purchase: this one is a single
 * silent, ongoing notification that is rewritten in place whenever the numbers change. It exists
 * because a balance you have to open an app to see is a balance you don't check -- putting it in
 * the shade means it's in front of the user every time they pull it down for anything else.
 */
object BalanceStatusNotifier {

    const val CHANNEL_ID = "balance_status_channel"
    private const val NOTIFICATION_ID = 1

    fun update(context: Context, status: BudgetRepository.BudgetStatus) {
        if (!canPostNotifications(context)) return

        // Wording lives in BalanceSurfaces.display() so this line, the widget and the dashboard
        // can never drift apart. Do not hand-build strings here.
        val display = BalanceSurfaces.display(status)
        val title = display.notificationLine
        val body = display.notificationSubLine

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
