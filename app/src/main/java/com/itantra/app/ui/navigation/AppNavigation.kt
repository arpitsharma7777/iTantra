package com.itantra.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.itantra.app.ui.home.HomeScreen
import com.itantra.app.ui.settings.SettingsScreen
import com.itantra.app.ui.connection.ConnectionScreen
import com.itantra.app.ui.developer.DeveloperScreen

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Screen.Developer.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(onNavigateToSettings = { navController.navigate(Screen.Settings.route) })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateToDeveloper = { navController.navigate(Screen.Developer.route) })
        }
        composable(Screen.Connection.route) {
            ConnectionScreen()
        }
        composable(Screen.Developer.route) {
            DeveloperScreen()
        }
    }
}
