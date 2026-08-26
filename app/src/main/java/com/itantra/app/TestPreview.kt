package com.itantra.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SimpleGreeting() {
    Text(text = "iTantra")
}

@Preview
@Composable
fun SimplePreview() {
    SimpleGreeting()
}
