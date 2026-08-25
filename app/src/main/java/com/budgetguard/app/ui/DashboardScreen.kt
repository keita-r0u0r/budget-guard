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
    onOpenNotificationAccessSettings: () -> Unit,
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
            BalanceCard(
                status = status,
                onEditBudget = { showBudgetDialog = true },
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
            Text(
                "残り予算",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            val remaining = status?.remainingYen ?: 0L
            Text(
                "¥${yenFormat.format(remaining)}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { status?.spentRatio ?: 0f },
                modifier = Modifier.fillMaxWidth(),
                color = if (status?.isOverBudget == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "使用 ¥${yenFormat.format(status?.spentYen ?: 0L)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "予算 ¥${yenFormat.format(status?.budgetYen ?: 0L)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = onEditBudget, modifier = Modifier.padding(top = 12.dp)) {
                Text("予算を編集")
            }
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
