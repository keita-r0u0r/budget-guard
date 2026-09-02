package com.budgetguard.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "budget_prefs")

/**
 * Small, app-wide settings that don't need Room: the monthly budget, which day of the month the
 * budget resets on, and which installed apps' notifications should be monitored.
 *
 * Monitored apps are stored as package names (a Set<String>) so that adding a new "known" app to
 * monitor is just adding it to this set from the UI -- no schema change needed. This is also the
 * seam a future feature (e.g. "auto-suggest banking apps installed on this phone") would write
 * into.
 */
class BudgetPreferences(private val context: Context) {

    private object Keys {
        val MONTHLY_BUDGET_YEN = longPreferencesKey("monthly_budget_yen")
        val BUDGET_RESET_DAY = intPreferencesKey("budget_reset_day")
        val MONITORED_PACKAGES = stringSetPreferencesKey("monitored_packages")
        val LOG_ALL_NOTIFICATIONS = booleanPreferencesKey("log_all_notifications")
        val PERSISTENT_NOTIFICATION = booleanPreferencesKey("persistent_notification")
    }

    val monthlyBudgetYen: Flow<Long> =
        context.dataStore.data.map { it[Keys.MONTHLY_BUDGET_YEN] ?: 0L }

    /** Day-of-month (1-28) the budget period resets. Defaults to the 1st. */
    val budgetResetDay: Flow<Int> =
        context.dataStore.data.map { it[Keys.BUDGET_RESET_DAY] ?: 1 }

    val monitoredPackages: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.MONITORED_PACKAGES] ?: DEFAULT_MONITORED_PACKAGES }

    /**
     * Debug aid: when true, every notification from every app is written to the notification log
     * (but only monitored apps ever create a transaction). This is how you discover which package
     * actually posts the "you spent money" notification for a given payment method -- e.g. a
     * vending-machine tap might surface as Google Wallet, モバイルSuica, or the card app itself,
     * and you can't know which without seeing the raw feed. Defaults on for the prototype; turn
     * it off once the right packages are identified, since it logs unrelated notifications too.
     */
    val logAllNotifications: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.LOG_ALL_NOTIFICATIONS] ?: true }

    /**
     * Whether to keep a permanent, silent notification in the shade showing today's remaining
     * allowance. This is the main "把握しやすさ" surface -- the number is only useful if it's
     * where the user already looks -- but it is genuinely a matter of taste how much notification
     * shade real estate someone wants to give up, so it stays user-controlled.
     */
    val persistentNotificationEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PERSISTENT_NOTIFICATION] ?: true }

    suspend fun setPersistentNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PERSISTENT_NOTIFICATION] = enabled }
    }

    suspend fun setMonthlyBudget(amountYen: Long) {
        context.dataStore.edit { it[Keys.MONTHLY_BUDGET_YEN] = amountYen }
    }

    suspend fun setBudgetResetDay(day: Int) {
        context.dataStore.edit { it[Keys.BUDGET_RESET_DAY] = day.coerceIn(1, 28) }
    }

    suspend fun setMonitoredPackages(packages: Set<String>) {
        context.dataStore.edit { it[Keys.MONITORED_PACKAGES] = packages }
    }

    suspend fun setLogAllNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOG_ALL_NOTIFICATIONS] = enabled }
    }

    suspend fun addMonitoredPackage(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.MONITORED_PACKAGES] ?: DEFAULT_MONITORED_PACKAGES
            prefs[Keys.MONITORED_PACKAGES] = current + packageName
        }
    }

    suspend fun removeMonitoredPackage(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.MONITORED_PACKAGES] ?: DEFAULT_MONITORED_PACKAGES
            prefs[Keys.MONITORED_PACKAGES] = current - packageName
        }
    }

    companion object {
        /**
         * A starter list of common Japanese card/bank/payment app package names, so the app is
         * useful out of the box. This is a best-effort list from public app-store listings --
         * verify against what's actually installed via the "監視アプリ選択" screen, which reads
         * real installed packages through PackageManager.
         */
        val DEFAULT_MONITORED_PACKAGES = setOf(
            "jp.co.rakuten.card",       // 楽天カード
            "jp.co.smbc.card.smcc",     // 三井住友カード Vpass
            "jp.ne.paypay.android.app", // PayPay
            "jp.co.aeon.aeonpay",       // イオンペイ
            "jp.co.jre.jremembers",     // JRE POINT / Suica系
            "com.nttdocomo.keitai.payment", // d払い
        )
    }
}
