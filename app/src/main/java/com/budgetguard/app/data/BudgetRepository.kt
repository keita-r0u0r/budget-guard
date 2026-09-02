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
        val remainingDays: Int,
        val totalDays: Int,
    ) {
        val remainingYen: Long get() = budgetYen - spentYen
        val isOverBudget: Boolean get() = remainingYen < 0
        val spentRatio: Float get() = if (budgetYen <= 0) 0f else (spentYen.toFloat() / budgetYen).coerceIn(0f, 1f)

        /**
         * How much of the period's *time* is gone, 0f..1f. Shown as the progress bar on every
         * surface, deliberately in preference to [spentRatio]: a money bar would only restate the
         * remaining figure printed right above it, whereas a day bar next to a money figure is
         * what makes "spending faster than the calendar" legible at a glance.
         *
         * Counts whole days already finished, so it reads 0 on day one and never quite reaches
         * full while today is still spendable.
         */
        val elapsedRatio: Float
            get() = if (totalDays <= 0) 0f
            else ((totalDays - remainingDays).toFloat() / totalDays).coerceIn(0f, 1f)

        /**
         * The rest of the budget spread evenly over the days left -- surfaced as
         * "1日 ¥X ペース", never as "今日あと ¥X".
         *
         * The headline number is the period remainder ([remainingYen]); this is its companion.
         * A period remainder alone can't be acted on without knowing whether 3 days or 20 remain,
         * which is exactly what this figure (plus [remainingDays]) supplies. Wording it as a pace
         * rather than a balance matters: two balances side by side compete for attention and
         * neither reads as the main number.
         *
         * Overspending today shrinks tomorrow's figure by itself, so the pace still self-corrects.
         */
        val dailyAllowanceYen: Long
            get() = if (remainingYen <= 0) 0L else remainingYen / remainingDays
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
                                    remainingDays = BudgetPeriod.remainingDays(resetDay),
                                    totalDays = BudgetPeriod.totalDays(periodKey, resetDay),
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
