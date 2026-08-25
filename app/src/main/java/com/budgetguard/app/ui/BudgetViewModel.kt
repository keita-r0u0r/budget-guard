package com.budgetguard.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.budgetguard.app.data.BudgetRepository
import com.budgetguard.app.data.NotificationLogEntity
import com.budgetguard.app.data.TransactionEntity
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

    fun setMonthlyBudget(amountYen: Long) {
        viewModelScope.launch { repository.preferences.setMonthlyBudget(amountYen) }
    }

    fun setBudgetResetDay(day: Int) {
        viewModelScope.launch { repository.preferences.setBudgetResetDay(day) }
    }

    fun toggleMonitoredPackage(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) repository.preferences.addMonitoredPackage(packageName)
            else repository.preferences.removeMonitoredPackage(packageName)
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { repository.deleteTransaction(id) }
    }
}
