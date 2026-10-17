package com.mosquetracker.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mosquetracker.app.ui.common.AppIcons
import com.mosquetracker.app.ui.common.EmptyState
import com.mosquetracker.app.ui.common.KeyValueRow
import com.mosquetracker.app.ui.common.ScreenScaffold
import com.mosquetracker.app.ui.common.SectionCard
import com.mosquetracker.app.ui.history.HistoryScreen
import com.mosquetracker.app.ui.home.HomeScreen
import com.mosquetracker.app.ui.qibla.QiblaScreen
import com.mosquetracker.app.ui.settings.SettingsScreen

private enum class Destination(val route: String, val label: String) {
    HOME("home", "Home"),
    HISTORY("history", "History"),
    QIBLA("qibla", "Qibla"),
    SETTINGS("settings", "Settings"),
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun navigate(route: String) {
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = { navigate(destination.route) },
                        icon = { DestinationIcon(destination) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.HOME.route) { HomeScreen() }
            composable(Destination.HISTORY.route) { HistoryScreen() }
            composable(Destination.QIBLA.route) { QiblaScreen() }
            composable(Destination.SETTINGS.route) {
                SettingsScreen(onOpenHome = { navigate(Destination.HOME.route) })
            }
        }
    }
}

@Composable
private fun DestinationIcon(destination: Destination) {
    when (destination) {
        Destination.HOME -> Icon(AppIcons.Home, contentDescription = destination.label)
        Destination.HISTORY -> Icon(AppIcons.DateRange, contentDescription = destination.label)
        Destination.QIBLA -> Icon(AppIcons.Compass, contentDescription = destination.label)
        Destination.SETTINGS -> Icon(AppIcons.Settings, contentDescription = destination.label)
    }
}
