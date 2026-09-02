package com.budgetguard.app.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.budgetguard.app.data.BudgetRepository
import com.budgetguard.app.data.NotificationLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The core detection mechanism: this is what lets BudgetGuard "see" a spend without the user
 * typing anything in. Android calls [onNotificationPosted] for every notification on the device
 * once the user grants Notification Access (Settings > Apps > Special access > Notification
 * access > BudgetGuard). Only apps the user has explicitly added to the monitored list
 * ([com.budgetguard.app.data.BudgetPreferences.monitoredPackages]) can create a transaction.
 *
 * When [com.budgetguard.app.data.BudgetPreferences.logAllNotifications] is on, notifications from
 * *other* apps are still written to the notification log (never counted as spend) purely so the
 * user can discover which package actually announces a purchase for a given payment method.
 *
 * This is one implementation of what the README calls a "spending event source". A future
 * source (email parsing, a bank aggregation API, SMS) would live alongside this file and write
 * into the same [com.budgetguard.app.data.TransactionIngestor], not duplicate this logic.
 */
class SpendingNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val parserRegistry = AmountParserRegistry()

    // Cheap in-memory de-dupe: NotificationListenerService.onNotificationPosted fires again
    // whenever an app *updates* an existing notification (e.g. progress text changes), not just
    // when a brand new one appears. We don't want to double-count the same purchase because its
    // notification got updated. Keyed on (notification key + text), capped to avoid unbounded
    // growth; this is process-lifetime only, which is fine since it's just for de-duping bursts.
    private val recentlySeen = ArrayDeque<String>()
    private val recentlySeenCapacity = 200

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        // Never react to our own "支出¥X / 残り¥Y" notifications -- they contain a yen amount, so
        // without this guard the app would read its own output back as new spending.
        if (packageName == applicationContext.packageName) return

        val repo = BudgetRepository.get(applicationContext)

        serviceScope.launch {
            val monitored = repo.preferences.monitoredPackages.first()
            val isMonitored = packageName in monitored
            val logAll = repo.preferences.logAllNotifications.first()
            if (!isMonitored && !logAll) return@launch

            val extras = sbn.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()

            val fingerprint = "${sbn.key}:$title:$text"
            if (!markSeen(fingerprint)) return@launch

            // Only monitored apps are parsed for an amount; everything else is log-only.
            val amount = if (isMonitored) {
                parserRegistry.parserFor(packageName).parse(title, text)
            } else {
                null
            }

            repo.logNotification(
                NotificationLogEntity(
                    packageName = packageName,
                    title = title,
                    text = text,
                    timestampMillis = sbn.postTime,
                    parsedAmountYen = amount,
                    wasRecordedAsTransaction = amount != null,
                )
            )

            if (amount != null) {
                val result = repo.ingestor.ingest(
                    amountYen = amount,
                    packageName = packageName,
                    sourceType = "notification",
                    rawText = text,
                    timestampMillis = sbn.postTime,
                )
                BalanceNotifier.notifyBalance(applicationContext, result)
                BalanceSurfaces.refresh(applicationContext)
            }
            // amount == null: logged for later tuning in the 通知ログ screen, but not counted as
            // spend -- better to miss a transaction the user notices is missing than to silently
            // mis-count something that wasn't a purchase.
        }
    }

    private fun markSeen(fingerprint: String): Boolean = synchronized(recentlySeen) {
        if (recentlySeen.contains(fingerprint)) return@synchronized false
        recentlySeen.addLast(fingerprint)
        if (recentlySeen.size > recentlySeenCapacity) recentlySeen.removeFirst()
        true
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        // Extension point: could reconcile against getActiveNotifications() here to catch up on
        // notifications still sitting in the shade from while we were disconnected.
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
    }

    companion object {
        /**
         * Set from the bind callbacks so the UI can tell "permission granted but service never
         * bound" (the usual post-reinstall failure) apart from "permission missing". Lives in the
         * same process as the UI, so a plain volatile flag is enough.
         */
        @Volatile
        var isConnected: Boolean = false
            private set
    }
}
