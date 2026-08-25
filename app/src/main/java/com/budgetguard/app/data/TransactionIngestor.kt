package com.budgetguard.app.data

import com.budgetguard.app.util.BudgetPeriod
import kotlinx.coroutines.flow.first

/**
 * Single funnel that every spending-detection source writes into.
 *
 * Today the only producer is [com.budgetguard.app.notification.SpendingNotificationListenerService].
 * The intent is that a future source -- parsing "ご利用のお知らせ" emails via Gmail API, polling a
 * bank aggregation API, reading SMS, whatever -- implements its own detector and calls
 * [ingest] with what it found, without needing to know anything about Room, DataStore, or how
 * the remaining-balance notification gets built. That keeps detection (notification listener,
 * email parser, ...) decoupled from accounting (this class) and from notifying the user
 * ([com.budgetguard.app.notification.BalanceNotifier]).
 */
class TransactionIngestor(
    private val transactionDao: TransactionDao,
    private val preferences: BudgetPreferences,
) {

    data class IngestResult(
        val amountYen: Long,
        val periodKey: String,
        val periodBudgetYen: Long,
        val periodSpentYen: Long,
    ) {
        val remainingYen: Long get() = periodBudgetYen - periodSpentYen
    }

    /**
     * Records a spending event and returns the up-to-date remaining budget for the period it
     * fell into, so the caller can immediately show a "支出¥X / 残り¥Y" notification.
     */
    suspend fun ingest(
        amountYen: Long,
        packageName: String,
        sourceType: String,
        rawText: String?,
        timestampMillis: Long = System.currentTimeMillis(),
    ): IngestResult {
        val resetDay = preferences.budgetResetDay.first()
        val periodKey = BudgetPeriod.keyFor(timestampMillis, resetDay)

        transactionDao.insert(
            TransactionEntity(
                amountYen = amountYen,
                packageName = packageName,
                sourceType = sourceType,
                rawText = rawText,
                timestampMillis = timestampMillis,
                yearMonth = periodKey,
            )
        )

        val budget = preferences.monthlyBudgetYen.first()
        val spent = transactionDao.observeMonthTotal(periodKey).first()

        return IngestResult(
            amountYen = amountYen,
            periodKey = periodKey,
            periodBudgetYen = budget,
            periodSpentYen = spent,
        )
    }
}
