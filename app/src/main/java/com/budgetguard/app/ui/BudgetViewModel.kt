package com.budgetguard.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.budgetguard.app.data.BudgetRepository
import com.budgetguard.app.data.NotificationLogEntity
import com.budgetguard.app.data.TransactionEntity
import com.budgetguard.app.notification.BalanceNotifier
import com.budgetguard.app.notification.BalanceSurfaces
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BudgetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BudgetRepository.get(application)

    val status: StateFlow<BudgetRepository.BudgetStatus?> = repository.observeCurrentStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentTransactions: StateFlow<List<TransactionEntity>> = repository.observeRecentTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notificationLogs: StateFlow<List<NotificationLogEntity>> = repository.observeRecentNotificationLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monitoredPackages: StateFlow<Set<String>> = repository.preferences.monitoredPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val budgetResetDay: StateFlow<Int> = repository.preferences.budgetResetDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    val logAllNotifications: StateFlow<Boolean> = repository.preferences.logAllNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val persistentNotificationEnabled: StateFlow<Boolean> =
        repository.preferences.persistentNotificationEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setPersistentNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferences.setPersistentNotificationEnabled(enabled)
            BalanceSurfaces.refresh(getApplication())
        }
    }

    /** Repaints the shade notification and widget after anything that moves the numbers. */
    fun refreshBalanceSurfaces() {
        viewModelScope.launch { BalanceSurfaces.refresh(getApplication()) }
    }

    fun setMonthlyBudget(amountYen: Long) {
        viewModelScope.launch {
            repository.preferences.setMonthlyBudget(amountYen)
            BalanceSurfaces.refresh(getApplication())
        }
    }

    fun setBudgetResetDay(day: Int) {
        viewModelScope.launch { repository.preferences.setBudgetResetDay(day) }
    }

    fun setLogAllNotifications(enabled: Boolean) {
        viewModelScope.launch { repository.preferences.setLogAllNotifications(enabled) }
    }

    fun toggleMonitoredPackage(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) repository.preferences.addMonitoredPackage(packageName)
            else repository.preferences.removeMonitoredPackage(packageName)
        }
    }

    /**
     * Records a fake spend without going through the notification listener. This is the isolation
     * test: if the balance notification appears from this button but never from a real purchase,
     * the budget/DB/notify path is fine and the problem is purely in notification detection.
     */
    fun addTestTransaction(amountYen: Long = 100L) {
        viewModelScope.launch {
            val result = repository.ingestor.ingest(
                amountYen = amountYen,
                packageName = "com.budgetguard.app (テスト)",
                sourceType = "manual_test",
                rawText = "手動テスト支出",
            )
            BalanceNotifier.notifyBalance(getApplication(), result)
            BalanceSurfaces.refresh(getApplication())
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            BalanceSurfaces.refresh(getApplication())
        }
    }
}
