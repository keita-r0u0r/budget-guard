package com.budgetguard.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE yearMonth = :yearMonth ORDER BY timestampMillis DESC")
    fun observeForMonth(yearMonth: String): Flow<List<TransactionEntity>>

    @Query("SELECT COALESCE(SUM(amountYen), 0) FROM transactions WHERE yearMonth = :yearMonth")
    fun observeMonthTotal(yearMonth: String): Flow<Long>

    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<TransactionEntity>>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
