package com.itantra.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SettingsScreen(onNavigateToDeveloper: () -> Unit) {
    Column {
        Text(text = "Settings Screen Placeholder")
        Button(onClick = onNavigateToDeveloper) {
            Text("Developer Dashboard")
        }
    }
}
