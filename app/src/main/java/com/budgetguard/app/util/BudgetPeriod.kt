package com.budgetguard.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Turns a point in time into a "budget period key" that respects a configurable reset day
 * (e.g. some cards close their statement on the 15th, not the 1st). The key is just used as a
 * grouping/query key in Room (see [com.budgetguard.app.data.TransactionEntity.yearMonth]); it is
 * always named after the month the period *starts* in.
 *
 * Example with resetDay = 15:
 *  - 2026-08-20 -> period "2026-08" (Aug 15 - Sep 14)
 *  - 2026-08-05 -> period "2026-07" (Jul 15 - Aug 14)
 */
object BudgetPeriod {

    private val KEY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    fun keyFor(epochMillis: Long, resetDay: Int, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        return keyFor(date, resetDay)
    }

    fun keyFor(date: LocalDate, resetDay: Int): String {
        val effective = if (date.dayOfMonth < resetDay) date.minusMonths(1) else date
        return effective.format(KEY_FORMAT)
    }

    fun currentKey(resetDay: Int, zoneId: ZoneId = ZoneId.systemDefault()): String =
        keyFor(LocalDate.now(zoneId), resetDay)

    /** Human-friendly label for a period key, e.g. "2026-08" -> "2026年8月分". */
    fun label(periodKey: String): String {
        val parts = periodKey.split("-")
        if (parts.size != 2) return periodKey
        val year = parts[0]
        val month = parts[1].toIntOrNull() ?: return periodKey
        return "${year}年${month}月分"
    }
}
