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
import com.budgetguard.app.data.TransactionIngestor
import com.budgetguard.app.ui.MainActivity
import java.util.concurrent.atomic.AtomicInteger

/**
 * Posts the "支出¥X / 残り予算¥Y" local notification every time [TransactionIngestor] records a
 * new spend. This is the whole point of the app, so it's kept deliberately simple: one function,
 * called right after ingest.
 */
object BalanceNotifier {

    const val CHANNEL_ID = "balance_channel"
    private val nextNotificationId = AtomicInteger(1000)

    fun notifyBalance(context: Context, result: TransactionIngestor.IngestResult) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            // User hasn't granted POST_NOTIFICATIONS (Android 13+). We still recorded the
            // transaction; we just can't surface a system notification for it right now.
            return
        }

        val remaining = result.remainingYen
        val amountStr = "%,d".format(result.amountYen)
        val spentStr = "%,d".format(result.periodSpentYen)
        val budgetStr = "%,d".format(result.periodBudgetYen)

        val title = if (remaining >= 0) {
            "支出 ¥$amountStr を検知"
        } else {
            "⚠️ 予算オーバー中（支出 ¥$amountStr）"
        }
        val body = if (remaining >= 0) {
            "残り予算: ¥%,d（使用 ¥$spentStr / 予算 ¥$budgetStr）".format(remaining)
        } else {
            "予算を ¥%,d 超過しています（使用 ¥$spentStr / 予算 ¥$budgetStr）".format(-remaining)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // TODO: swap for a proper monochrome status-bar icon before shipping past prototype.
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(nextNotificationId.getAndIncrement(), notification)
    }
}
