package com.itantra.app.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Connection : Screen("connection")
    object Communication : Screen("communication")
    object Settings : Screen("settings")
    object Developer : Screen("developer")
    object Benchmark : Screen("benchmark")
    object LanguageVault : Screen("language_vault")
    object TtsEvaluation : Screen("tts_evaluation/{languageCode}") {
        fun createRoute(languageCode: String) = "tts_evaluation/$languageCode"
    }
}
