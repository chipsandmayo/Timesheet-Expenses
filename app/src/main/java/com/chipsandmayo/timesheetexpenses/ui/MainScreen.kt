package com.chipsandmayo.timesheetexpenses.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.chipsandmayo.timesheetexpenses.AppViewModel
import com.chipsandmayo.timesheetexpenses.util.Updater

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Timesheet", Icons.Filled.CalendarMonth),
    Tab("Expenses", Icons.AutoMirrored.Filled.ReceiptLong),
    Tab("Profiles", Icons.Filled.Person),
    Tab("Settings", Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(if (vm.profiles.isEmpty()) 2 else 0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    val updateAvailable = vm.release?.let { Updater.isNewer(it) } == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tabs[tab].title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy, titleContentColor = Color.White, actionIconContentColor = Color.White,
                ),
                actions = { ProfileSwitcher(vm) },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            if (i == 3 && updateAvailable) BadgedBox(badge = { Badge() }) { Icon(t.icon, null) }
                            else Icon(t.icon, null)
                        },
                        label = { Text(t.title) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> TimesheetScreen(vm)
                1 -> ExpensesScreen(vm)
                2 -> ProfilesScreen(vm)
                else -> SettingsScreen(vm)
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ProfileSwitcher(vm: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    val p = vm.activeProfile ?: return
    Box {
        TextButton(onClick = { open = true }) {
            Text(p.displayName, color = Color.White)
            Icon(Icons.Filled.ArrowDropDown, null, tint = Color.White)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            vm.profiles.forEach { pr ->
                DropdownMenuItem(text = { Text(pr.displayName) }, onClick = { vm.selectProfile(pr.id); open = false })
            }
        }
    }
}
