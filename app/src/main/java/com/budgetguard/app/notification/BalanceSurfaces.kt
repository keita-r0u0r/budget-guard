package com.budgetguard.app.notification

import android.content.Context
import com.budgetguard.app.data.BudgetRepository
import com.budgetguard.app.widget.BalanceWidgetProvider
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Single entry point for refreshing every place the balance is shown outside the app itself:
 * the ongoing notification and the home screen widget.
 *
 * Callers (a detected purchase, the app resuming, a date rollover) shouldn't have to know how
 * many ambient surfaces exist or which of them the user has enabled -- they just say "the numbers
 * changed", and adding a future surface means editing this one function.
 *
 * It also owns [display], the one place the user-visible wording is built. The widget, the shade
 * notification and the in-app dashboard all read from it, because the moment those three drift
 * apart the user is looking at what appears to be three different balances.
 */
object BalanceSurfaces {

    private val yenFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.JAPAN)

    /**
     * Ready-to-render strings for one [BudgetRepository.BudgetStatus].
     *
     * The hierarchy is fixed here and nowhere else: [headline] + [amountText] is the period
     * remainder and is the main number; [remainingDaysText] and [paceText] are the supporting
     * pair. [paceText] is phrased as a pace ("1日 ¥2,500 ペース") on purpose -- phrasing it as a
     * balance ("今日あと ¥2,500") puts two balances on the same card and neither wins.
     */
    data class Display(
        val periodLabel: String,
        val headline: String,
        val amountText: String,
        val remainingDaysText: String,
        val paceText: String,
        /** Share of the period's days already elapsed, 0..100, for RemoteViews' ProgressBar. */
        val progressPercent: Int,
        val isOverBudget: Boolean,
        val isBudgetUnset: Boolean,
    ) {
        /**
         * The shade notification collapses to a single line, so main and supporting figures are
         * folded together with a full-width slash rather than stacked.
         */
        val notificationLine: String
            get() = when {
                isBudgetUnset -> "予算が未設定です"
                isOverBudget -> "⚠️ $headline $amountText ／ $paceText"
                else -> "$headline $amountText ／ $paceText"
            }

        val notificationSubLine: String
            get() = if (isBudgetUnset) "タップして今月の予算を設定してください"
            else "$remainingDaysText ・ $periodLabel"
    }

    fun display(status: BudgetRepository.BudgetStatus): Display {
        val budgetUnset = status.budgetYen <= 0L
        val overBudget = status.isOverBudget
        return Display(
            periodLabel = status.periodLabel,
            headline = when {
                budgetUnset -> "予算未設定"
                overBudget -> "予算オーバー"
                else -> "今月あと"
            },
            amountText = when {
                budgetUnset -> "--"
                overBudget -> "-¥${yenFormat.format(-status.remainingYen)}"
                else -> "¥${yenFormat.format(status.remainingYen)}"
            },
            remainingDaysText = if (budgetUnset) "" else "残り${status.remainingDays}日",
            // Over budget the pace is honestly ¥0/day, which is the point: it keeps the same slot
            // saying the same kind of thing instead of swapping in a different sentence.
            paceText = if (budgetUnset) "タップして設定"
            else "1日 ¥${yenFormat.format(status.dailyAllowanceYen)} ペース",
            progressPercent = (status.elapsedRatio * 100f).roundToInt().coerceIn(0, 100),
            isOverBudget = overBudget,
            isBudgetUnset = budgetUnset,
        )
    }

    suspend fun refresh(context: Context) {
        val repository = BudgetRepository.get(context)
        val status = repository.observeCurrentStatus().first()
        refresh(context, status)
    }

    suspend fun refresh(context: Context, status: BudgetRepository.BudgetStatus) {
        val repository = BudgetRepository.get(context)
        if (repository.preferences.persistentNotificationEnabled.first()) {
            BalanceStatusNotifier.update(context, status)
        } else {
            BalanceStatusNotifier.cancel(context)
        }
        BalanceWidgetProvider.refresh(context, status)
    }
}
