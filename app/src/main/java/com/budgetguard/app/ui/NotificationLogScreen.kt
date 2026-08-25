package com.budgetguard.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.budgetguard.app.data.NotificationLogEntity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val yenFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.JAPAN)
private val timeFormat = SimpleDateFormat("M/d HH:mm:ss", Locale.JAPAN)

/**
 * Raw feed of every notification BudgetGuard has seen from monitored apps, with whether an
 * amount was parsed out of it. This is the debugging tool for tuning [com.budgetguard.app
 * .notification.AmountParser] per app: if a real purchase shows "未検出" here, copy its exact
 * text and add/adjust a parser for that package in AmountParserRegistry.
 */
@Composable
fun NotificationLogScreen(
    logs: List<NotificationLogEntity>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text(
                "監視対象アプリの通知履歴（金額の解析結果つき）。解析精度の調整に使ってください。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        if (logs.isEmpty()) {
            item {
                Text(
                    "まだ通知を受信していません。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(logs, key = { it.id }) { log ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        log.packageName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (!log.title.isNullOrBlank()) {
                        Text(log.title, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!log.text.isNullOrBlank()) {
                        Text(log.text, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = buildString {
                            append(timeFormat.format(Date(log.timestampMillis)))
                            append(" ・ ")
                            append(
                                if (log.parsedAmountYen != null) "¥${yenFormat.format(log.parsedAmountYen)} を記録"
                                else "金額未検出"
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (log.wasRecordedAsTransaction) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
