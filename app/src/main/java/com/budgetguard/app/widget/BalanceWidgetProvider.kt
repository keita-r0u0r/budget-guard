package com.budgetguard.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.budgetguard.app.R
import com.budgetguard.app.data.BudgetRepository
import com.budgetguard.app.notification.BalanceSurfaces
import com.budgetguard.app.schedule.DayRolloverScheduler
import com.budgetguard.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home screen widget showing the period remainder, with the per-day pace underneath it.
 *
 * The lock screen and home screen are the surfaces a phone owner sees most often without
 * intending to, which makes this the cheapest possible way to keep the number in front of them.
 * Deliberately built on RemoteViews rather than Glance: the content is four lines and a bar, and
 * RemoteViews avoids pulling in an extra dependency for that.
 *
 * Every string it renders comes from [BalanceSurfaces.display]; this class only decides sizes,
 * colours and the progress value.
 */
class BalanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Reading the budget touches Room and DataStore, which is too slow for a broadcast
        // receiver's synchronous window, so hold the broadcast open until the read finishes.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val status = BudgetRepository.get(context).observeCurrentStatus().first()
                appWidgetManager.updateAppWidget(appWidgetIds, buildViews(context, status))
                // Placing the widget is exactly the moment the day-rollover alarm starts to
                // matter, and re-arming an already-armed alarm is free.
                DayRolloverScheduler.schedule(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {

        /** Pushes fresh numbers to every placed instance of the widget. No-op if none exist. */
        fun refresh(context: Context, status: BudgetRepository.BudgetStatus) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BalanceWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, buildViews(context, status))
        }

        /**
         * RemoteViews has no autoSizeTextType, so the headline size is picked by hand from the
         * rendered length. "¥300,000" is 8 characters and still fits at 40sp on a 4x2 cell;
         * beyond that it has to step down or it clips at the ellipsis.
         */
        private fun amountTextSizeSp(amountText: String): Float = when {
            amountText.length <= 8 -> 40f
            amountText.length <= 10 -> 32f
            else -> 26f
        }

        private fun buildViews(
            context: Context,
            status: BudgetRepository.BudgetStatus,
        ): RemoteViews {
            val display = BalanceSurfaces.display(status)
            val amountColor = ContextCompat.getColor(
                context,
                if (display.isOverBudget) R.color.widget_over else R.color.widget_text_primary,
            )

            return RemoteViews(context.packageName, R.layout.widget_balance).apply {
                setTextViewText(R.id.widget_label, display.headline)
                setTextViewText(R.id.widget_amount, display.amountText)
                setTextViewTextSize(
                    R.id.widget_amount,
                    TypedValue.COMPLEX_UNIT_SP,
                    amountTextSizeSp(display.amountText),
                )
                setTextColor(R.id.widget_amount, amountColor)
                setProgressBar(R.id.widget_progress, 100, display.progressPercent, false)
                setTextViewText(R.id.widget_days, display.remainingDaysText)
                setTextViewText(R.id.widget_pace, display.paceText)

                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
        }
    }
}
