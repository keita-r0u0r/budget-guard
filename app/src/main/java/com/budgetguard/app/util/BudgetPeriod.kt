package com.budgetguard.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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

    /** First day (inclusive) of the period identified by [periodKey]. */
    fun startDate(periodKey: String, resetDay: Int): LocalDate {
        val parts = periodKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: LocalDate.now().year
        val month = parts.getOrNull(1)?.toIntOrNull() ?: LocalDate.now().monthValue
        return LocalDate.of(year, month, resetDay.coerceIn(1, 28))
    }

    /** Last day (inclusive) of the period identified by [periodKey]. */
    fun endDate(periodKey: String, resetDay: Int): LocalDate =
        startDate(periodKey, resetDay).plusMonths(1).minusDays(1)

    /**
     * Days left in the current period, counting today. Never returns less than 1, so callers can
     * divide by it without guarding: on the last day of the period you still get "today's"
     * allowance rather than a division by zero.
     */
    fun remainingDays(resetDay: Int, zoneId: ZoneId = ZoneId.systemDefault()): Int {
        val today = LocalDate.now(zoneId)
        val end = endDate(currentKey(resetDay, zoneId), resetDay)
        return (ChronoUnit.DAYS.between(today, end).toInt() + 1).coerceAtLeast(1)
    }

    /**
     * Total number of days in the period identified by [periodKey]. Paired with [remainingDays]
     * this gives the "how far through the period are we" ratio the widget's progress bar shows.
     */
    fun totalDays(periodKey: String, resetDay: Int): Int {
        val start = startDate(periodKey, resetDay)
        val end = endDate(periodKey, resetDay)
        return (ChronoUnit.DAYS.between(start, end).toInt() + 1).coerceAtLeast(1)
    }

    /** [totalDays] for whichever period today falls in. */
    fun totalDays(resetDay: Int, zoneId: ZoneId = ZoneId.systemDefault()): Int =
        totalDays(currentKey(resetDay, zoneId), resetDay)

    /**
     * The next instant at which anything on screen goes stale without the user spending a yen:
     * [remainingDays] drops by one, the per-day pace is recomputed against it, and on the reset
     * day the period key itself flips. All three happen at the same moment, so one alarm covers
     * them.
     *
     * That moment is local midnight. [resetDay] is taken as a parameter even though it does not
     * currently move the boundary: the reset day decides *which* midnight starts a new period,
     * and if a reset *time* is ever added (e.g. "my period starts at 04:00 on the 15th") this is
     * the single place that has to learn about it. Callers should not compute midnight themselves.
     */
    fun nextRolloverMillis(resetDay: Int, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.now(zoneId).plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

    /** Human-friendly label for a period key, e.g. "2026-08" -> "2026年8月分". */
    fun label(periodKey: String): String {
        val parts = periodKey.split("-")
        if (parts.size != 2) return periodKey
        val year = parts[0]
        val month = parts[1].toIntOrNull() ?: return periodKey
        return "${year}年${month}月分"
    }
}
