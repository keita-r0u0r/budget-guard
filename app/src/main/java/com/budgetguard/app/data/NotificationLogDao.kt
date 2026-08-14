package com.budgetguard.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationLogDao {

    @Insert
    suspend fun insert(log: NotificationLogEntity): Long

    @Query("SELECT * FROM notification_log ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<NotificationLogEntity>>

    @Query("DELETE FROM notification_log WHERE timestampMillis < :beforeMillis")
    suspend fun pruneOlderThan(beforeMillis: Long)
}
