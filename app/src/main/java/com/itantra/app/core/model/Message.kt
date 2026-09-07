package com.itantra.app.core.model

data class Message(
    val id: String,
    val sender: String,
    val language: Language,
    val text: String,
    val timestamp: Long
)
