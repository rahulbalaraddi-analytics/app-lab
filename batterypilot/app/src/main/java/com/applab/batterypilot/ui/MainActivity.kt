package com.applab.batterypilot.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.applab.batterypilot.ui.theme.BatteryPilotTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TAB = "tab"
    }

    private val requestedTab = mutableIntStateOf(-1)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedTab.intValue = intent.getIntExtra(EXTRA_TAB, -1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTab.intValue = intent?.getIntExtra(EXTRA_TAB, -1) ?: -1
        setContent {
            BatteryPilotTheme { App(requestedTab.intValue) { requestedTab.intValue = -1 } }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Today", Icons.Filled.BatteryChargingFull),
    Tab("Fixes", Icons.Filled.Checklist),
    Tab("Apps", Icons.Filled.Apps),
    Tab("History", Icons.Filled.History),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(requestedTab: Int, consumeTab: () -> Unit) {
    val vm: MainViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(requestedTab) {
        if (requestedTab in tabs.indices) { tab = requestedTab; showSetup = false; consumeTab() }
    }
    // Fresh reading + analysis every time you come back to the app (also after a settings screen).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }
    // First launch: show setup.
    LaunchedEffect(state.snap?.access) {
        val a = state.snap?.access ?: return@LaunchedEffect
        if (!a.usage && !a.writeSettings) showSetup = true
    }
    BackHandler(enabled = showSetup) { showSetup = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showSetup) "Setup & settings" else "BatteryPilot") },
                navigationIcon = {
                    if (showSetup) IconButton(onClick = { showSetup = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "Refresh") }
                    if (!showSetup) IconButton(onClick = { showSetup = true }) { Icon(Icons.Filled.Settings, "Setup") }
                },
            )
        },
        bottomBar = {
            if (!showSetup) NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val snap = state.snap
        if (snap == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize()) {
            when {
                showSetup -> SetupScreen(snap, vm, padding)
                tab == 0 -> TodayScreen(snap, padding, openFixes = { tab = 1 }, openSetup = { showSetup = true })
                tab == 1 -> FixesScreen(snap, state, vm, padding)
                tab == 2 -> AppsScreen(snap, padding, openSetup = { showSetup = true })
                else -> HistoryScreen(snap, padding)
            }
            if (state.loading) {
                LinearProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = padding.calculateTopPadding()).fillMaxWidth())
            }
        }
    }
}
