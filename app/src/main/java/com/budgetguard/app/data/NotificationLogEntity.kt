package com.budgetguard.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Every notification BudgetGuard has seen from a monitored app, whether or not an amount could
 * be parsed out of it. This exists so a human can look at real notification text from their own
 * bank/card apps (Settings > 通知ログ in this prototype) and tune [com.budgetguard.app.notification
 * .AmountParser] implementations for apps that don't match the default pattern. Amount parsing
 * from free-form notification text is inherently best-effort — this log is the debugging tool
 * for improving it over time.
 */
@Entity(tableName = "notification_log")
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val title: String?,
    val text: String?,
    val timestampMillis: Long,
    val parsedAmountYen: Long?,
    val wasRecordedAsTransaction: Boolean,
)
