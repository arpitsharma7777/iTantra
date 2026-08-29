package com.itantra.app.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Settings : Screen("settings")
    object Connection : Screen("connection")
    object Developer : Screen("developer")
}
