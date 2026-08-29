package com.itantra.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun HomeScreen(onNavigateToSettings: () -> Unit) {
    Column {
        Text(text = "Home Screen Placeholder")
        Button(onClick = onNavigateToSettings) {
            Text("Go to Settings")
        }
    }
}
