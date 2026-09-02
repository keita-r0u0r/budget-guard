package com.budgetguard.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.budgetguard.app.data.NotificationLogEntity
import com.budgetguard.app.notification.NotificationAccess
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val yenFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.JAPAN)
private val timeFormat = SimpleDateFormat("M/d HH:mm:ss", Locale.JAPAN)
private val fullTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN)

/** Entries included in a shared report. Keeps the intent payload well under the Binder limit. */
private const val REPORT_MAX_ENTRIES = 80

/**
 * Raw feed of notifications BudgetGuard has seen, with whether an amount was parsed out of each.
 * Two jobs: (1) find out which package actually announces a purchase for a given payment method,
 * by flipping on "すべてのアプリの通知を記録" and making a test payment, and (2) tune
 * [com.budgetguard.app.notification.AmountParser] per app -- if a real purchase shows 未検出 here,
 * copy its exact text and add a parser for that package in AmountParserRegistry.
 */
@Composable
fun NotificationLogScreen(
    logs: List<NotificationLogEntity>,
    logAllNotifications: Boolean,
    onToggleLogAll: (Boolean) -> Unit,
    monitoredPackages: Set<String>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "すべてのアプリの通知を記録",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            "どのアプリが支出通知を出しているか調べる用。ONにして実際に支払うと、通知を出したアプリがここに出ます。特定できたら「監視アプリ」タブでそのアプリにチェックを入れてください。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Switch(checked = logAllNotifications, onCheckedChange = onToggleLogAll)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val report = buildLogReport(context, logs, monitoredPackages)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "BudgetGuard 通知ログ")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(send, "ログを送る"))
                    },
                ) {
                    Text("ログを送る")
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(
                            AnnotatedString(buildLogReport(context, logs, monitoredPackages))
                        )
                    },
                ) {
                    Text("コピー")
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Text(
                    "まだ通知を受信していません。ホームタブの「動作診断」で通知リスナーが接続中になっているか確認してください。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(logs, key = { it.id }) { log ->
            val isMonitored = monitoredPackages.contains(log.packageName)
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (isMonitored) "${log.packageName} ・監視中" else log.packageName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isMonitored) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                when {
                                    log.parsedAmountYen != null ->
                                        "¥${yenFormat.format(log.parsedAmountYen)} を記録"
                                    isMonitored -> "金額を検出できず"
                                    else -> "監視対象外（記録のみ）"
                                }
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (log.wasRecordedAsTransaction) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Plain-text dump of the log plus the environment around it (permission state, which packages are
 * monitored). Both of those matter when reading the log: an empty log means something different
 * depending on whether the listener was even connected.
 */
private fun buildLogReport(
    context: Context,
    logs: List<NotificationLogEntity>,
    monitoredPackages: Set<String>,
): String = buildString {
    appendLine("=== BudgetGuard 通知ログ ===")
    appendLine("出力日時: ${fullTimeFormat.format(Date())}")
    appendLine("端末: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
    appendLine("通知アクセス: ${if (NotificationAccess.isEnabled(context)) "許可済み" else "未許可"}")
    appendLine("通知リスナー: ${if (NotificationAccess.isListenerConnected()) "接続中" else "未接続"}")
    appendLine("監視中のアプリ (${monitoredPackages.size}件):")
    monitoredPackages.sorted().forEach { appendLine("  - $it") }
    appendLine("記録件数: ${logs.size}")
    if (logs.size > REPORT_MAX_ENTRIES) {
        appendLine("（新しい方から $REPORT_MAX_ENTRIES 件のみ出力）")
    }
    appendLine()

    logs.take(REPORT_MAX_ENTRIES).forEachIndexed { index, log ->
        appendLine("[${index + 1}] ${fullTimeFormat.format(Date(log.timestampMillis))}")
        appendLine("  pkg   : ${log.packageName}${if (log.packageName in monitoredPackages) " (監視中)" else ""}")
        appendLine("  title : ${log.title ?: "(なし)"}")
        appendLine("  text  : ${log.text ?: "(なし)"}")
        appendLine("  parsed: ${log.parsedAmountYen?.toString() ?: "未検出"} / 記録: ${if (log.wasRecordedAsTransaction) "あり" else "なし"}")
        appendLine("---")
    }
}
