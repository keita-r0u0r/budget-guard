package com.budgetguard.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetguard.app.data.BudgetRepository
import com.budgetguard.app.data.TransactionEntity
import com.budgetguard.app.notification.BalanceSurfaces
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val yenFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.JAPAN)
private val timeFormat = SimpleDateFormat("M/d HH:mm", Locale.JAPAN)

@Composable
fun DashboardScreen(
    status: BudgetRepository.BudgetStatus?,
    transactions: List<TransactionEntity>,
    notificationAccessEnabled: Boolean,
    listenerConnected: Boolean,
    onOpenNotificationAccessSettings: () -> Unit,
    onRequestRebind: () -> Unit,
    onAddTestTransaction: () -> Unit,
    persistentNotificationEnabled: Boolean,
    onTogglePersistentNotification: (Boolean) -> Unit,
    onSetBudget: (Long) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showBudgetDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!notificationAccessEnabled) {
            item {
                NotificationAccessWarningCard(onOpenNotificationAccessSettings)
            }
        }

        item {
            DiagnosticsCard(
                notificationAccessEnabled = notificationAccessEnabled,
                listenerConnected = listenerConnected,
                onOpenSettings = onOpenNotificationAccessSettings,
                onRequestRebind = onRequestRebind,
                onAddTestTransaction = onAddTestTransaction,
            )
        }

        item {
            BalanceCard(
                status = status,
                onEditBudget = { showBudgetDialog = true },
            )
        }

        item {
            PersistentNotificationToggle(
                enabled = persistentNotificationEnabled,
                onToggle = onTogglePersistentNotification,
            )
        }

        item {
            Text(
                "最近の支出",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (transactions.isEmpty()) {
            item {
                Text(
                    "まだ支出は記録されていません。監視アプリからの通知を待つか、監視アプリ選択タブで対象アプリを追加してください。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(transactions, key = { it.id }) { tx ->
                TransactionRow(tx, onDelete = { onDeleteTransaction(tx.id) })
            }
        }
    }

    if (showBudgetDialog) {
        BudgetEditDialog(
            initialValue = status?.budgetYen ?: 0L,
            onDismiss = { showBudgetDialog = false },
            onConfirm = {
                onSetBudget(it)
                showBudgetDialog = false
            },
        )
    }
}

@Composable
private fun NotificationAccessWarningCard(onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    "  通知アクセスが未許可です",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                "支出通知を検知するには、システム設定で本アプリに通知アクセスを許可してください。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Button(onClick = onOpenSettings) {
                Text("設定を開く")
            }
        }
    }
}

@Composable
private fun BalanceCard(
    status: BudgetRepository.BudgetStatus?,
    onEditBudget: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                status?.periodLabel ?: "-",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Same order and the same words as the widget and the shade notification, and for
            // the same reason: the period remainder leads, the per-day figure supports it as a
            // *pace* rather than a second balance. If this screen disagreed with the widget the
            // user would be looking at what reads as two different balances.
            val display = status?.let { BalanceSurfaces.display(it) }
            val overBudget = status?.isOverBudget == true
            Text(
                display?.headline ?: "今月あと",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                display?.amountText ?: "--",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (overBudget) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(10.dp))
            // Days elapsed, not money spent. A money bar would only restate the number directly
            // above it; against the money figure, a day bar is what shows the gap between how
            // fast the month is going and how fast the budget is.
            LinearProgressIndicator(
                progress = { status?.elapsedRatio ?: 0f },
                modifier = Modifier.fillMaxWidth(),
                color = if (overBudget) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    display?.remainingDaysText ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    display?.paceText ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "使用 ¥${yenFormat.format(status?.spentYen ?: 0L)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "予算 ¥${yenFormat.format(status?.budgetYen ?: 0L)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onEditBudget, modifier = Modifier.padding(top = 12.dp)) {
                Text("予算を編集")
            }
        }
    }
}

@Composable
private fun PersistentNotificationToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("残額を通知に常時表示", style = MaterialTheme.typography.titleSmall)
                Text(
                    "通知シェードに「今月あと¥○○ ／ 1日 ¥△△ ペース」を出しっぱなしにします。音は鳴りません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun TransactionRow(tx: TransactionEntity, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("¥${yenFormat.format(tx.amountYen)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${tx.packageName} ・ ${timeFormat.format(Date(tx.timestampMillis))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDelete) {
                Text("削除")
            }
        }
    }
}

@Composable
private fun BudgetEditDialog(
    initialValue: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var text by remember { mutableStateOf(if (initialValue > 0) initialValue.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("月の予算を設定") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() } },
                label = { Text("予算（円）") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toLongOrNull()?.let(onConfirm) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

/**
 * Setup/troubleshooting panel. Notification detection has several independent things that must
 * all be true (permission granted, service actually bound, app allowed to run in background),
 * and when a purchase produces nothing there is no way to tell which one failed. This surfaces
 * each of them, plus a way to fix the two most common problems in place.
 */
@Composable
private fun DiagnosticsCard(
    notificationAccessEnabled: Boolean,
    listenerConnected: Boolean,
    onOpenSettings: () -> Unit,
    onRequestRebind: () -> Unit,
    onAddTestTransaction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("動作診断", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            StatusLine(
                label = "通知アクセスの許可",
                ok = notificationAccessEnabled,
                okText = "許可済み",
                ngText = "未許可",
            )
            StatusLine(
                label = "通知リスナーの接続",
                ok = listenerConnected,
                okText = "接続中",
                ngText = "未接続",
            )

            if (notificationAccessEnabled && !listenerConnected) {
                Text(
                    "許可はされていますがサービスが繋がっていません。アプリを入れ直した直後によく起こります。下の「再接続」を押すか、設定で通知アクセスを一度OFF→ONしてください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRequestRebind) { Text("再接続") }
                OutlinedButton(onClick = onOpenSettings) { Text("通知設定") }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onAddTestTransaction) {
                Text("テスト支出 ¥100 を追加")
            }
            Text(
                "「テスト支出」で残高通知が出れば、予算計算と通知の仕組みは正常です。出るのに実際の買い物で反応しない場合は、通知の検知だけが問題ということになります。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean, okText: String, ngText: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (ok) "\u2713 $okText" else "\u2717 $ngText",
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}
