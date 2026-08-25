package com.budgetguard.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A confirmed spending event that has been counted against the budget.
 *
 * [sourceType] identifies which kind of [com.budgetguard.app.notification.SpendingEventSource]
 * produced this record. Today only "notification" exists (see
 * SpendingNotificationListenerService), but the schema already carries this field so a future
 * email-parsing or bank-API source can write into the same table without a migration.
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountYen: Long,
    val packageName: String,
    val sourceType: String = "notification",
    val rawText: String?,
    val timestampMillis: Long,
    /** yyyy-MM in the device's default locale/timezone, precomputed for fast month sums. */
    val yearMonth: String,
)
