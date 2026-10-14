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
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mosquetracker.app.R
import com.mosquetracker.app.ui.common.AppIcons
import com.mosquetracker.app.ui.dashboard.DashboardScreen
import com.mosquetracker.app.ui.history.HistoryScreen
import com.mosquetracker.app.ui.mosques.MosquesScreen
import com.mosquetracker.app.ui.sensors.SensorsScreen

private enum class Destination(val route: String, val label: String) {
    DASHBOARD("dashboard", "Dashboard"),
    SENSORS("sensors", "Sensors"),
    MOSQUES("mosques", "Mosques"),
    HISTORY("history", "History"),
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { DestinationIcon(destination) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.DASHBOARD.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.DASHBOARD.route) { DashboardScreen() }
            composable(Destination.SENSORS.route) { SensorsScreen() }
            composable(Destination.MOSQUES.route) { MosquesScreen() }
            composable(Destination.HISTORY.route) { HistoryScreen() }
        }
    }
}

@Composable
private fun DestinationIcon(destination: Destination) {
    when (destination) {
        Destination.DASHBOARD -> Icon(AppIcons.Home, contentDescription = destination.label)
        Destination.SENSORS -> Icon(painterResource(R.drawable.ic_wifi), contentDescription = destination.label)
        Destination.MOSQUES -> Icon(painterResource(R.drawable.ic_mosque), contentDescription = destination.label)
        Destination.HISTORY -> Icon(AppIcons.DateRange, contentDescription = destination.label)
    }
}
