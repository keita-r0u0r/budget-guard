package com.budgetguard.app.data

import android.content.Context
import com.budgetguard.app.util.BudgetPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

/**
 * UI-facing façade over Room + DataStore. [MainActivity]/ViewModels should only ever talk to
 * this class, never to the DAOs or DataStore directly, so storage details can keep changing
 * underneath without touching the UI layer.
 */
class BudgetRepository(context: Context) {

    private val db = AppDatabase.get(context)
    val preferences = BudgetPreferences(context)
    val ingestor = TransactionIngestor(db.transactionDao(), preferences)

    data class BudgetStatus(
        val periodKey: String,
        val periodLabel: String,
        val budgetYen: Long,
        val spentYen: Long,
    ) {
        val remainingYen: Long get() = budgetYen - spentYen
        val isOverBudget: Boolean get() = remainingYen < 0
        val spentRatio: Float get() = if (budgetYen <= 0) 0f else (spentYen.toFloat() / budgetYen).coerceIn(0f, 1f)
    }

    /**
     * Live "how much budget is left this period" for the dashboard screen. Recomputes the
     * period key from [BudgetPreferences.budgetResetDay] whenever the budget, reset day, or the
     * month's transaction total changes, then re-subscribes to that period's running total.
     */
    fun observeCurrentStatus(): Flow<BudgetStatus> =
        combine(
            preferences.monthlyBudgetYen,
            preferences.budgetResetDay,
        ) { budget, resetDay -> budget to resetDay }
            .let { budgetAndResetDay ->
                channelFlow {
                    budgetAndResetDay.collectLatest { (budget, resetDay) ->
                        val periodKey = BudgetPeriod.currentKey(resetDay)
                        db.transactionDao().observeMonthTotal(periodKey).collect { spent ->
                            send(
                                BudgetStatus(
                                    periodKey = periodKey,
                                    periodLabel = BudgetPeriod.label(periodKey),
                                    budgetYen = budget,
                                    spentYen = spent,
                                )
                            )
                        }
                    }
                }
            }

    fun observeRecentTransactions(limit: Int = 50): Flow<List<TransactionEntity>> =
        db.transactionDao().observeRecent(limit)

    fun observeRecentNotificationLogs(limit: Int = 200): Flow<List<NotificationLogEntity>> =
        db.notificationLogDao().observeRecent(limit)

    suspend fun logNotification(log: NotificationLogEntity) {
        db.notificationLogDao().insert(log)
    }

    suspend fun deleteTransaction(id: Long) {
        db.transactionDao().deleteById(id)
    }

    companion object {
        @Volatile
        private var instance: BudgetRepository? = null

        fun get(context: Context): BudgetRepository =
            instance ?: synchronized(this) {
                instance ?: BudgetRepository(context.applicationContext).also { instance = it }
            }
    }
}
