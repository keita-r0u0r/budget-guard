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
 * access > BudgetGuard). We only act on notifications from apps the user has explicitly added
 * to the monitored list ([com.budgetguard.app.data.BudgetPreferences.monitoredPackages]) --
 * everything else is ignored.
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
        val repo = BudgetRepository.get(applicationContext)

        serviceScope.launch {
            val monitored = repo.preferences.monitoredPackages.first()
            if (packageName !in monitored) return@launch

            val extras = sbn.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()

            val fingerprint = "${sbn.key}:$title:$text"
            if (!markSeen(fingerprint)) return@launch

            val parser = parserRegistry.parserFor(packageName)
            val amount = parser.parse(title, text)

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
        // No-op today. Extension point: could reconcile against sbn history via
        // getActiveNotifications() here if we ever want to catch up on notifications posted
        // while the listener was disconnected.
    }
}
