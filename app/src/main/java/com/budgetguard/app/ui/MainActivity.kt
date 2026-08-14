package com.budgetguard.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budgetguard.app.notification.NotificationAccess
import com.budgetguard.app.ui.theme.BudgetGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BudgetGuardTheme {
                Surface {
                    BudgetGuardApp()
                }
            }
        }
    }
}

private enum class Tab(val label: String) {
    DASHBOARD("ホーム"),
    APPS("監視アプリ"),
    LOG("通知ログ"),
}

@Composable
private fun BudgetGuardApp(viewModel: BudgetViewModel = viewModel()) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(Tab.DASHBOARD) }

    // Notification-access is a system Settings toggle with no in-app callback, so we recheck it
    // every time the activity resumes (e.g. the user comes back from the settings screen).
    var notificationAccessEnabled by remember { mutableStateOf(NotificationAccess.isEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccessEnabled = NotificationAccess.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result not otherwise needed; BalanceNotifier re-checks before every notify() call */ }

    DisposableEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onDispose {}
    }

    val status by viewModel.status.collectAsState()
    val transactions by viewModel.recentTransactions.collectAsState()
    val logs by viewModel.notificationLogs.collectAsState()
    val monitoredPackages by viewModel.monitoredPackages.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == Tab.DASHBOARD,
                    onClick = { selectedTab = Tab.DASHBOARD },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(Tab.DASHBOARD.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == Tab.APPS,
                    onClick = { selectedTab = Tab.APPS },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text(Tab.APPS.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == Tab.LOG,
                    onClick = { selectedTab = Tab.LOG },
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    label = { Text(Tab.LOG.label) },
                )
            }
        },
    ) { padding ->
        when (selectedTab) {
            Tab.DASHBOARD -> DashboardScreen(
                status = status,
                transactions = transactions,
                notificationAccessEnabled = notificationAccessEnabled,
                onOpenNotificationAccessSettings = { NotificationAccess.openSettings(context) },
                onSetBudget = viewModel::setMonthlyBudget,
                onDeleteTransaction = viewModel::deleteTransaction,
                modifier = Modifier.padding(padding),
            )
            Tab.APPS -> MonitoredAppsScreen(
                monitoredPackages = monitoredPackages,
                onToggle = viewModel::toggleMonitoredPackage,
                modifier = Modifier.padding(padding),
            )
            Tab.LOG -> NotificationLogScreen(
                logs = logs,
                modifier = Modifier.padding(padding),
            )
        }
    }
}
