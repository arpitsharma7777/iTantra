package com.itantra.app.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Connection : Screen("connection")
    object Communication : Screen("communication")
    object Settings : Screen("settings")
}
